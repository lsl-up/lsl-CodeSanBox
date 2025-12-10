package com.lsl.lslojcodesandbox.demo.docker;

import com.lsl.lslojcodesandbox.JavaDockerCodeSandbox;
import com.lsl.lslojcodesandbox.JavaDockerCodeSandbox_Old;
import com.lsl.lslojcodesandbox.model.ExecuteCodeRequest;
import com.lsl.lslojcodesandbox.model.ExecuteCodeResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Docker 沙箱开发环境测试类
 * 作用：不启动 Spring Boot 容器，直接测试 Docker 沙箱的核心逻辑
 */
public class DockerSandboxDevTest {

    public static void main(String[] args) {
        System.out.println("🚀 开始 Docker 沙箱冒烟测试...");
        
        // 1. 准备一段符合 ACM 模式的用户代码 (A + B)
        // 注意：使用 Scanner 读取标准输入 System.in

        String code = "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        if (sc.hasNextInt()) {\n" +
                "            int a = sc.nextInt();\n" +
                "            int b = sc.nextInt();\n" +
                "            int c = sc.nextInt();\n" +
                "            try { \n" +
                "                Thread.sleep(1000);\n" +
                "            } catch (InterruptedException e) {}\n" +
                "            System.out.println(\"计算结果:\" + (a + b + c));\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // 2. 构造测试用例 (两组数据)
        List<String> inputList = Arrays.asList("5 5 5", "10 10 10");
        
        ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                .code(code)
                .language("java")
                .inputList(inputList)
                .build();

        // 3. 调用沙箱执行
        try {
            long startTime = System.currentTimeMillis();
            JavaDockerCodeSandbox javaDockerCodeSandbox = new JavaDockerCodeSandbox();
            // 直接调用静态方法进行测试
            ExecuteCodeResponse response = javaDockerCodeSandbox.execute(request);
            
            // 4. 分析结果
            System.out.println("\n-------------------------------------------");
            System.out.println("📝 测试报告：");
            System.out.println("-------------------------------------------");
            System.out.println("状态码 (1:运行中 2:成功 3:失败): " + response.getStatus());
            System.out.println("运行信息: " + response.getMessage());
            
            if (response.getJudgeInfo() != null) {
                System.out.println("内存占用: " + (response.getJudgeInfo().getMemory() / 1024 / 1024) + "MB");
                System.out.println("运行时间: " + response.getJudgeInfo().getTime() + "ms");
            }
            System.out.println("📝 结果：");
            System.out.println(response.getOutputList());
            
        } catch (Exception e) {
            System.err.println("\n❌ 发生严重错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 强制退出，因为 Docker Client 可能会有一些守护线程导致 main 不自动结束
        System.exit(0);
    }
}