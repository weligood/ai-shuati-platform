package com.weligood.mianshiya.model.dto.ai;

import java.io.Serializable;
import lombok.Data;

/**
 * AI 题目提示请求
 */
@Data
public class AiQuestionHintRequest implements Serializable {

    private Long questionId;

    private Integer level;

    private static final long serialVersionUID = 1L;
}


