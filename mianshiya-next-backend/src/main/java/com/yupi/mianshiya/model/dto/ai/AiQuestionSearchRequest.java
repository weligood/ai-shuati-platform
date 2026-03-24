package com.yupi.mianshiya.model.dto.ai;

import java.io.Serializable;
import lombok.Data;

/**
 * AI 智能搜索请求
 */
@Data
public class AiQuestionSearchRequest implements Serializable {

    /**
     * 搜索内容
     */
    private String query;

    /**
     * 结果数量
     */
    private Integer pageSize;

    private static final long serialVersionUID = 1L;
}
