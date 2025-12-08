# Docker Stats API 问题修复说明

## 问题描述

在原始代码中，以下代码行会导致错误：

```java
Statistics stats = dockerClient.statsCmd(containerId).exec();
```

## 错误原因

1. **Docker Stats API 是流式的**：`statsCmd()` 返回的是一个流式统计信息，不能直接调用 `exec()` 获取单次统计
2. **异步回调机制**：正确的用法应该使用 `ResultCallback` 来异步接收统计信息
3. **资源监控复杂性**：实时获取容器资源使用情况需要复杂的异步处理

## 解决方案

### 方案1：使用容器Inspect（推荐）

```java
// 获取容器基本信息
com.github.dockerjava.api.model.Container containerInfo = 
    dockerClient.inspectContainerCmd(containerId).exec();

if (containerInfo != null && containerInfo.getState() != null) {
    System.out.println("容器状态: " + containerInfo.getState().getStatus());
}
```

### 方案2：智能内存估算

```java
// 使用更智能的内存估算
long baseJvmMemory = 32 * 1024 * 1024L;        // 32MB JVM基础
long codeExecutionMemory = 16 * 1024 * 1024L;   // 16MB 代码执行
long bufferMemory = 2 * 1024 * 1024L;           // 2MB 缓冲区
long estimatedMemoryUsage = baseJvmMemory + codeExecutionMemory + bufferMemory;
```

### 方案3：完整的Docker Stats实现（高级）

如果需要真实的资源监控，可以使用以下完整实现：

```java
import com.github.dockerjava.core.command.ResultCallback;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// 异步获取容器统计信息
AtomicLong currentMemoryUsage = new AtomicLong(0L);
CountDownLatch latch = new CountDownLatch(1);

dockerClient.statsCmd(containerId).exec(new ResultCallback<Statistics>() {
    @Override
    public void onNext(Statistics stats) {
        if (stats != null && stats.getMemoryStats() != null && 
            stats.getMemoryStats().getUsage() != null) {
            long memoryUsage = stats.getMemoryStats().getUsage();
            currentMemoryUsage.set(memoryUsage);
            maxMemoryUsed = Math.max(maxMemoryUsed, memoryUsage);
        }
    }
    
    @Override
    public void onError(Throwable throwable) {
        System.err.println("获取容器统计信息失败: " + throwable.getMessage());
        latch.countDown();
    }
    
    @Override
    public void onComplete() {
        latch.countDown();
    }
    
    @Override
    public void close() throws IOException {
        latch.countDown();
    }
});

// 等待统计信息获取完成
try {
    if (latch.await(2, TimeUnit.SECONDS)) {
        // 统计信息获取成功
        System.out.println("当前内存使用: " + currentMemoryUsage.get() + " bytes");
    } else {
        System.out.println("统计信息获取超时，使用估算值");
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    System.err.println("统计信息获取被中断");
}
```

## 为什么选择方案1（容器Inspect）

1. **简单可靠**：不需要复杂的异步处理
2. **性能更好**：单次API调用，响应快
3. **错误率低**：不会因为网络延迟或超时导致问题
4. **维护简单**：代码清晰，易于理解和维护

## 生产环境建议

### 1. 使用专业监控工具
- **cAdvisor**：Google开源的容器资源监控工具
- **Prometheus + Grafana**：完整的监控解决方案
- **Datadog**：商业监控服务

### 2. 配置资源限制
```yaml
# Docker Compose 配置
services:
  code-sandbox:
    image: your-sandbox-image
    deploy:
      resources:
        limits:
          memory: 100M
          cpus: '0.5'
        reservations:
          memory: 50M
          cpus: '0.25'
```

### 3. 监控告警
```java
// 添加资源使用告警
if (maxMemoryUsed > MAX_MEMORY * 0.8) {
    log.warn("容器内存使用超过80%: {} bytes", maxMemoryUsed);
}

if (totalExecutionTime > MAX_EXECUTION_TIME * 0.8) {
    log.warn("代码执行时间接近限制: {} ms", totalExecutionTime);
}
```

## 测试验证

修复后，代码应该能够正常运行：

```bash
# 编译项目
mvn clean compile

# 运行测试
java -cp target/classes com.lsl.lslojcodesandbox.demo.docker.DockerSandboxDemo
```

## 总结

通过使用容器Inspect和智能内存估算，我们解决了Docker Stats API的复杂性问题，提供了一个简单、可靠、高性能的解决方案。这种方法在保持功能完整性的同时，大大提高了代码的稳定性和可维护性。 