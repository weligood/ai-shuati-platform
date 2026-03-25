package com.weligood.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 管理员批量题目草稿响应
 */
@Data
public class AiAdminQuestionBatchDraftVO implements Serializable {

    /**
     * AI 回复
     */
    private String assistantMessage;

    /**
     * 题目草稿列表
     */
    private List<AiAdminQuestionDraftVO> questionDrafts;

    /**
     * 来源
     */
    private String source;

    private static final long serialVersionUID = 1L;
}


