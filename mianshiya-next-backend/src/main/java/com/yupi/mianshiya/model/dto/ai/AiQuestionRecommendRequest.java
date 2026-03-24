package com.yupi.mianshiya.model.dto.ai;

import java.io.Serializable;
import lombok.Data;

/**
 * AI 推荐下一题请求
 */
@Data
public class AiQuestionRecommendRequest implements Serializable {

    /**
     * 当前题目 id
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;
}
