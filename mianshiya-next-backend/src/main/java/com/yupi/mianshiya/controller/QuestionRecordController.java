package com.yupi.mianshiya.controller;

import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.common.ResultUtils;
import com.yupi.mianshiya.constant.QuestionRecordActionConstant;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.model.dto.questionRecord.QuestionRecordAddRequest;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.entity.UserQuestionRecord;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.service.UserQuestionRecordService;
import com.yupi.mianshiya.service.UserService;
import java.util.Calendar;
import java.util.Date;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户题目行为记录接口
 */
@RestController
@RequestMapping("/question_record")
public class QuestionRecordController {

    @Resource
    private UserQuestionRecordService userQuestionRecordService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionService questionService;

    @PostMapping("/add")
    public BaseResponse<Boolean> addQuestionRecord(@RequestBody QuestionRecordAddRequest questionRecordAddRequest,
                                                   HttpServletRequest request) {
        if (questionRecordAddRequest == null || questionRecordAddRequest.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser == null) {
            return ResultUtils.success(true);
        }
        Question question = questionService.getById(questionRecordAddRequest.getQuestionId());
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        UserQuestionRecord userQuestionRecord = new UserQuestionRecord();
        userQuestionRecord.setUserId(loginUser.getId());
        userQuestionRecord.setQuestionId(questionRecordAddRequest.getQuestionId());
        userQuestionRecord.setQuestionBankId(questionRecordAddRequest.getQuestionBankId());
        String actionType = StringUtils.defaultIfBlank(questionRecordAddRequest.getActionType(), QuestionRecordActionConstant.VIEW);
        userQuestionRecord.setActionType(actionType);
        userQuestionRecord.setIsCorrect(questionRecordAddRequest.getIsCorrect());
        userQuestionRecord.setUsedAi(questionRecordAddRequest.getUsedAi() == null ? 0 : questionRecordAddRequest.getUsedAi());
        userQuestionRecord.setViewDuration(
                questionRecordAddRequest.getViewDuration() == null ? 0 : questionRecordAddRequest.getViewDuration());
        if (shouldSkipDuplicateRecord(loginUser.getId(), questionRecordAddRequest.getQuestionId(), actionType)) {
            return ResultUtils.success(true);
        }
        boolean result = userQuestionRecordService.save(userQuestionRecord);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 简单去重，避免刷新页面或重复点击导致记录过于噪声。
     */
    private boolean shouldSkipDuplicateRecord(long userId, long questionId, String actionType) {
        int duplicateWindowMinutes = QuestionRecordActionConstant.VIEW.equals(actionType) ? 30 : 10;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -duplicateWindowMinutes);
        Date minTime = calendar.getTime();
        UserQuestionRecord duplicateRecord = userQuestionRecordService.getOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(UserQuestionRecord.class)
                        .eq(UserQuestionRecord::getUserId, userId)
                        .eq(UserQuestionRecord::getQuestionId, questionId)
                        .eq(UserQuestionRecord::getActionType, actionType)
                        .ge(UserQuestionRecord::getCreateTime, minTime)
                        .last("limit 1")
        );
        return duplicateRecord != null;
    }
}
