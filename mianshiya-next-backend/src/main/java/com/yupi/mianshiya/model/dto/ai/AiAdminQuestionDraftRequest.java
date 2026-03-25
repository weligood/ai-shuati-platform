package com.yupi.mianshiya.model.dto.ai;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 管理员生成题目草稿请求
 */
@Data
public class AiAdminQuestionDraftRequest implements Serializable {

    /**
     * 生成主题 / 目标方向
     */
    private String topic;

    /**
     * 当前标题
     */
    private String currentTitle;

    /**
     * 当前题目内容
     */
    private String currentContent;

    /**
     * 当前答案
     */
    private String currentAnswer;

    /**
     * 当前标签
     */
    private List<String> currentTags;

    private static final long serialVersionUID = 1L;
}
