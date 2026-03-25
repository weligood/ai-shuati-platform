package com.yupi.mianshiya.model.dto.ai;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 管理员批量生成题目草稿请求
 */
@Data
public class AiAdminQuestionBatchDraftRequest implements Serializable {

    /**
     * 当前输入
     */
    private String prompt;

    /**
     * 历史对话
     */
    private List<String> history;

    /**
     * 生成数量
     */
    private Integer count;

    private static final long serialVersionUID = 1L;
}
