package com.lsl.lslcodesandbox.Utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 简单的 Docker 容器池
 * 作用：预先启动一堆容器，让它们待命，避免每次请求都创建销毁
 */
@Component
public class ContainerPool {

    // 容器池大小
    private static final int POOL_SIZE = 10;
    
    // 镜像名称
    private static final String IMAGE = "oj-sandbox-java:1.0";
    
    // 根工作目录（所有容器共享这个挂载点）
    private static final String ROOT_WORK_DIR = System.getProperty("java.io.tmpdir") + File.separator + "oj-sandbox-files";

    // 存放容器 ID 的阻塞队列（线程安全）
    private final BlockingQueue<String> availableContainers = new ArrayBlockingQueue<>(POOL_SIZE);
    
    private DockerClient dockerClient;

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    /**
     * 项目启动时自动执行：初始化车队
     */
    @PostConstruct
    public void init() {
        // 1. 初始化 Docker Client (复用你之前的配置逻辑)
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .build();
        dockerClient = DockerClientBuilder.getInstance(config).withDockerHttpClient(httpClient).build();

        // 2. 确保挂载总目录存在
        new File(ROOT_WORK_DIR).mkdirs();

        // 3. 预热容器
        System.out.println("正在初始化 " + POOL_SIZE + " 个容器...");
        for (int i = 0; i < POOL_SIZE; i++) {
            String containerId = createAndStartContainer();
            availableContainers.offer(containerId);
        }
        System.out.println("初始化完成，" + POOL_SIZE + " 个容器已待命！");
    }

    /**
     * 创建并启动一个“空转”的容器
     */
    private String createAndStartContainer() {
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1024 * 1024L); // 限制内存 100MB，防止 OOM
        hostConfig.withMemorySwap(0L);             // 禁止使用 Swap 交换分区
        hostConfig.withCpuCount(1L);               // 限制使用 1 核 CPU
        hostConfig.withPidsLimit(100L);            // 限制进程数，防止 Fork 炸弹攻击
        // 关键点：挂载总目录
        hostConfig.setBinds(new Bind(ROOT_WORK_DIR, new Volume("/app")));

        CreateContainerResponse response = dockerClient.createContainerCmd(IMAGE)
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(false)
                // 关键点：让容器启动后死循环空转，不要退出
                .withCmd("tail", "-f", "/dev/null") 
                .exec();

        String id = response.getId();
        dockerClient.startContainerCmd(id).exec();
        return id;
    }

    /**
     * 获取一个容器
     */
    public String acquire() throws InterruptedException {
        // 如果池子空了，这就阻塞等待
        return availableContainers.take();
    }

    /**
     * 归还一个容器
     */
    public void release(String containerId) {
        // 这里可以加一些清理逻辑（比如清理容器内临时文件，但因为我们是只读文件系统+挂载，其实不用清）
        availableContainers.offer(containerId);
    }

    // 关闭所有容器（可选，用于应用关闭时优雅退出）
    @PreDestroy
    public void shutdown() throws InterruptedException {
        System.out.println("正在关闭所有容器...");
        List<String> remaining = new ArrayList<>();
        availableContainers.drainTo(remaining);

        for (String id : remaining) {
            try {
                dockerClient.stopContainerCmd(id).exec();
                dockerClient.removeContainerCmd(id).exec();
            } catch (Exception e) {
                System.err.println("关闭容器失败: " + id + ", " + e.getMessage());
            }
        }
        try {
            dockerClient.close();
        } catch (IOException e) {

        }
    }
}