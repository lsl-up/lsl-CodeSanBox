package com.lsl.lslcodesandbox.demo.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

public class DockerCreateTest {

    // 使用你刚才拉取成功的镜像名 (推荐使用 eclipse-temurin:11-jdk-alpine)
    private static final String DOCKER_IMAGE = "amazoncorretto:11";

    public static void main(String[] args) {
        // 1. 初始化 Docker Client
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
        String containerId = null;

        try {
            // 2. 准备挂载路径 (Volume)
            // 我们把当前项目的 userCode 目录挂载到容器内的 /app 目录
            String userDir = System.getProperty("user.dir");
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
                    .withNetworkDisabled(true)  // [安全] 禁用网络，防止黑客反向连接或挖矿
                    .withReadonlyRootfs(true)   // [安全] 根文件系统只读，防止删库跑路
                    .withAttachStdin(true)      // 开启输入流 (为了支持 Scanner)
                    .withAttachStdout(true)     // 开启输出流
                    .withAttachStderr(true)     // 开启错误流
                    .withWorkingDir("/app")           // [新增] 设置工作目录，进去默认就在 /app 下
                    .withTty(true)              // 开启终端交互
                    .exec();

            // 5. 获取容器 ID
            containerId = containerResponse.getId();
            System.out.println("容器创建成功！ID: " + containerId);

            // 6. 启动容器 (创建后默认是停止状态，需要 Start)
            dockerClient.startContainerCmd(containerId).exec();
            Thread.sleep(5000);
            System.out.println("容器已启动");
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        } catch (ConflictException e) {
            throw new RuntimeException(e);
        } catch (NotModifiedException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("正在停止容器...");
            dockerClient.stopContainerCmd(containerId).exec();
            System.out.println("正在删除容器...");
            dockerClient.removeContainerCmd(containerId).exec();
            System.out.println("🧹 容器清理完毕");
        }


        try {
            dockerClient.close(); // 实际使用记得关闭
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}