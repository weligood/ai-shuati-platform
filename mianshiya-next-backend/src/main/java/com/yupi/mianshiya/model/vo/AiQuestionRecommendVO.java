package com.yupi.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * AI 推荐下一题视图
 */
@Data
public class AiQuestionRecommendVO implements Serializable {

    /**
     * 推荐理由
     */
    private String recommendationReason;

    /**
     * 推荐题目列表
     */
    private List<QuestionVO> questions;

    /**
     * 数据来源
     */
    private String source;

    private static final long serialVersionUID = 1L;
}
