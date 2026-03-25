package com.weligood.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 管理员题库草稿
 */
@Data
public class AiAdminBankDraftVO implements Serializable {

    private String title;

    private String description;

    private String picture;

    private List<String> suggestedQuestionTitles;

    private List<String> operationTips;

    private String summary;

    private String source;

    private static final long serialVersionUID = 1L;
}


