package com.weligood.mianshiya.service;

import com.weligood.mianshiya.model.vo.AiQuestionExplainVO;
import com.weligood.mianshiya.model.vo.AiQuestionHintVO;
import com.weligood.mianshiya.model.vo.AiQuestionRecommendVO;
import com.weligood.mianshiya.model.vo.AiQuestionSearchVO;
import com.weligood.mianshiya.model.vo.AiAdminBankDraftVO;
import com.weligood.mianshiya.model.vo.AiAdminQuestionBatchDraftVO;
import com.weligood.mianshiya.model.vo.AiAdminQuestionDraftVO;
import com.weligood.mianshiya.model.vo.UserAiReportVO;

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
     * 管理员生成题目草稿
     *
     * @param topic         主题
     * @param currentTitle  当前标题
     * @param currentContent 当前内容
     * @param currentAnswer 当前答案
     * @param currentTags   当前标签
     * @return 题目草稿
     */
    AiAdminQuestionDraftVO generateAdminQuestionDraft(String topic, String currentTitle, String currentContent,
                                                      String currentAnswer, java.util.List<String> currentTags);

    /**
     * 管理员对话式批量生成题目草稿
     *
     * @param prompt  当前输入
     * @param history 历史对话
     * @param count   生成数量
     * @return 批量草稿
     */
    AiAdminQuestionBatchDraftVO generateAdminQuestionBatchDraft(String prompt, java.util.List<String> history,
                                                                Integer count);

    /**
     * 管理员生成题库草稿
     *
     * @param topic             主题
     * @param currentTitle      当前标题
     * @param currentDescription 当前描述
     * @return 题库草稿
     */
    AiAdminBankDraftVO generateAdminBankDraft(String topic, String currentTitle, String currentDescription);

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


