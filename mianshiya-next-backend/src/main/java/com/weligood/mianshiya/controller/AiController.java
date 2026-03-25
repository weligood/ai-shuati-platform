package com.weligood.mianshiya.controller;

import com.weligood.mianshiya.common.BaseResponse;
import com.weligood.mianshiya.common.ErrorCode;
import com.weligood.mianshiya.common.ResultUtils;
import com.weligood.mianshiya.exception.BusinessException;
import com.weligood.mianshiya.model.dto.ai.AiAdminBankDraftRequest;
import com.weligood.mianshiya.model.dto.ai.AiAdminQuestionBatchDraftRequest;
import com.weligood.mianshiya.model.dto.ai.AiAdminQuestionDraftRequest;
import com.weligood.mianshiya.model.dto.ai.AiQuestionExplainRequest;
import com.weligood.mianshiya.model.dto.ai.AiQuestionHintRequest;
import com.weligood.mianshiya.model.dto.ai.AiQuestionRecommendRequest;
import com.weligood.mianshiya.model.dto.ai.AiQuestionSearchRequest;
import com.weligood.mianshiya.model.entity.User;
import com.weligood.mianshiya.model.vo.AiAdminBankDraftVO;
import com.weligood.mianshiya.model.vo.AiAdminQuestionBatchDraftVO;
import com.weligood.mianshiya.model.vo.AiAdminQuestionDraftVO;
import com.weligood.mianshiya.model.vo.AiQuestionExplainVO;
import com.weligood.mianshiya.model.vo.AiQuestionHintVO;
import com.weligood.mianshiya.model.vo.AiQuestionRecommendVO;
import com.weligood.mianshiya.model.vo.AiQuestionSearchVO;
import com.weligood.mianshiya.model.vo.UserAiReportVO;
import com.weligood.mianshiya.service.AiService;
import com.weligood.mianshiya.service.UserService;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 接口
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiService aiService;

    @Resource
    private UserService userService;

    @PostMapping("/question/explain")
    public BaseResponse<AiQuestionExplainVO> getQuestionExplain(
            @RequestBody AiQuestionExplainRequest aiQuestionExplainRequest) {
        if (aiQuestionExplainRequest == null || aiQuestionExplainRequest.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(aiService.getQuestionExplain(aiQuestionExplainRequest.getQuestionId()));
    }

    @PostMapping("/question/hint")
    public BaseResponse<AiQuestionHintVO> getQuestionHint(@RequestBody AiQuestionHintRequest aiQuestionHintRequest) {
        if (aiQuestionHintRequest == null || aiQuestionHintRequest.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(
                aiService.getQuestionHint(aiQuestionHintRequest.getQuestionId(), aiQuestionHintRequest.getLevel()));
    }

    @PostMapping("/question/recommend")
    public BaseResponse<AiQuestionRecommendVO> recommendQuestions(
            @RequestBody(required = false) AiQuestionRecommendRequest aiQuestionRecommendRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long questionId = aiQuestionRecommendRequest == null ? null : aiQuestionRecommendRequest.getQuestionId();
        return ResultUtils.success(aiService.recommendQuestions(loginUser.getId(), questionId));
    }

    @PostMapping("/search/questions")
    public BaseResponse<AiQuestionSearchVO> aiSearchQuestions(@RequestBody AiQuestionSearchRequest aiQuestionSearchRequest) {
        if (aiQuestionSearchRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(aiService.aiSearchQuestions(
                aiQuestionSearchRequest.getQuery(), aiQuestionSearchRequest.getPageSize()));
    }

    @GetMapping("/user/report")
    public BaseResponse<UserAiReportVO> getUserAiReport(String reportType, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiService.getUserAiReport(loginUser.getId(), reportType));
    }

    @PostMapping("/admin/question/draft")
    public BaseResponse<AiAdminQuestionDraftVO> generateAdminQuestionDraft(
            @RequestBody(required = false) AiAdminQuestionDraftRequest draftRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (draftRequest == null) {
            draftRequest = new AiAdminQuestionDraftRequest();
        }
        return ResultUtils.success(aiService.generateAdminQuestionDraft(
                draftRequest.getTopic(),
                draftRequest.getCurrentTitle(),
                draftRequest.getCurrentContent(),
                draftRequest.getCurrentAnswer(),
                draftRequest.getCurrentTags()));
    }

    @PostMapping("/admin/question/draft/batch")
    public BaseResponse<AiAdminQuestionBatchDraftVO> generateAdminQuestionBatchDraft(
            @RequestBody(required = false) AiAdminQuestionBatchDraftRequest draftRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (draftRequest == null) {
            draftRequest = new AiAdminQuestionBatchDraftRequest();
        }
        return ResultUtils.success(aiService.generateAdminQuestionBatchDraft(
                draftRequest.getPrompt(),
                draftRequest.getHistory(),
                draftRequest.getCount()));
    }

    @PostMapping("/admin/bank/draft")
    public BaseResponse<AiAdminBankDraftVO> generateAdminBankDraft(
            @RequestBody(required = false) AiAdminBankDraftRequest draftRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (draftRequest == null) {
            draftRequest = new AiAdminBankDraftRequest();
        }
        return ResultUtils.success(aiService.generateAdminBankDraft(
                draftRequest.getTopic(),
                draftRequest.getCurrentTitle(),
                draftRequest.getCurrentDescription()));
    }
}


