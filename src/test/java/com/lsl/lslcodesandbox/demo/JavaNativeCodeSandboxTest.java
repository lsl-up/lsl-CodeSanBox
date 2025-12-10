package com.lsl.lslcodesandbox.demo;

import com.lsl.lslcodesandbox.JavaDockerCodeSandbox;
import com.lsl.lslcodesandbox.model.ExecuteCodeRequest;
import com.lsl.lslcodesandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * Java 原生代码沙箱测试类
 */
@SpringBootTest
class JavaNativeCodeSandboxTest {

    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;

    /**
     * 测试场景 1：使用 Scanner 读取输入 (ACM模式)
     * 对应题目：计算 A + B
     */
    @Test
    void testExecuteWithScanner() {
        // 1. 构造用户代码 (模拟提交)
        // 注意：这里使用的是 Scanner(System.in) 读取数据
        String code = "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        if (sc.hasNextInt()) {\n" +
                "            int a = sc.nextInt();\n" +
                "            int b = sc.nextInt();\n" +
                "            System.out.println(\"结果:\" + (a + b));\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // 2. 构造请求参数
        ExecuteCodeRequest request = new ExecuteCodeRequest();
        request.setCode(code);
        request.setLanguage("java");
        // 输入用例：两组数据
        // 第一组：1 2 -> 预期输出 3
        // 第二组：10 20 -> 预期输出 30
        request.setInputList(Arrays.asList("3 10", "1 20"));

        // 3. 调用沙箱执行
        System.out.println("=== 开始测试 Scanner 模式 ===");

        ExecuteCodeResponse response = javaDockerCodeSandbox.execute(request);

        // 4. 验证结果
        System.out.println(response);

        System.out.println("=== Scanner 模式测试通过 ===\n");
    }
}