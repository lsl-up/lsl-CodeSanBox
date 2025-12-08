package com.lsl.lslojcodesandbox;

import cn.hutool.core.io.FileUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class JavaNativeCodeSandBox {

    private static String className;
    private static final ArrayList<String> allOutputs = new ArrayList<>(); //用于存放输出用例

    @SneakyThrows
    public static ExecuteCodeResponse execute(ExecuteCodeRequest executeCodeRequest) {

        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        List<String> inputList = executeCodeRequest.getInputList();


        //设置判题请求
//        String code = FileUtil.readUtf8String("E:\\ideaxm\\lsloj-code-sandbox\\src\\main\\resources\\SimpleCompute.java");
//        System.out.println(code);
//        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
//        executeCodeRequest.setCode(code);
//        executeCodeRequest.setLanguage("java");
//        executeCodeRequest.setInputList(Arrays.asList("7 0", "5 4"));

        //获取类名
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(code);
        CompilationUnit cu = parseResult.getResult().get();
        cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(ClassOrInterfaceDeclaration::isPublic)
                .forEach(classOrInterfaceDeclaration -> {
                    className = classOrInterfaceDeclaration.getNameAsString();
                });
        //检查文件操作
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
            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            executeCodeResponse.setMessage("违规操作");
            throw new Exception();
        }
        ExecuteCodeResponse executeCodeResponse = executeCode(executeCodeRequest);
        return executeCodeResponse;

    }

    private static ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) throws IOException, InterruptedException {


        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();


        //1. 把用户的代码保存为文件
        //创建根路径
        String userDir = System.getProperty("user.dir");
        String userDirPath = userDir + File.separator + "src\\main\\resources\\userCode";
        if (FileUtil.exist(userDirPath)) {
            FileUtil.mkdir(userDirPath);
        }
        //创建子目录+用户文件
        String userPathFile = userDirPath + File.separator + UUID.randomUUID();
        String userPathFileName = userPathFile + File.separator + className + ".java";
        File file = FileUtil.writeUtf8String(code, userPathFileName);
        //   System.out.println(FileUtil.readUtf8String(file));

        //2.编译代码
        String javac = String.format("javac %s", file.getAbsolutePath());
        Process compile = Runtime.getRuntime().exec(javac);
        int waitFor = compile.waitFor();
        System.out.println("编译状态码：" + waitFor);


        String classPath = file.getParent();
        AtomicBoolean isCompleted = new AtomicBoolean(true);

        // 记录运行开始时间
        long totalRunTime = 0L;
        long maxMemoryUsed = 0L;

        if (waitFor == 0) {
            for (String InputArgs : inputList) {
                String[] args = InputArgs.split(" ");
                List<String> command = new ArrayList<>();
                command.add("java");
                command.add("-Xmx5m");        // 最大堆内存5MB
                command.add("-Xms1m");        // 初始堆内存1MB
                command.add("-cp");
                command.add(classPath);
                command.add(className);
                Collections.addAll(command, args);
                //3.运行已编译代码
                ProcessBuilder run = new ProcessBuilder(command);
                Process start = run.start();


                // 记录单个测试用例开始时间
                long testStartTime = System.currentTimeMillis();
                // 超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(3000L);
                        if (isCompleted.get()) {
                            start.destroy();
                            System.out.println("超时异常中断");
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                int runIsFinish = start.waitFor();
                if (runIsFinish == 0) {
                    // 读取标准输出
                    InputStreamReader in = new InputStreamReader(start.getInputStream(), "GBK");
                    BufferedReader bufferedReader = new BufferedReader(in);
                    //StringBuilder stringBuilder = new StringBuilder();

                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        //stringBuilder.append(line);
                        allOutputs.add(line);
                    }
                    //System.out.println(executeCodeResponse.getOutputList());
                    isCompleted.set(false);

                    // 记录单个测试用例结束时间和运行时间
                    long testEndTime = System.currentTimeMillis();
                    long testRunTime = testEndTime - testStartTime;
                    totalRunTime += testRunTime;

                } else {
                    // 读取error输出
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader runErrorMessage = new BufferedReader(new InputStreamReader(start.getErrorStream(), "GBK"));
                    String line;
                    while ((line = runErrorMessage.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    executeCodeResponse.setMessage(stringBuilder.toString());
                    System.out.println(executeCodeResponse.getMessage());
                    isCompleted.set(false);
                    System.out.println("运行失败");
                }
            }
        } else {
            //读取error输出
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader compileErrorMessage = new BufferedReader(new InputStreamReader(compile.getErrorStream(), "GBK"));
            String line;
            while ((line = compileErrorMessage.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            executeCodeResponse.setMessage(stringBuilder.toString());
            System.out.println(executeCodeResponse.getMessage());
        }
        executeCodeResponse.setOutputList(new ArrayList<>(allOutputs));
        List<String> outputList = executeCodeResponse.getOutputList();
        System.out.println("测试用例输出：" + outputList);
        //outputList.forEach(System.out::println);
        if (outputList.size() != inputList.size()) {
            executeCodeResponse.setStatus(3);
            executeCodeResponse.setMessage("结果错误:" + executeCodeResponse.getMessage());
        }else {
            executeCodeResponse.setStatus(2);
        }

        // 设置判题信息
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage("原生执行成功");

        judgeInfo.setTime(totalRunTime < 3000 ? totalRunTime : 3000L); // 只包含运行时间，不包含编译时间
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 输出统计信息
        System.out.println("运行耗时：" + totalRunTime + "ms");
        System.out.println("最大内存使用：" + (maxMemoryUsed / 1024 / 1024) + "MB");
        //释放资源
        if (file.exists()) {
            FileUtil.del(userPathFile);
        }
        allOutputs.clear();
        return executeCodeResponse;
    }
}
