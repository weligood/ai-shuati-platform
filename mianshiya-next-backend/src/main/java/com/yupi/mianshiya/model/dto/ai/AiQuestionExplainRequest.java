package com.yupi.mianshiya.model.dto.ai;

import java.io.Serializable;
import lombok.Data;

/**
 * AI 题目讲解请求
 */
@Data
public class AiQuestionExplainRequest implements Serializable {

    private Long questionId;

    private static final long serialVersionUID = 1L;
}
