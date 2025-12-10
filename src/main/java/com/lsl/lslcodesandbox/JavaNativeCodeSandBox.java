package com.lsl.lslcodesandbox;

import cn.hutool.core.io.FileUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.lsl.lslcodesandbox.model.ExecuteCodeRequest;
import com.lsl.lslcodesandbox.model.ExecuteCodeResponse;
import com.lsl.lslcodesandbox.model.ExecuteMessage;
import com.lsl.lslcodesandbox.model.JudgeInfo;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Java 原生代码沙箱实现
 */
@Component
public class JavaNativeCodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L; // 超时时间 5秒

    @SneakyThrows
    public static ExecuteCodeResponse execute(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();

        // 1. 安全检查（黑名单机制）
        checkCodeSecurity(code);

        // 2. 解析类名
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(code);
        CompilationUnit cu = parseResult.getResult().orElseThrow(() -> new RuntimeException("解析代码失败"));

        String className = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(ClassOrInterfaceDeclaration::isPublic)
                .filter(c -> !c.isInterface()) // 排除接口
                .findFirst()
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElseThrow(() -> new RuntimeException("未找到可执行的Public类"));

        // 3. 调用执行逻辑
        return executeCode(executeCodeRequest, className);
    }

    /**
     * 核心执行方法
     */
    private static ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest, String className) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        
        // 使用局部变量收集输出
        List<String> outputList = new ArrayList<>();
        
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        // --- 1. 代码保存 ---
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 即使目录不存在，FileUtil.touch 也会自动创建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        
        // 隔离存放：tmpCode/UUID/ClassName.java
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + className + ".java";
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // --- 2. 编译代码 ---
        // 注意：Windows下路径如果包含空格可能会出问题，这里简单处理
        // 命令：javac -encoding utf-8 /path/to/Main.java
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            // 等待编译完成
            ExecuteMessage compileMessage = getProcessMessage(compileProcess, "编译");
            System.out.println("编译信息：" + compileMessage);
            
            if (compileMessage.getExitValue() != 0) {
                // 编译失败
                executeCodeResponse.setStatus(3);
                executeCodeResponse.setMessage("编译失败：\n" + compileMessage.getErrorMessage());
                return executeCodeResponse;
            }
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        // --- 3. 执行代码 ---
        long maxTime = 0;
        long maxMemory = 0; // 原生获取内存较难，这里暂存 0 或后续通过 Runtime 估算

        try {
            for (String inputArgs : inputList) {
                // 构造运行命令：java -Xmx256m -Dfile.encoding=UTF-8 -cp /path/to/dir ClassName
                // 注意：这里不再通过 args 传参，而是通过 Process 的 OutputStream 写入
                List<String> runCmd = new ArrayList<>();
                runCmd.add("java");
                runCmd.add("-Xmx256m"); // 限制最大堆内存
                runCmd.add("-Dfile.encoding=UTF-8");
                runCmd.add("-cp");
                runCmd.add(userCodeParentPath);
                runCmd.add(className);

                ProcessBuilder processBuilder = new ProcessBuilder(runCmd);
                // 必须重定向错误流，否则如果不读取错误流，进程可能会卡死
                // processBuilder.redirectErrorStream(true); 
                
                long startTime = System.currentTimeMillis();
                Process runProcess = processBuilder.start();

                // 重点：通过标准输入流（Stdin）写入测试用例
                // 类似于在控制台手动输入数据
                try (OutputStream outputStream = runProcess.getOutputStream()) {
                    outputStream.write((inputArgs + "\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }

                // 开启超时控制线程
                // 这里使用 Process.waitFor(time, unit) Java 8+ 支持，更优雅
                boolean completed = runProcess.waitFor(TIME_OUT, TimeUnit.MILLISECONDS);
                
                if (!completed) {
                    runProcess.destroy(); // 超时销毁
                    executeCodeResponse.setStatus(3);
                    executeCodeResponse.setMessage("执行超时");
                    break;
                }

                // 获取运行结果
                ExecuteMessage runMessage = getProcessMessage(runProcess, "运行");
                // System.out.println(runMessage); // 调试用
                
                long timeCost = System.currentTimeMillis() - startTime;
                maxTime = Math.max(maxTime, timeCost);

                if (runMessage.getExitValue() != 0) {
                    executeCodeResponse.setStatus(3);
                    executeCodeResponse.setMessage("运行错误：\n" + runMessage.getErrorMessage());
                    // 只要有一个用例失败，判题就算失败（或者你可以选择继续跑完）
                    break; 
                } else {
                    // 成功，收集输出
                    outputList.add(runMessage.getMessage());
                }
            }
        } catch (Exception e) {
            return getErrorResponse(e);
        } finally {
            // --- 4. 资源清理 (非常重要！) ---
            // 无论成功失败，都要删除生成的临时目录
            if (userCodeFile.getParentFile() != null) {
                boolean del = FileUtil.del(userCodeParentPath);
                System.out.println("删除临时目录" + (del ? "成功" : "失败"));
            }
        }

        // --- 5. 结果封装 ---
        if (outputList.size() == inputList.size()) {
            executeCodeResponse.setStatus(1); // 成功
            executeCodeResponse.setMessage("执行成功");
        }
        
        executeCodeResponse.setOutputList(outputList);
        
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        // 原生实现难以精确监控内存，这里暂给 0 或一个估算值
        judgeInfo.setMemory(0L); 
        
        executeCodeResponse.setJudgeInfo(judgeInfo);

        return executeCodeResponse;
    }

    /**
     * 获取进程的输出信息（Stdout + Stderr）
     */
    private static ExecuteMessage getProcessMessage(Process process, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            // 读取标准输出
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder stdoutBuilder = new StringBuilder();
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                stdoutBuilder.append(line).append("\n"); // 还原换行符
            }
            // 这里的 trim 视情况而定，有些题目答案需要保留尾部空格
            executeMessage.setMessage(stdoutBuilder.toString().trim());

            // 读取错误输出
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder stderrBuilder = new StringBuilder();
            while ((line = stderrReader.readLine()) != null) {
                stderrBuilder.append(line).append("\n");
            }
            executeMessage.setErrorMessage(stderrBuilder.toString().trim());

            executeMessage.setExitValue(process.exitValue());
        } catch (Exception e) {
            executeMessage.setErrorMessage(opName + "时发生异常: " + e.getMessage());
        }
        return executeMessage;
    }

    /**
     * 获取错误响应
     */
    private static ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 2 表示系统内部错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
    
    /**
     * 简单的安全检查
     */
    private static void checkCodeSecurity(String code) throws Exception {
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(code);
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return;
        
        // 检查文件操作
        boolean hasFileOperations = cu.findAll(MethodCallExpr.class).stream()
                .anyMatch(method -> method.getNameAsString().contains("File") ||
                        method.getNameAsString().contains("delete") ||
                        method.getNameAsString().contains("write"));

        // 检查网络操作
        boolean hasNetworkOperations = cu.findAll(MethodCallExpr.class).stream()
                .anyMatch(method -> method.getNameAsString().contains("connect") ||
                        method.getNameAsString().contains("socket") ||
                        method.getNameAsString().contains("http"));

        // 检查系统调用
        boolean hasSystemCalls = cu.findAll(MethodCallExpr.class).stream()
                .anyMatch(method -> method.getNameAsString().contains("exec") ||
                        method.getNameAsString().contains("Runtime"));

        if (hasFileOperations || hasNetworkOperations || hasSystemCalls) {
            throw new RuntimeException("检测到违规代码操作，已拦截");
        }
    }


}