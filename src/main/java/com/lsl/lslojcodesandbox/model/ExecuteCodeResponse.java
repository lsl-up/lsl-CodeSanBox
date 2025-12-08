package com.lsl.lslojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {

    private List<String> outputList;

    /**
     * 判题信息
     */
    private JudgeInfo judgeInfo;

    /**
     * 执行状态
      */
    private Integer status;

    /**
     * 接口信息
     */
    private String message;
}
