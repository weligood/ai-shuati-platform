package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 用户题目行为记录
 */
@TableName(value = "user_question_record")
@Data
public class UserQuestionRecord implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long questionId;

    private Long questionBankId;

    private String actionType;

    private Integer isCorrect;

    private Integer usedAi;

    private Integer viewDuration;

    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
