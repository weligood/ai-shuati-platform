package com.weligood.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * AI 题目讲解视图
 */
@Data
public class AiQuestionExplainVO implements Serializable {

    private Long questionId;

    private String plainExplanation;

    private List<String> keyPoints;

    private List<String> pitfalls;

    private List<String> followUpQuestions;

    private String source;

    private static final long serialVersionUID = 1L;
}


