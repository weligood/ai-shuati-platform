package com.yupi.mianshiya.model.dto.questionRecord;

import java.io.Serializable;
import lombok.Data;

/**
 * 用户题目行为记录新增请求
 */
@Data
public class QuestionRecordAddRequest implements Serializable {

    private Long questionId;

    private Long questionBankId;

    private String actionType;

    private Integer isCorrect;

    private Integer usedAi;

    private Integer viewDuration;

    private static final long serialVersionUID = 1L;
}
