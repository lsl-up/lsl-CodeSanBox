package com.lsl.lslcodesandbox;

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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


//@Component
public class JavaDockerCodeSandbox_Old {

    // 指定使用的镜像 Amazon Corretto 11
    private static final String DOCKER_IMAGE = "amazoncorretto:11";
    private static final long TIME_OUT = 5000L;

    @SneakyThrows
    public static ExecuteCodeResponse execute(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();

        // 1. 使用 JavaParser 解析代码，动态获取类名
        // 这一步是为了防止用户乱写类名导致编译运行失败
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(code);
        CompilationUnit cu = parseResult.getResult().orElseThrow(() -> new RuntimeException("解析代码失败"));

        // 查找第一个 Public 的类作为主类
        String className = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(ClassOrInterfaceDeclaration::isPublic)
                .filter(c -> !c.isInterface()) // 排除接口
                .findFirst()
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElseThrow(() -> new RuntimeException("未找到Public类"));

        // 调用核心执行逻辑
        return executeCode(executeCodeRequest, className);
    }


    private static ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest, String className) throws IOException, InterruptedException {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        // 用于收集每个测试用例的执行结果
        List<String> outputList = new ArrayList<>();

        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setStatus(1);

        // 1. 代码文件保存

        String userDir = System.getProperty("user.dir");
        // 存放用户代码的根目录：src/main/resources/userCode
        String userDirPath = userDir + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "userCode";
        if (!FileUtil.exist(userDirPath)) {
            FileUtil.mkdir(userDirPath);
        }
        // 为每次请求创建一个独立的UUID目录，防止文件名冲突
        String userPathFile = userDirPath + File.separator + UUID.randomUUID();
        String userPathFileName = userPathFile + File.separator + className + ".java";
        File file = FileUtil.writeUtf8String(code, userPathFileName);


        // 2. 编译代码 (Javac)

        // 在宿主机进行编译（简单快捷），也可以选择在 Docker 内编译
        // -encoding utf-8 防止中文乱码
        String javac = String.format("javac -encoding utf-8 %s", file.getAbsolutePath());
        Process compile = Runtime.getRuntime().exec(javac);
        int waitFor = compile.waitFor();

        // 如果编译失败，直接返回错误信息，不再运行 Docker
        if (waitFor != 0) {
            StringBuilder stringBuilder = new StringBuilder();
            // 注意：Windows 默认可能是 GBK，如果乱码请调整编码
            BufferedReader compileErrorMessage = new BufferedReader(new InputStreamReader(compile.getErrorStream(), "GBK"));
            String line;
            while ((line = compileErrorMessage.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            executeCodeResponse.setMessage(stringBuilder.toString());
            executeCodeResponse.setStatus(3); // 状态 3 表示编译/运行失败
            // 清理临时文件
            FileUtil.del(userPathFile);
            return executeCodeResponse;
        }


        // 3. Docker 环境准备


        // 加载默认配置 (环境变量等)
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        // 使用 ApacheDockerHttpClient
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        String containerId = null;
        long totalRunTime = 0L;
        long maxMemoryUsed = 0L;

        try {
            // 配置容器资源限制 (HostConfig)
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(10 * 1024 * 1024L); // 限制内存 10MB，防止 OOM
            hostConfig.withMemorySwap(0L);             // 禁止使用 Swap 交换分区
            hostConfig.withCpuCount(1L);               // 限制使用 1 核 CPU
            hostConfig.withPidsLimit(100L);            // 限制进程数，防止 Fork 炸弹攻击

            // 挂载卷：将宿主机的代码目录挂载到容器内的 /app 目录
            // 这样容器就能访问到我们在宿主机编译好的 .class 文件
            hostConfig.setBinds(new Bind(file.getParent(), new Volume("/app")));

            // 创建容器命令
            CreateContainerResponse containerResponse = dockerClient.createContainerCmd(DOCKER_IMAGE)
                    .withHostConfig(hostConfig)
                    .withNetworkDisabled(true)  // 禁用网络，防止反弹 Shell 或恶意下载
                    .withReadonlyRootfs(true)            // 只读文件系统，防止恶意修改系统文件
                    .withAttachStdin(true)      // 开启标准输入 (为了支持 Scanner)
                    .withAttachStdout(true)     // 开启标准输出
                    .withAttachStderr(true)     // 开启标准错误
                    .withTty(true)              // 开启伪终端交互
                    .withWorkingDir("/app")           // 设置工作目录，方便直接运行 class
                    .exec();

            containerId = containerResponse.getId();
            // 启动容器
            dockerClient.startContainerCmd(containerId).exec();


            // 4. 循环执行测试用例
            for (String inputArgs : inputList) {
                // 构造运行命令：java -cp /app Main
                // 注意：这里不再通过 args 传参，而是通过 Stdin 输入流传入
                String[] cmdArray = new String[] {"java", "-cp", "/app", className};

                // 创建执行命令 (Exec)
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray)
                        .withAttachStderr(true)
                        .withAttachStdin(true)  // 必须开启，否则无法输入
                        .withAttachStdout(true)
                        .exec();

                String execId = execCreateCmdResponse.getId();

                // 拼接分帧传输的输出结果
                StringBuilder errorMessage = new StringBuilder();
                StringBuilder message = new StringBuilder();

                // 启动内存监控 (异步)
                final long[] currentMemoryUsage = {0L};
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                // 使用回调实时获取内存状态，取最大值
                ResultCallback<Statistics> statsCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics statistics) {
                        if (statistics.getMemoryStats() != null && statistics.getMemoryStats().getUsage() != null) {
                            currentMemoryUsage[0] = Math.max(currentMemoryUsage[0], statistics.getMemoryStats().getUsage());
                        }
                    }
                    @Override public void close() throws IOException {}
                    @Override public void onStart(java.io.Closeable closeable) {}
                    @Override public void onError(Throwable throwable) {}
                    @Override public void onComplete() {}
                });

                // 启动输出流监听 (异步)
                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        // 区分标准错误和标准输出
                        if (StreamType.STDERR.equals(streamType)) {
                            errorMessage.append(new String(frame.getPayload()));
                        } else {
                            message.append(new String(frame.getPayload()));
                        }
                        super.onNext(frame);
                    }
                };

                long testStartTime = System.currentTimeMillis();


                String inputContent = inputArgs + "\n";
                InputStream inputStream = new ByteArrayInputStream(inputContent.getBytes(StandardCharsets.UTF_8));

                try {
                    // 执行命令并注入输入流
                    dockerClient.execStartCmd(execId)
                            .withStdIn(inputStream)
                            .exec(execStartResultCallback)
                            .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                } catch (RuntimeException e) {
                    // Windows Docker Desktop 使用 npipe 连接时，
                    // 当程序运行结束关闭流，客户端可能会误报 "管道已结束" 异常。
                    // 只要程序实际跑完了，这个异常可以安全忽略。
                    if (e.getMessage() != null && (e.getMessage().contains("管道已结束") || e.getMessage().contains("The pipe has been ended"))) {
                        // ignore
                    } else {
                        throw e; // 其他真正的异常仍需抛出
                    }
                }

                long testEndTime = System.currentTimeMillis();
                totalRunTime += (testEndTime - testStartTime);
                maxMemoryUsed = Math.max(maxMemoryUsed, currentMemoryUsage[0]);

                // 关闭流和监控
                inputStream.close();
                statsCmd.close();

                String stderr = errorMessage.toString();
                String stdout = message.toString();


                if (!stderr.isEmpty()) {
                    executeCodeResponse.setMessage(stderr);
                    executeCodeResponse.setStatus(3);
                    break; // 遇到错误直接中断后续测试
                } else {
                    String output = stdout.trim();
                    outputList.add(output);
                }
            }
        } catch (Exception e) {
            executeCodeResponse.setMessage("执行错误: " + e.getMessage());
            executeCodeResponse.setStatus(3);
            e.printStackTrace();
        } finally {
            if (containerId != null) {
                try {
                    // 无论成功失败，都要停止并删除容器，防止残留僵尸容器
                    dockerClient.stopContainerCmd(containerId).exec();
                    dockerClient.removeContainerCmd(containerId).exec();
                } catch (Exception e) {
                    // 忽略清理过程中的报错 (比如容器已经停止了)
                }
            }
            // 关闭 Docker 客户端连接
            try {
                dockerClient.close();
            } catch (IOException e) {}

            // 删除生成的代码文件目录
            if (file.exists()) {
                FileUtil.del(userPathFile);
            }
        }


        // 6. 结果封装返回

        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(totalRunTime);
        judgeInfo.setMemory(maxMemoryUsed); // 单位 byte
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 如果所有用例都跑通且没有报错，标记为成功
        if (outputList.size() == inputList.size() && executeCodeResponse.getStatus() == 1) {
            executeCodeResponse.setStatus(2); // 2 表示执行成功
            executeCodeResponse.setMessage("执行成功");
        }

        return executeCodeResponse;
    }
}