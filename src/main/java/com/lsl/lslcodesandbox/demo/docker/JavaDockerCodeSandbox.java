package com.lsl.lslcodesandbox.demo.docker;

import cn.hutool.core.io.FileUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.lsl.lslcodesandbox.model.ExecuteCodeRequest;
import com.lsl.lslcodesandbox.model.ExecuteCodeResponse;
import com.lsl.lslcodesandbox.model.JudgeInfo;
import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

//@Component
public class JavaDockerCodeSandbox {

//    private static String className;
    private static final ArrayList<String> allOutputs = new ArrayList<>(); //用于存放输出用例
    private static final String DOCKER_IMAGE = "docker.xuanyuan.me/openjdk:11";
    private static final long TIME_OUT = 3000L; // 3秒超时

    @SneakyThrows
    public static ExecuteCodeResponse execute(ExecuteCodeRequest executeCodeRequest) {

        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        List<String> inputList = executeCodeRequest.getInputList();

        //获取类名

        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(code);
        CompilationUnit cu = parseResult.getResult().get();

        String className = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(ClassOrInterfaceDeclaration::isPublic)
                .filter(c -> !c.isInterface())
                .map(classOrInterfaceDeclaration -> classOrInterfaceDeclaration.getNameAsString())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到Public类"));;


        ExecuteCodeResponse executeCodeResponse = executeCode(executeCodeRequest, className);
        return executeCodeResponse;
    }

    private static ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest, String className) throws IOException, InterruptedException {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setStatus(1); // 1表示正在执行

        //1. 把用户的代码保存为文件
        //创建根路径
        String userDir = System.getProperty("user.dir");
        String userDirPath = userDir + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "userCode";
        if (FileUtil.exist(userDirPath)) {
            FileUtil.mkdir(userDirPath);
        }
        //创建子目录+用户文件
        String userPathFile = userDirPath + File.separator + UUID.randomUUID();
        String userPathFileName = userPathFile + File.separator + className + ".java";
        File file = FileUtil.writeUtf8String(code, userPathFileName);

        //2.编译代码
        // 在Windows环境中编译Java代码
        String javac = String.format("javac %s", file.getAbsolutePath());
        Process compile = Runtime.getRuntime().exec(javac);
        int waitFor = compile.waitFor();
        System.out.println("编译状态码：" + waitFor);

        String classPath = file.getParent();
        long totalRunTime = 0L;
        long maxMemoryUsed = 0L; // 记录最大内存使用量

        if (waitFor == 0) {
            // 3. 使用Docker容器执行代码
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                    .withDockerHttpClient(httpClient)
                    .build();



            // 2. 准备挂载路径 (Volume)
            // 我们把当前项目的 userCode 目录挂载到容器内的 /app 目录
            String hostPath = userDir + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "userCode";
            // 如果目录不存在，先创建它，防止报错
            new File(hostPath).mkdirs();

            System.out.println("宿主机挂载路径: " + hostPath);

            // 3. 配置容器参数 (HostConfig) —— 这是最关键的一步！
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(100 * 1024 * 1024L);
            hostConfig.withMemorySwap(0L);
            hostConfig.withCpuCount(1L);
            hostConfig.withPidsLimit(100L);

            // [文件挂载] hostPath(宿主机) -> /app(容器内)
            hostConfig.setBinds(new Bind(hostPath, new Volume("/app")));

            // 4. 创建容器命令
            System.out.println("正在创建容器...");
            CreateContainerResponse containerResponse = dockerClient.createContainerCmd(DOCKER_IMAGE)
                    .withHostConfig(hostConfig) // 注入硬件配置
                    .withNetworkDisabled(true)  // 禁用网络，防止黑客反向连接或挖矿
                    .withReadonlyRootfs(true)   // 根文件系统只读，防止删库跑路
                    .withAttachStdin(true)      // 开启输入流 (为了支持 Scanner)
                    .withAttachStdout(true)     // 开启输出流
                    .withAttachStderr(true)     // 开启错误流
                    .withWorkingDir("/app")           // 设置工作目录，进去默认就在 /app 下
                    .withTty(true)              // 开启终端交互
                    .exec();
            
            String containerId = containerResponse.getId();
            dockerClient.startContainerCmd(containerId).exec();

            // 执行测试用例
            for (String InputArgs : inputList) {
                String[] args = InputArgs.split(" ");
                // 1. 创建一个动态数组（List），它会自动扩容
                List<String> cmdList = new ArrayList<>();
                cmdList.add("java");
                cmdList.add("-cp");
                cmdList.add("/app");
                cmdList.add(className);
                // 3. 把 args 里的所有元素加进去
                Collections.addAll(cmdList, args);
                // 4. 转回数组（如果你非要用数组传给 Docker 接口）
                String[] cmdArray = cmdList.toArray(new String[0]);

                // 启动内存监控
                final long[] currentMemoryUsage = {0L};
                final long[] maxMemoryForTest = {0L};

                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics statistics) {
                        if (statistics.getMemoryStats() != null && statistics.getMemoryStats().getUsage() != null) {
                            currentMemoryUsage[0] = statistics.getMemoryStats().getUsage();
                            maxMemoryForTest[0] = Math.max(maxMemoryForTest[0], currentMemoryUsage[0]);
                            System.out.println("当前内存使用: " + (currentMemoryUsage[0] / 1024 / 1024) + "MB");
                        }
                    }

                    @Override
                    public void close() throws IOException {}

                    @Override
                    public void onStart(java.io.Closeable closeable) {}

                    @Override
                    public void onError(Throwable throwable) {}

                    @Override
                    public void onComplete() {}
                });

                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray)
                        .withAttachStderr(true)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .exec();

                String execId = execCreateCmdResponse.getId();
                final String[] message = {null};
                final String[] errorMessage = {null};

                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            errorMessage[0] = new String(frame.getPayload());
                        } else {
                            message[0] = new String(frame.getPayload());
                        }
                        super.onNext(frame);
                    }
                };

                long testStartTime = System.currentTimeMillis();
                
                try {
                    dockerClient.execStartCmd(execId)
                            .exec(execStartResultCallback)
                            .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                    
                    long testEndTime = System.currentTimeMillis();
                    long testRunTime = testEndTime - testStartTime;
                    totalRunTime += testRunTime;
                    
                    // 更新最大内存使用量
                    maxMemoryUsed = Math.max(maxMemoryUsed, maxMemoryForTest[0]);
                    System.out.println("本次测试最大内存使用: " + (maxMemoryForTest[0] / 1024 / 1024) + "MB");

                    if (errorMessage[0] != null && !errorMessage[0].trim().isEmpty()) {
                        executeCodeResponse.setMessage(errorMessage[0]);
                        System.out.println("运行错误：" + errorMessage[0]);
                    } else if (message[0] != null) {
                        String output = message[0].trim();
                        if (!output.isEmpty()) {
                            allOutputs.add(output);
                            System.out.println("输出结果：" + output);
                        }
                    }
                    
                    // 关闭统计监控
                    try {
                        statsCmd.close();
                    } catch (Exception e) {
                        System.out.println("关闭内存监控失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.out.println("执行超时或异常：" + e.getMessage());
                    executeCodeResponse.setMessage("执行超时或异常：" + e.getMessage());
                    executeCodeResponse.setStatus(3); // 3表示执行错误
                }
            }

            // 停止并删除容器
            try {
                System.out.println("正在清理Docker容器...");
                dockerClient.stopContainerCmd(containerId).exec();
                dockerClient.removeContainerCmd(containerId).exec();
                System.out.println("Docker容器清理成功");
            } catch (Exception e) {
                System.out.println("容器清理失败：" + e.getMessage());
            } finally {
                try {
                    dockerClient.close();
                    System.out.println("Docker客户端已关闭");
                } catch (Exception e) {
                    System.out.println("关闭Docker客户端失败：" + e.getMessage());
                }
            }
        } else {
            //读取编译错误输出
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader compileErrorMessage = new BufferedReader(new InputStreamReader(compile.getErrorStream(), "GBK"));
            String line;
            while ((line = compileErrorMessage.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            executeCodeResponse.setMessage(stringBuilder.toString());
            executeCodeResponse.setStatus(3); // 3表示编译错误
            System.out.println("编译失败：" + executeCodeResponse.getMessage());
        }

        executeCodeResponse.setOutputList(new ArrayList<>(allOutputs));
        List<String> outputList = executeCodeResponse.getOutputList();
        System.out.println("测试用例输出：" + outputList);


        // 设置判题信息
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage("Docker执行成功");
        judgeInfo.setTime(totalRunTime < 3000 ? totalRunTime : 3000L);
        // 设置内存使用信息（转换为MB）
        judgeInfo.setMemory(maxMemoryUsed / 1024 / 1024);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 输出统计信息
        System.out.println("运行耗时：" + totalRunTime + "ms");
        System.out.println("最大内存使用：" + (maxMemoryUsed / 1024 / 1024) + "MB");
        System.out.println("测试用例数量：" + inputList.size());
        System.out.println("输出结果数量：" + outputList.size());
        System.out.println("执行状态：" + executeCodeResponse.getStatus());

        //释放资源
        if (file.exists()) {
            FileUtil.del(userPathFile);
        }
        allOutputs.clear();
        return executeCodeResponse;
    }
}



