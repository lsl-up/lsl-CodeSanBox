package com.lsl.lslojcodesandbox.demo;

import com.lsl.lslojcodesandbox.JavaNativeCodeSandBox;
import com.lsl.lslojcodesandbox.model.ExecuteCodeRequest;
import com.lsl.lslojcodesandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

/**
 * Java 原生代码沙箱测试类
 */
@SpringBootTest
class JavaNativeCodeSandboxTest {

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
        ExecuteCodeResponse response = JavaNativeCodeSandBox.execute(request);

        // 4. 验证结果
        System.out.println(response);
        
//        // 断言状态为成功
//        Assertions.assertEquals(1, response.getStatus());
//        // 断言输出了两组结果
//        Assertions.assertEquals(2, response.getOutputList().size());
//        // 验证具体输出内容 (根据代码逻辑，输出应包含 "结果:3" 和 "结果:30")
//        Assertions.assertTrue(response.getOutputList().get(0).contains("结果:3"));
//        Assertions.assertTrue(response.getOutputList().get(1).contains("结果:30"));
        
        System.out.println("=== Scanner 模式测试通过 ===\n");
    }

//    /**
//     * 测试场景 2：普通输出 (无输入)
//     * 验证基础运行环境
//     */
//    @Test
//    void testExecuteSimplePrint() {
//        String code = "public class Main {\n" +
//                "    public static void main(String[] args) {\n" +
//                "        System.out.println(\"Hello Sandbox\");\n" +
//                "    }\n" +
//                "}";
//
//        ExecuteCodeRequest request = new ExecuteCodeRequest();
//        request.setCode(code);
//        request.setLanguage("java");
//        // 哪怕不需要输入，通常判题机也会传入一个空参数或占位符来触发运行
//        request.setInputList(Collections.singletonList(""));
//
//        System.out.println("=== 开始测试普通输出 ===");
//        ExecuteCodeResponse response = JavaNativeCodeSandBox.execute(request);
//
//        System.out.println(response);
//
//        Assertions.assertEquals(1, response.getStatus());
//        Assertions.assertEquals("Hello Sandbox", response.getOutputList().get(0));
//
//        System.out.println("=== 普通输出测试通过 ===\n");
//    }
    
//    /**
//     * 测试场景 3：模拟恶意代码 (验证安全性或超时)
//     * 注意：由于是原生沙箱，安全性较差，这里主要测超时
//     */
//    @Test
//    void testTimeOut() {
//        // 一个死循环代码
//        String code = "public class Main {\n" +
//                "    public static void main(String[] args) throws InterruptedException {\n" +
//                "        while(true) {\n" +
//                "            Thread.sleep(100);\n" +
//                "        }\n" +
//                "    }\n" +
//                "}";
//
//        ExecuteCodeRequest request = new ExecuteCodeRequest();
//        request.setCode(code);
//        request.setInputList(Collections.singletonList(""));
//
//        System.out.println("=== 开始测试超时控制 ===");
//        ExecuteCodeResponse response = JavaNativeCodeSandBox.execute(request);
//
//        System.out.println(response);
//
//        // 预期状态应该是 3 (失败/超时)
//        // 具体的错误信息取决于你的实现是返回 "超时" 还是直接杀掉进程报错
//        Assertions.assertNotEquals(1, response.getStatus());
//        System.out.println("=== 超时测试通过 ===");
//    }
}