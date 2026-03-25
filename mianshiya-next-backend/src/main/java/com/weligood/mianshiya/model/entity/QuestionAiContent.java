package com.weligood.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 题目 AI 内容
 */
@TableName(value = "question_ai_content")
@Data
public class QuestionAiContent implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long questionId;

    private String plainExplanation;

    private String keyPointsJson;

    private String thinkingHintsJson;

    private String pitfallsJson;

    private String followUpQuestionsJson;

    private String modelName;

    private Integer version;

    private Integer status;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}


