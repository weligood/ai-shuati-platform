package com.yupi.mianshiya.service;

import com.yupi.mianshiya.model.vo.AiQuestionExplainVO;
import com.yupi.mianshiya.model.vo.AiQuestionHintVO;
import com.yupi.mianshiya.model.vo.AiQuestionRecommendVO;
import com.yupi.mianshiya.model.vo.AiQuestionSearchVO;
import com.yupi.mianshiya.model.vo.UserAiReportVO;

/**
 * AI 服务
 */
public interface AiService {

    AiQuestionExplainVO getQuestionExplain(long questionId);

    AiQuestionHintVO getQuestionHint(long questionId, Integer level);

    /**
     * 推荐下一题
     *
     * @param userId     用户 id
     * @param questionId 当前题目 id
     * @return 推荐结果
     */
    AiQuestionRecommendVO recommendQuestions(long userId, Long questionId);

    /**
     * AI 智能搜索
     *
     * @param query    搜索内容
     * @param pageSize 数量
     * @return 搜索结果
     */
    AiQuestionSearchVO aiSearchQuestions(String query, Integer pageSize);

    UserAiReportVO getUserAiReport(long userId, String reportType);

    /**
     * 清理题目 AI 缓存
     *
     * @param questionId 题目 id
     */
    void clearQuestionAiCache(long questionId);

    /**
     * 清理题目 AI 相关数据
     *
     * @param questionId 题目 id
     */
    void clearQuestionAiData(long questionId);
}
