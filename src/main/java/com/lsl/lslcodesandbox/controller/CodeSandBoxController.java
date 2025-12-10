package com.lsl.lslcodesandbox.controller;

import com.lsl.lslcodesandbox.JavaNativeCodeSandBox;
import com.lsl.lslcodesandbox.model.ExecuteCodeRequest;
import com.lsl.lslcodesandbox.model.ExecuteCodeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/codesandbox")
@CrossOrigin(origins = "*")
public class CodeSandBoxController {

    @Autowired
    private JavaNativeCodeSandBox javaNativeCodeSandBox;

    @PostMapping("/execute")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) throws Exception {
        try {
            // 调用代码沙箱
            return JavaNativeCodeSandBox.execute(executeCodeRequest);
        } catch (Exception e) {
            ExecuteCodeResponse response = new ExecuteCodeResponse();
            response.setMessage("调用代码沙箱失败: " + e.getMessage());
            return response;
        }
    }
}