package com.yupi.mianshiya.controller;

import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.common.ResultUtils;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.ai.AiQuestionExplainRequest;
import com.yupi.mianshiya.model.dto.ai.AiQuestionHintRequest;
import com.yupi.mianshiya.model.dto.ai.AiQuestionRecommendRequest;
import com.yupi.mianshiya.model.dto.ai.AiQuestionSearchRequest;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.vo.AiQuestionExplainVO;
import com.yupi.mianshiya.model.vo.AiQuestionHintVO;
import com.yupi.mianshiya.model.vo.AiQuestionRecommendVO;
import com.yupi.mianshiya.model.vo.AiQuestionSearchVO;
import com.yupi.mianshiya.model.vo.UserAiReportVO;
import com.yupi.mianshiya.service.AiService;
import com.yupi.mianshiya.service.UserService;
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
}
