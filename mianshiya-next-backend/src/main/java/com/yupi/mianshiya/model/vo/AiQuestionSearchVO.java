package com.yupi.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * AI 智能搜索视图
 */
@Data
public class AiQuestionSearchVO implements Serializable {

    /**
     * 改写后的搜索词
     */
    private String rewrittenQuery;

    /**
     * 搜索意图说明
     */
    private String reason;

    /**
     * 提取出的标签
     */
    private List<String> tags;

    /**
     * 搜索结果
     */
    private List<QuestionVO> questions;

    /**
     * 数据来源
     */
    private String source;

    private static final long serialVersionUID = 1L;
}
