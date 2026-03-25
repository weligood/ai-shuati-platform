package com.yupi.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 管理员题目草稿
 */
@Data
public class AiAdminQuestionDraftVO implements Serializable {

    private String title;

    private String content;

    private String answer;

    private List<String> tags;

    private String summary;

    private String source;

    private static final long serialVersionUID = 1L;
}
