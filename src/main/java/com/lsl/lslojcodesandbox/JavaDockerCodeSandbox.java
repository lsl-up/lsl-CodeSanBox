package com.lsl.lslojcodesandbox;

import cn.hutool.core.io.FileUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.lsl.lslojcodesandbox.model.ExecuteCodeRequest;
import com.lsl.lslojcodesandbox.model.ExecuteCodeResponse;
import com.lsl.lslojcodesandbox.model.JudgeInfo;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandbox {

    private static String className;
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
        cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(ClassOrInterfaceDeclaration::isPublic)
                .forEach(classOrInterfaceDeclaration -> {
                    className = classOrInterfaceDeclaration.getNameAsString();
                });


        ExecuteCodeResponse executeCodeResponse = executeCode(executeCodeRequest);
        return executeCodeResponse;
    }

    private static ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) throws IOException, InterruptedException {

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
            DockerClient dockerClient = DockerClientBuilder.getInstance().build();
            
            // 拉取镜像（如果需要）
            try {
                System.out.println("正在拉取Docker镜像: " + DOCKER_IMAGE);
                dockerClient.pullImageCmd(DOCKER_IMAGE).start().awaitCompletion();
                System.out.println("Docker镜像拉取成功");
            } catch (Exception e) {
                System.out.println("Docker镜像拉取失败，尝试使用本地镜像: " + e.getMessage());
            }

            // 创建容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(DOCKER_IMAGE);
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(100 * 1000 * 1000L); // 100MB内存限制，适合Java程序运行
            hostConfig.withMemorySwap(0L);
            hostConfig.withCpuCount(1L);
            
            // 增强安全配置
            hostConfig.withSecurityOpts(Arrays.asList("no-new-privileges")); // 禁止获取新权限
            hostConfig.withPidsLimit(50L); // 限制进程数量

            hostConfig.setBinds(new Bind(classPath, new Volume("/app")));
            
            CreateContainerResponse createContainerResponse = containerCmd
                    .withHostConfig(hostConfig)
                    .withNetworkDisabled(true)
                    .withReadonlyRootfs(true)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withTty(true)
                    .withWorkingDir("/app") // 设置工作目录
                    .withPrivileged(false) // 非特权模式
                    .exec();
            
            String containerId = createContainerResponse.getId();
            dockerClient.startContainerCmd(containerId).exec();

            // 执行测试用例
            for (String InputArgs : inputList) {
                String[] args = InputArgs.split(" ");
                String[] cmdArray = new String[args.length + 3];
                cmdArray[0] = "java";
                cmdArray[1] = "-cp";
                cmdArray[2] = "/app"; // Linux容器中的路径
                cmdArray[3] = className;
                System.arraycopy(args, 0, cmdArray, 4, args.length);

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



