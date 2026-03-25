package com.yupi.mianshiya.model.dto.ai;

import java.io.Serializable;
import lombok.Data;

/**
 * 管理员生成题库草稿请求
 */
@Data
public class AiAdminBankDraftRequest implements Serializable {

    /**
     * 生成主题 / 岗位方向
     */
    private String topic;

    /**
     * 当前题库标题
     */
    private String currentTitle;

    /**
     * 当前题库描述
     */
    private String currentDescription;

    private static final long serialVersionUID = 1L;
}
