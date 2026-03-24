package com.yupi.mianshiya.model.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * AI 题目提示视图
 */
@Data
public class AiQuestionHintVO implements Serializable {

    private Long questionId;

    private Integer level;

    private Integer totalLevels;

    private String hintContent;

    private String source;

    private static final long serialVersionUID = 1L;
}
