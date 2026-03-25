package com.yupi.mianshiya.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.constant.QuestionRecordActionConstant;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.manager.QwenManager;
import com.yupi.mianshiya.model.dto.question.QuestionQueryRequest;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.QuestionAiContent;
import com.yupi.mianshiya.model.entity.UserAiReport;
import com.yupi.mianshiya.model.entity.UserQuestionRecord;
import com.yupi.mianshiya.model.vo.AiAdminBankDraftVO;
import com.yupi.mianshiya.model.vo.AiAdminQuestionBatchDraftVO;
import com.yupi.mianshiya.model.vo.AiAdminQuestionDraftVO;
import com.yupi.mianshiya.model.vo.AiQuestionExplainVO;
import com.yupi.mianshiya.model.vo.AiQuestionHintVO;
import com.yupi.mianshiya.model.vo.AiQuestionRecommendVO;
import com.yupi.mianshiya.model.vo.AiQuestionSearchVO;
import com.yupi.mianshiya.model.vo.QuestionVO;
import com.yupi.mianshiya.model.vo.UserAiReportVO;
import com.yupi.mianshiya.service.AiService;
import com.yupi.mianshiya.service.QuestionAiContentService;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.service.UserAiReportService;
import com.yupi.mianshiya.service.UserQuestionRecordService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * AI 服务实现
 */
@Service
public class AiServiceImpl implements AiService {

    private static final String MODEL_NAME = "mock-study-coach-v1";

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionAiContentService questionAiContentService;

    @Resource
    private UserQuestionRecordService userQuestionRecordService;

    @Resource
    private UserAiReportService userAiReportService;

    @Resource
    private QwenManager qwenManager;

    @Override
    public AiQuestionExplainVO getQuestionExplain(long questionId) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
        QuestionAiContent questionAiContent = getOrCreateQuestionAiContent(questionId);
        AiQuestionExplainVO aiQuestionExplainVO = new AiQuestionExplainVO();
        aiQuestionExplainVO.setQuestionId(questionId);
        aiQuestionExplainVO.setPlainExplanation(questionAiContent.getPlainExplanation());
        aiQuestionExplainVO.setKeyPoints(parseJsonArray(questionAiContent.getKeyPointsJson()));
        aiQuestionExplainVO.setPitfalls(parseJsonArray(questionAiContent.getPitfallsJson()));
        aiQuestionExplainVO.setFollowUpQuestions(parseJsonArray(questionAiContent.getFollowUpQuestionsJson()));
        aiQuestionExplainVO.setSource(getSourceByModelName(questionAiContent.getModelName()));
        return aiQuestionExplainVO;
    }

    @Override
    public AiQuestionHintVO getQuestionHint(long questionId, Integer level) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
        QuestionAiContent questionAiContent = getOrCreateQuestionAiContent(questionId);
        List<String> hints = parseJsonArray(questionAiContent.getThinkingHintsJson());
        ThrowUtils.throwIf(hints.isEmpty(), ErrorCode.SYSTEM_ERROR, "AI 提示生成失败");
        int safeLevel = level == null || level <= 0 ? 1 : level;
        safeLevel = Math.min(safeLevel, hints.size());
        AiQuestionHintVO aiQuestionHintVO = new AiQuestionHintVO();
        aiQuestionHintVO.setQuestionId(questionId);
        aiQuestionHintVO.setLevel(safeLevel);
        aiQuestionHintVO.setTotalLevels(hints.size());
        aiQuestionHintVO.setHintContent(hints.get(safeLevel - 1));
        aiQuestionHintVO.setSource(getSourceByModelName(questionAiContent.getModelName()));
        return aiQuestionHintVO;
    }

    @Override
    public AiQuestionRecommendVO recommendQuestions(long userId, Long questionId) {
        ThrowUtils.throwIf(userId <= 0, ErrorCode.PARAMS_ERROR);
        List<String> weakTags = getWeakTags(userId, 5);
        QueryWrapper<Question> queryWrapper = Wrappers.query();
        queryWrapper.ne(questionId != null, "id", questionId);
        queryWrapper.orderByDesc("createTime");
        queryWrapper.last("limit 30");
        List<Question> candidateQuestionList = questionService.list(queryWrapper);
        List<Question> recommendedQuestions = candidateQuestionList.stream()
                .sorted((o1, o2) -> Integer.compare(scoreQuestion(o2, weakTags), scoreQuestion(o1, weakTags)))
                .limit(3)
                .collect(Collectors.toList());
        List<QuestionVO> questionVOList = recommendedQuestions.stream()
                .map(question -> questionService.getQuestionVO(question, null))
                .collect(Collectors.toList());
        AiQuestionRecommendVO recommendVO = new AiQuestionRecommendVO();
        recommendVO.setQuestions(questionVOList);
        String localReason = weakTags.isEmpty()
                ? "根据你最近的刷题轨迹，优先推荐最新且覆盖面更广的题目，帮助你保持刷题连续性。"
                : String.format("结合你最近较薄弱的 %s 方向，优先推荐这些覆盖相关考点的题目。", String.join("、", weakTags.subList(0, Math.min(3, weakTags.size()))));
        String qwenReason = buildRecommendReasonByQwen(weakTags, questionVOList);
        recommendVO.setRecommendationReason(StringUtils.defaultIfBlank(qwenReason, localReason));
        recommendVO.setSource(StringUtils.isNotBlank(qwenReason) ? "qwen" : "local");
        return recommendVO;
    }

    @Override
    public AiQuestionSearchVO aiSearchQuestions(String query, Integer pageSize) {
        ThrowUtils.throwIf(StringUtils.isBlank(query), ErrorCode.PARAMS_ERROR, "搜索内容不能为空");
        int safePageSize = pageSize == null || pageSize <= 0 ? 6 : Math.min(pageSize, 12);
        QueryIntent queryIntent = buildSearchIntent(query);
        List<QuestionVO> questionVOList = searchQuestionsByIntent(queryIntent, safePageSize);
        String reason = queryIntent.getReason();
        if (CollUtil.isEmpty(questionVOList)) {
            QueryIntent fallbackIntent = new QueryIntent();
            fallbackIntent.setRewrittenQuery(query.trim());
            fallbackIntent.setReason("严格匹配没有命中结果，已自动退回到宽松关键词搜索。");
            fallbackIntent.setTags(new ArrayList<>());
            fallbackIntent.setSource("fallback");
            questionVOList = searchQuestionsByIntent(fallbackIntent, safePageSize);
            reason = fallbackIntent.getReason();
        }
        if (CollUtil.isEmpty(questionVOList)) {
            questionVOList = questionService.getQuestionVOPage(
                    questionService.listQuestionByPage(buildLatestQuestionRequest(safePageSize)), null).getRecords();
            reason = "当前没有命中强相关题目，已为你展示最新的可练习题目。";
        }
        AiQuestionSearchVO searchVO = new AiQuestionSearchVO();
        searchVO.setRewrittenQuery(queryIntent.getRewrittenQuery());
        searchVO.setReason(reason);
        searchVO.setTags(queryIntent.getTags());
        searchVO.setQuestions(questionVOList);
        searchVO.setSource(queryIntent.getSource());
        return searchVO;
    }

    @Override
    public UserAiReportVO getUserAiReport(long userId, String reportType) {
        ThrowUtils.throwIf(userId <= 0, ErrorCode.PARAMS_ERROR);
        String safeReportType = normalizeReportType(reportType);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = "month".equals(safeReportType) ? endDate.minusDays(29) : endDate.minusDays(6);
        Date startTime = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endTime = Date.from(endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant());
        UserAiReport cachedReport = userAiReportService.getOne(
                Wrappers.lambdaQuery(UserAiReport.class)
                        .eq(UserAiReport::getUserId, userId)
                        .eq(UserAiReport::getReportType, safeReportType)
                        .eq(UserAiReport::getStartDate, java.sql.Date.valueOf(startDate))
                        .eq(UserAiReport::getEndDate, java.sql.Date.valueOf(endDate))
                        .last("limit 1")
        );
        UserQuestionRecord latestRecord = userQuestionRecordService.getOne(
                Wrappers.lambdaQuery(UserQuestionRecord.class)
                        .eq(UserQuestionRecord::getUserId, userId)
                        .between(UserQuestionRecord::getCreateTime, startTime, endTime)
                        .orderByDesc(UserQuestionRecord::getCreateTime)
                        .last("limit 1")
        );
        if (cachedReport != null
                && (latestRecord == null || !latestRecord.getCreateTime().after(cachedReport.getUpdateTime()))) {
            return buildReportFromCache(cachedReport, userId, startTime, endTime);
        }
        List<UserQuestionRecord> userQuestionRecordList = userQuestionRecordService.list(
                Wrappers.lambdaQuery(UserQuestionRecord.class)
                        .eq(UserQuestionRecord::getUserId, userId)
                        .between(UserQuestionRecord::getCreateTime, startTime, endTime)
                        .orderByAsc(UserQuestionRecord::getCreateTime)
        );
        UserAiReportVO reportVO = buildUserReport(userQuestionRecordList, safeReportType, startDate, endDate);
        upsertUserReport(userId, reportVO);
        return reportVO;
    }

    @Override
    public AiAdminQuestionDraftVO generateAdminQuestionDraft(String topic, String currentTitle, String currentContent,
                                                             String currentAnswer, List<String> currentTags) {
        String seed = firstNonBlank(topic, currentTitle, currentContent, currentAnswer);
        ThrowUtils.throwIf(StringUtils.isBlank(seed), ErrorCode.PARAMS_ERROR, "请先输入主题或已有题目信息");
        AiAdminQuestionDraftVO qwenDraft = buildAdminQuestionDraftByQwen(topic, currentTitle, currentContent,
                currentAnswer, currentTags);
        if (qwenDraft != null) {
            return qwenDraft;
        }
        return buildLocalAdminQuestionDraft(topic, currentTitle, currentContent, currentAnswer, currentTags, null);
    }

    @Override
    public AiAdminQuestionBatchDraftVO generateAdminQuestionBatchDraft(String prompt, List<String> history, Integer count) {
        ThrowUtils.throwIf(StringUtils.isBlank(prompt), ErrorCode.PARAMS_ERROR, "请输入你希望批量生成的题目要求");
        int safeCount = count == null || count <= 0 ? 5 : Math.min(count, 10);
        List<String> safeHistory = history == null ? new ArrayList<>() : history.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .limit(20)
                .collect(Collectors.toList());
        AiAdminQuestionBatchDraftVO qwenDraft = buildAdminQuestionBatchDraftByQwen(prompt.trim(), safeHistory, safeCount);
        if (qwenDraft != null) {
            return qwenDraft;
        }
        String context = buildBatchPromptContext(prompt, safeHistory);
        String normalizedTopic = getNormalizedTopic(context);
        List<AiAdminQuestionDraftVO> questionDrafts = buildLocalAdminQuestionBatchDraft(prompt.trim(), safeHistory, safeCount);
        AiAdminQuestionBatchDraftVO draftVO = new AiAdminQuestionBatchDraftVO();
        draftVO.setAssistantMessage(String.format(
                "已围绕 %s 生成 %d 道同类型题目草稿。你可以继续补充“更偏基础 / 更偏场景 / 加入追问”等要求，再批量生成下一组。",
                normalizedTopic, questionDrafts.size()));
        draftVO.setQuestionDrafts(questionDrafts);
        draftVO.setSource("local");
        return draftVO;
    }

    private AiAdminQuestionDraftVO buildLocalAdminQuestionDraft(String topic, String currentTitle, String currentContent,
                                                                String currentAnswer, List<String> currentTags,
                                                                String summary) {
        String seed = firstNonBlank(topic, currentTitle, currentContent, currentAnswer);
        String normalizedTopic = getNormalizedTopic(seed);
        String title = StringUtils.defaultIfBlank(currentTitle, buildAdminQuestionTitle(normalizedTopic, null, seed));
        String content = StringUtils.defaultIfBlank(currentContent, buildAdminQuestionContent(normalizedTopic));
        List<String> draftTags = buildAdminQuestionTags(seed, normalizedTopic, content, currentTags, null);
        String answer = StringUtils.defaultIfBlank(currentAnswer,
                buildAdminQuestionStandardAnswer(normalizedTopic, content, draftTags));
        AiAdminQuestionDraftVO draftVO = new AiAdminQuestionDraftVO();
        draftVO.setTitle(title);
        draftVO.setContent(content);
        draftVO.setAnswer(answer);
        draftVO.setTags(draftTags.stream().distinct().limit(8).collect(Collectors.toList()));
        draftVO.setSummary(StringUtils.defaultIfBlank(summary,
                "已基于你输入的主题生成题目草稿和标准答案，建议管理员再补一版更贴近业务场景的案例和追问。"));
        draftVO.setSource("local");
        return draftVO;
    }

    @Override
    public AiAdminBankDraftVO generateAdminBankDraft(String topic, String currentTitle, String currentDescription) {
        String seed = firstNonBlank(topic, currentTitle, currentDescription);
        ThrowUtils.throwIf(StringUtils.isBlank(seed), ErrorCode.PARAMS_ERROR, "请先输入题库主题或已有题库信息");
        AiAdminBankDraftVO qwenDraft = buildAdminBankDraftByQwen(topic, currentTitle, currentDescription);
        if (qwenDraft != null) {
            return qwenDraft;
        }
        String normalizedTopic = getNormalizedTopic(seed);
        AiAdminBankDraftVO draftVO = new AiAdminBankDraftVO();
        draftVO.setTitle(StringUtils.defaultIfBlank(currentTitle,
                normalizedTopic + (normalizedTopic.contains("题库") ? "" : " 面试题库")));
        draftVO.setDescription(StringUtils.defaultIfBlank(currentDescription, String.format(
                "这个题库围绕 %s 展开，适合希望快速建立核心知识框架、补齐高频考点并完成阶段性复盘的同学。建议按照“基础概念 -> 典型场景 -> 风险取舍 -> 高频追问”的顺序刷题。",
                normalizedTopic)));
        draftVO.setPicture("");
        draftVO.setSuggestedQuestionTitles(Arrays.asList(
                normalizedTopic + " 的核心原理和适用场景是什么？",
                normalizedTopic + " 在高并发 / 高数据量场景下要注意哪些问题？",
                normalizedTopic + " 常见坑点和排查思路有哪些？",
                normalizedTopic + " 和相近方案相比有什么取舍？",
                normalizedTopic + " 如果你来落地，会如何设计整体方案？"
        ));
        draftVO.setOperationTips(Arrays.asList(
                "先确认题库目标人群，再决定题目难度分层。",
                "优先把题目按“基础、进阶、场景追问”三段组织，学习路径会更清晰。",
                "题库创建后建议补充 5 到 8 道代表题，再关联相似题。"
        ));
        draftVO.setSummary("已生成题库草案，建议管理员确认题库标题、目标人群和题目顺序后再发布。");
        draftVO.setSource("local");
        return draftVO;
    }

    @Override
    public void clearQuestionAiCache(long questionId) {
        if (questionId <= 0) {
            return;
        }
        questionAiContentService.remove(
                Wrappers.lambdaQuery(QuestionAiContent.class)
                        .eq(QuestionAiContent::getQuestionId, questionId)
        );
    }

    @Override
    public void clearQuestionAiData(long questionId) {
        if (questionId <= 0) {
            return;
        }
        clearQuestionAiCache(questionId);
        userQuestionRecordService.remove(
                Wrappers.lambdaQuery(UserQuestionRecord.class)
                        .eq(UserQuestionRecord::getQuestionId, questionId)
        );
    }

    private QuestionAiContent getOrCreateQuestionAiContent(long questionId) {
        QuestionAiContent questionAiContent = questionAiContentService.getOne(
                Wrappers.lambdaQuery(QuestionAiContent.class)
                        .eq(QuestionAiContent::getQuestionId, questionId)
                        .last("limit 1")
        );
        if (questionAiContent != null) {
            return questionAiContent;
        }
        Question question = questionService.getById(questionId);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        QuestionAiContent generatedContent = buildQuestionAiContent(question);
        boolean result = questionAiContentService.save(generatedContent);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存 AI 内容失败");
        return generatedContent;
    }

    private QuestionAiContent buildQuestionAiContent(Question question) {
        QuestionAiContent qwenContent = buildQuestionAiContentByQwen(question);
        if (qwenContent != null) {
            return qwenContent;
        }
        List<String> tags = parseJsonArray(question.getTags());
        String title = StringUtils.defaultIfBlank(question.getTitle(), "未命名题目");
        String focus = tags.isEmpty() ? "题意理解、结构化表达和场景分析" : String.join("、", tags);
        String contentSummary = getFirstMeaningfulLine(question.getContent(), "题干中的关键信息");
        String answerSummary = getFirstMeaningfulLine(question.getAnswer(), "推荐答案里的核心步骤");
        List<String> keyPoints = new ArrayList<>();
        if (!tags.isEmpty()) {
            keyPoints.addAll(tags.stream().limit(3).collect(Collectors.toList()));
        }
        if (keyPoints.isEmpty()) {
            keyPoints.addAll(Arrays.asList("题意理解", "核心思路", "场景分析"));
        } else if (keyPoints.size() < 3) {
            keyPoints.add("结构化表达");
        }
        List<String> hints = Arrays.asList(
                String.format("先用一句话说明这道题《%s》在考什么，再确认它和 %s 的关系。", title,
                        tags.isEmpty() ? "真实业务场景" : focus),
                String.format("回答时按“问题定义 -> 核心思路 -> 适用场景 -> 风险与取舍”四段来展开，并优先抓住：%s。", contentSummary),
                String.format("如果还是卡住，就把推荐答案拆成 2 到 3 个步骤，用自己的话复述，再补充边界情况：%s。", answerSummary)
        );
        List<String> pitfalls = Arrays.asList(
                "直接背答案，不说明为什么这么做。",
                "只回答主流程，忽略边界情况和失败场景。",
                String.format("没有结合 %s 说明方案的适用条件和取舍。", tags.isEmpty() ? "题干" : focus)
        );
        List<String> followUpQuestions = Arrays.asList(
                String.format("如果把《%s》的输入规模扩大 10 倍，你会怎么调整方案？", title),
                String.format("这道题里的 %s 在真实项目里通常会遇到什么坑？", tags.isEmpty() ? "核心思路" : tags.get(0)),
                "如果面试官继续追问复杂度、可维护性或扩展性，你会怎么回答？"
        );

        QuestionAiContent questionAiContent = new QuestionAiContent();
        questionAiContent.setQuestionId(question.getId());
        questionAiContent.setPlainExplanation(String.format(
                "这道题《%s》主要考察 %s。建议先明确题目要解决的问题，再围绕“核心思路、关键步骤、适用场景、风险取舍”组织回答。题干里最值得优先关注的是：%s；推荐答案中最值得先吃透的是：%s。",
                title, focus, contentSummary, answerSummary
        ));
        questionAiContent.setKeyPointsJson(JSONUtil.toJsonStr(keyPoints));
        questionAiContent.setThinkingHintsJson(JSONUtil.toJsonStr(hints));
        questionAiContent.setPitfallsJson(JSONUtil.toJsonStr(pitfalls));
        questionAiContent.setFollowUpQuestionsJson(JSONUtil.toJsonStr(followUpQuestions));
        questionAiContent.setModelName(MODEL_NAME);
        questionAiContent.setVersion(1);
        questionAiContent.setStatus(1);
        return questionAiContent;
    }

    private QuestionAiContent buildQuestionAiContentByQwen(Question question) {
        if (!qwenManager.isAvailable()) {
            return null;
        }
        String prompt = String.format(
                "你是程序员面试教练。请基于下面题目信息输出 JSON，对象字段必须严格为 plainExplanation,keyPoints,thinkingHints,pitfalls,followUpQuestions。"
                        + "其中 keyPoints、thinkingHints、pitfalls、followUpQuestions 都是字符串数组，thinkingHints 固定输出 3 条，且不要使用 markdown。"
                        + "题目标题：%s\n题目标签：%s\n题目内容：%s\n推荐答案：%s",
                StringUtils.defaultString(question.getTitle()),
                StringUtils.defaultString(question.getTags()),
                StringUtils.defaultString(question.getContent()),
                StringUtils.defaultString(question.getAnswer())
        );
        String content = qwenManager.chat("你需要输出合法 JSON，不能输出额外解释。", prompt);
        String json = qwenManager.extractJson(content);
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(json);
            QuestionAiContent questionAiContent = new QuestionAiContent();
            questionAiContent.setQuestionId(question.getId());
            questionAiContent.setPlainExplanation(jsonObject.getStr("plainExplanation"));
            questionAiContent.setKeyPointsJson(normalizeArrayJson(jsonObject.get("keyPoints")));
            questionAiContent.setThinkingHintsJson(normalizeArrayJson(jsonObject.get("thinkingHints")));
            questionAiContent.setPitfallsJson(normalizeArrayJson(jsonObject.get("pitfalls")));
            questionAiContent.setFollowUpQuestionsJson(normalizeArrayJson(jsonObject.get("followUpQuestions")));
            questionAiContent.setModelName("qwen");
            questionAiContent.setVersion(1);
            questionAiContent.setStatus(1);
            if (StringUtils.isBlank(questionAiContent.getPlainExplanation())) {
                return null;
            }
            return questionAiContent;
        } catch (Exception e) {
            return null;
        }
    }

    private AiAdminQuestionDraftVO buildAdminQuestionDraftByQwen(String topic, String currentTitle, String currentContent,
                                                                 String currentAnswer, List<String> currentTags) {
        if (!qwenManager.isAvailable()) {
            return null;
        }
        String prompt = String.format(
                "你是程序员刷题平台的内容运营助手。请输出 JSON，对象字段必须严格为 title,content,answer,tags,summary。"
                        + "其中 content 只能是题目背景、题目要求和提问点，不能直接写出结论、方案、答案、注意事项、总结。"
                        + "其中 tags 是 5 到 8 个字符串组成的数组，content 和 answer 使用 markdown。answer 必须是可直接保存的标准答案，"
                        + "要直接回答题目，不要写“回答思路”、“面试表达建议”、“可展开关键点”、“可以从以下几个方面回答”这类提纲式内容。"
                        + "answer 要尽量详细，至少覆盖：问题定义、核心方案、方案取舍、落地细节、常见风险、适合继续追问的点。"
                        + "title 必须简洁自然，像真实题库标题，不能直接照抄用户整段提示词。"
                        + "生成主题：%s\n当前标题：%s\n当前内容：%s\n当前答案：%s\n当前标签：%s",
                StringUtils.defaultString(topic),
                StringUtils.defaultString(currentTitle),
                StringUtils.defaultString(currentContent),
                StringUtils.defaultString(currentAnswer),
                currentTags == null ? "[]" : JSONUtil.toJsonStr(currentTags)
        );
        String content = qwenManager.chat("你只输出合法 JSON，不要附加任何解释。", prompt);
        String json = qwenManager.extractJson(content);
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(json);
            AiAdminQuestionDraftVO draftVO = new AiAdminQuestionDraftVO();
            draftVO.setTitle(jsonObject.getStr("title"));
            draftVO.setContent(jsonObject.getStr("content"));
            draftVO.setAnswer(jsonObject.getStr("answer"));
            draftVO.setTags(qwenManager.parseStringList(normalizeArrayJson(jsonObject.get("tags"))));
            draftVO.setSummary(jsonObject.getStr("summary"));
            draftVO.setSource("qwen");
            if (StringUtils.isAnyBlank(draftVO.getTitle(), draftVO.getContent(), draftVO.getAnswer())) {
                return null;
            }
            if (isAnswerStyleContent(draftVO.getContent())) {
                return null;
            }
            if (isGuidanceStyleAnswer(draftVO.getAnswer())) {
                return null;
            }
            return draftVO;
        } catch (Exception e) {
            return null;
        }
    }

    private AiAdminQuestionBatchDraftVO buildAdminQuestionBatchDraftByQwen(String prompt, List<String> history, int count) {
        if (!qwenManager.isAvailable()) {
            return null;
        }
        String historyText = CollUtil.isEmpty(history) ? "暂无历史对话" : String.join("\n", history);
        String batchPrompt = String.format(
                "你是程序员刷题平台的内容运营助手。请输出 JSON，对象字段必须严格为 assistantMessage,questionDrafts。"
                        + "其中 questionDrafts 必须是长度不超过 %d 的数组，每个元素字段必须严格为 title,content,answer,tags,summary。"
                        + "title 要区分开，避免同质化。content 只能是题目背景、题目要求和提问点，不能直接写出结论、方案、答案、注意事项、总结。"
                        + "answer 必须是可直接保存的标准答案，不能写“回答思路”、“面试表达建议”等提纲式内容。"
                        + "answer 要写得足够完整，至少覆盖问题本质、核心实现、适用场景、风险取舍和落地建议。"
                        + "tags 是 5 到 8 个字符串组成的数组，content 和 answer 使用 markdown。"
                        + "title 必须像真实题库标题，不能把管理员整段提示词原样塞进标题。"
                        + "历史对话：\n%s\n当前管理员要求：\n%s",
                count, historyText, prompt
        );
        String content = qwenManager.chat("你只输出合法 JSON，不要附加任何解释。", batchPrompt);
        String json = qwenManager.extractJson(content);
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(json);
            List<Object> rawDraftList = JSONUtil.toList(JSONUtil.parseArray(
                    normalizeArrayJson(jsonObject.get("questionDrafts"))), Object.class);
            List<AiAdminQuestionDraftVO> questionDrafts = new ArrayList<>();
            for (Object rawDraft : rawDraftList) {
                if (rawDraft == null) {
                    continue;
                }
                AiAdminQuestionDraftVO draftVO = buildAdminQuestionDraftFromJson(JSONUtil.parseObj(rawDraft), "qwen");
                if (draftVO != null) {
                    questionDrafts.add(draftVO);
                }
                if (questionDrafts.size() >= count) {
                    break;
                }
            }
            if (questionDrafts.isEmpty()) {
                return null;
            }
            AiAdminQuestionBatchDraftVO draftVO = new AiAdminQuestionBatchDraftVO();
            draftVO.setAssistantMessage(StringUtils.defaultIfBlank(jsonObject.getStr("assistantMessage"),
                    String.format("已按你的要求生成 %d 道题目草稿，可继续对话细化。", questionDrafts.size())));
            draftVO.setQuestionDrafts(questionDrafts);
            draftVO.setSource("qwen");
            return draftVO;
        } catch (Exception e) {
            return null;
        }
    }

    private AiAdminQuestionDraftVO buildAdminQuestionDraftFromJson(cn.hutool.json.JSONObject jsonObject, String source) {
        if (jsonObject == null) {
            return null;
        }
        AiAdminQuestionDraftVO draftVO = new AiAdminQuestionDraftVO();
        draftVO.setTitle(jsonObject.getStr("title"));
        draftVO.setContent(jsonObject.getStr("content"));
        draftVO.setAnswer(jsonObject.getStr("answer"));
        draftVO.setTags(qwenManager.parseStringList(normalizeArrayJson(jsonObject.get("tags"))));
        draftVO.setSummary(jsonObject.getStr("summary"));
        draftVO.setSource(source);
        if (StringUtils.isAnyBlank(draftVO.getTitle(), draftVO.getContent(), draftVO.getAnswer())) {
            return null;
        }
        if (isAnswerStyleContent(draftVO.getContent()) || isGuidanceStyleAnswer(draftVO.getAnswer())) {
            return null;
        }
        if (CollUtil.isEmpty(draftVO.getTags())) {
            draftVO.setTags(extractTagsFromText(
                    String.join(" ", draftVO.getTitle(), draftVO.getContent(), draftVO.getAnswer())));
        }
        if (CollUtil.isEmpty(draftVO.getTags())) {
            draftVO.setTags(Arrays.asList("面试题", "结构化表达", "场景分析"));
        } else {
            draftVO.setTags(draftVO.getTags().stream().filter(StringUtils::isNotBlank).distinct().limit(8)
                    .collect(Collectors.toList()));
        }
        return draftVO;
    }

    private List<AiAdminQuestionDraftVO> buildLocalAdminQuestionBatchDraft(String prompt, List<String> history, int count) {
        String context = buildBatchPromptContext(prompt, history);
        String normalizedTopic = getNormalizedTopic(context);
        List<String> baseTags = buildAdminQuestionTags(context, normalizedTopic, null, null, null);
        if (baseTags.isEmpty()) {
            baseTags = new ArrayList<>(Arrays.asList("面试题", "场景分析", "方案设计"));
        }
        List<String> focusList = buildBatchFocusList(context, count);
        List<AiAdminQuestionDraftVO> questionDrafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String focus = focusList.get(i % focusList.size());
            String title = buildAdminQuestionTitle(normalizedTopic, focus, context);
            String content = buildAdminQuestionContentByFocus(normalizedTopic, focus, context);
            List<String> tags = buildAdminQuestionTags(context, normalizedTopic, content, baseTags, focus);
            String summary = String.format("第 %d 题聚焦 %s，适合一组同类型题里做难度和考察角度补充。", i + 1, focus);
            questionDrafts.add(buildLocalAdminQuestionDraft(
                    normalizedTopic + " " + focus,
                    title,
                    content,
                    null,
                    tags,
                    summary));
        }
        return questionDrafts;
    }

    private String buildBatchPromptContext(String prompt, List<String> history) {
        List<String> parts = new ArrayList<>();
        if (CollUtil.isNotEmpty(history)) {
            parts.addAll(history);
        }
        parts.add(prompt);
        return parts.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining("\n"));
    }

    private List<String> buildBatchFocusList(String context, int count) {
        List<String> focusList = new ArrayList<>(Arrays.asList(
                "核心原理与适用场景",
                "高并发场景设计",
                "故障排查与优化",
                "方案对比与取舍",
                "落地实践与监控",
                "常见坑点与追问",
                "性能优化思路",
                "数据一致性设计",
                "系统扩展与演进",
                "业务案例分析"
        ));
        if (isCivilEngineeringContext(context)) {
            focusList = new ArrayList<>(Arrays.asList(
                    "施工组织与现场协调",
                    "质量控制与验收",
                    "安全管理与风险防范",
                    "进度计划与资源调配",
                    "成本控制与签证管理",
                    "多方协同与沟通处理"
            ));
        } else if (containsAnyIgnoreCase(context, "Redis")) {
            focusList = new ArrayList<>(Arrays.asList(
                    "核心原理与典型应用",
                    "缓存设计与热点治理",
                    "高并发访问控制",
                    "分布式锁与一致性",
                    "性能优化与故障排查",
                    "业务落地与方案取舍"
            ));
        } else if (containsAnyIgnoreCase(context, "基础", "入门", "初级")) {
            focusList = new ArrayList<>(Arrays.asList(
                    "基础概念理解",
                    "典型使用场景",
                    "核心流程说明",
                    "常见误区分析",
                    "基础追问延伸"
            ));
        } else if (containsAnyIgnoreCase(context, "高级", "进阶", "资深", "架构")) {
            focusList = new ArrayList<>(Arrays.asList(
                    "架构设计取舍",
                    "复杂场景落地",
                    "高并发与高可用",
                    "稳定性与容灾",
                    "性能与成本平衡",
                    "跨系统协同设计"
            ));
        }
        return focusList.stream().limit(Math.max(1, count)).collect(Collectors.toList());
    }

    private String buildAdminQuestionTitle(String topic, String focus, String context) {
        String safeTopic = StringUtils.defaultIfBlank(topic, "相关主题");
        if (containsAnyIgnoreCase(safeTopic, "Redis")) {
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "缓存", "热点")) {
                return "Redis 热点数据场景下如何设计稳定的缓存方案？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "并发", "访问控制")) {
                return "高并发场景下，Redis 如何避免请求同时击穿后端？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "锁", "一致性")) {
                return "用 Redis 做分布式锁时，哪些细节最容易出问题？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "排查", "优化")) {
                return "Redis 性能突然下降时，你会按什么顺序排查？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "取舍", "落地")) {
                return "如果让你在业务里落地 Redis 方案，你会如何做技术取舍？";
            }
            return "Redis 在高并发业务里通常承担哪些关键职责？";
        }
        if (isCivilEngineeringContext(safeTopic)) {
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "质量")) {
                return "主体结构施工阶段，如何把质量问题控制在验收前？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "安全")) {
                return "高处作业和交叉施工并行时，现场安全管理应如何落地？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "进度")) {
                return "工期紧、工序多的项目里，施工进度应该如何统筹？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "成本")) {
                return "土木项目施工过程中，怎样兼顾成本控制和现场执行？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "协调", "沟通")) {
                return "多工种交叉作业的土木项目中，项目负责人如何做好现场协调？";
            }
            return "土木工程项目推进过程中，现场管理最容易忽视哪些关键点？";
        }
        if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "原理", "基础", "概念")) {
            return safeTopic + " 在实际工作中主要解决什么问题？";
        }
        if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "排查", "优化")) {
            return "如果 " + safeTopic + " 方案上线后出现问题，你会如何排查并优化？";
        }
        if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "取舍", "对比")) {
            return safeTopic + " 和常见替代方案相比，该如何做技术取舍？";
        }
        if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "落地", "实践")) {
            return "如果让你负责 " + safeTopic + " 落地，你会如何推进整体方案？";
        }
        return "请结合实际场景谈谈 " + safeTopic + " 的设计思路。";
    }

    private String buildAdminQuestionContentByFocus(String topic, String focus, String context) {
        if (containsAnyIgnoreCase(topic, "Redis")) {
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "缓存", "热点")) {
                return "你的系统首页存在明显热点数据，访问量在短时间内快速上涨。为了保证接口稳定，"
                        + "团队决定继续使用 Redis 做缓存层，但线上已经出现过热点 key 失效后数据库压力飙升的问题。\n\n"
                        + "请回答：\n"
                        + "1. 这类场景最容易出现哪些缓存风险？\n"
                        + "2. 你会如何设计缓存更新、过期和重建策略？\n"
                        + "3. 如果要兼顾一致性、性能和实现复杂度，你会如何取舍？\n"
                        + "4. 线上应该重点监控哪些指标来验证方案是否有效？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "并发", "访问控制")) {
                return "某个抢购接口在高峰期会同时打到 Redis 和下游服务，团队担心在瞬时并发拉高时出现重复执行、热点穿透或下游雪崩。\n\n"
                        + "请回答：\n"
                        + "1. 这类高并发场景里 Redis 通常承担哪些职责？\n"
                        + "2. 你会如何设计限流、幂等、削峰或热点保护机制？\n"
                        + "3. 如果业务允许短暂最终一致，你会怎么优化系统稳定性？\n"
                        + "4. 方案上线后你会怎样评估瓶颈是否真的被缓解？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "锁", "一致性")) {
                return "团队准备使用 Redis 分布式锁保护关键业务流程，例如库存扣减、任务调度或定时补偿。"
                        + "但大家对“只要加锁就安全”这件事并不放心。\n\n"
                        + "请回答：\n"
                        + "1. 使用 Redis 分布式锁时最关键的正确性要求是什么？\n"
                        + "2. 加锁、续期、解锁分别要注意哪些细节？\n"
                        + "3. 如果遇到主从切换、超时或锁误删，应该如何兜底？\n"
                        + "4. 在什么情况下你会放弃 Redis 锁，改用别的方案？";
            }
        }
        if (isCivilEngineeringContext(topic)) {
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "质量")) {
                return "一个土木工程项目进入主体结构施工阶段，现场进度压力较大，但监理已经提示存在混凝土浇筑质量、钢筋绑扎细节和隐蔽工程验收衔接不顺的问题。\n\n"
                        + "请回答：\n"
                        + "1. 你会优先从哪些环节建立质量控制点？\n"
                        + "2. 施工单位、监理和项目部之间如何形成有效闭环？\n"
                        + "3. 如果进度压力和质量要求发生冲突，你会怎么处理？\n"
                        + "4. 哪些资料和现场动作最能体现质量管理做到了位？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "安全")) {
                return "项目现场存在高处作业、临边防护、模板支撑和多工种交叉施工等风险点。近期工期紧张，部分班组有赶工倾向。\n\n"
                        + "请回答：\n"
                        + "1. 你会如何识别并分级现场主要安全风险？\n"
                        + "2. 日常安全交底、检查和整改闭环该怎么组织？\n"
                        + "3. 如果现场负责人和劳务班组在安全措施上产生分歧，你会如何推进执行？\n"
                        + "4. 发生险情苗头时，项目管理层应怎样快速响应？";
            }
            if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "进度", "协调", "沟通")) {
                return "项目存在多个专业和班组交叉施工，甲方要求节点不能延误，但现场反馈劳动力、材料到场和工序衔接都不够稳定。\n\n"
                        + "请回答：\n"
                        + "1. 你会怎样拆解施工进度计划，避免关键节点失控？\n"
                        + "2. 资源调配和现场协调应该如何落到每天的执行层？\n"
                        + "3. 当计划和实际偏差越来越大时，你会优先处理什么？\n"
                        + "4. 怎样和甲方、监理、分包沟通，既说明问题又推动解决？";
            }
            return "一个土木工程项目进入施工高峰期，现场同时面临进度、质量、安全和成本多线压力。项目负责人需要在复杂约束下推进落地。\n\n"
                    + "请回答：\n"
                    + "1. 你会如何梳理当前阶段最核心的管理目标？\n"
                    + "2. 现场管理中最容易被忽略但后果较重的问题有哪些？\n"
                    + "3. 如果你来统筹项目推进，会先抓哪些关键动作？\n"
                    + "4. 如何通过制度、检查和沟通，让方案真正落实到现场？";
        }
        return buildAdminQuestionContent(topic)
                + "\n\n补充要求：请候选人重点从“" + focus + "”这个角度展开，并尽量结合真实业务场景说明。";
    }

    private List<String> buildAdminQuestionTags(String seed, String topic, String content, List<String> currentTags,
                                                String focus) {
        List<String> tags = new ArrayList<>();
        if (CollUtil.isNotEmpty(currentTags)) {
            tags.addAll(currentTags.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        }
        tags.addAll(extractTagsFromText(String.join(" ", StringUtils.defaultString(seed), StringUtils.defaultString(topic),
                StringUtils.defaultString(content), StringUtils.defaultString(focus))));
        if (containsAnyIgnoreCase(topic, "Redis")) {
            addTags(tags, "Redis", "缓存", "高并发", "分布式锁", "架构设计");
            if (containsAnyIgnoreCase(seed, "热点", "缓存")) {
                addTags(tags, "热点数据", "缓存治理");
            }
        }
        if (containsAnyIgnoreCase(topic, "MySQL")) {
            addTags(tags, "MySQL", "索引优化", "SQL 调优", "数据库");
        }
        if (isCivilEngineeringContext(topic)) {
            addTags(tags, "土木工程", "施工管理", "项目管理", "现场管理");
            if (containsAnyIgnoreCase(seed, "质量")) {
                addTags(tags, "质量控制", "工程验收");
            }
            if (containsAnyIgnoreCase(seed, "安全")) {
                addTags(tags, "安全管理", "风险防范");
            }
            if (containsAnyIgnoreCase(seed, "进度", "工期")) {
                addTags(tags, "进度管理", "资源调配");
            }
            if (containsAnyIgnoreCase(seed, "成本", "造价")) {
                addTags(tags, "成本控制", "造价管理");
            }
        }
        if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "取舍", "对比")) {
            addTags(tags, "方案设计", "技术取舍");
        }
        if (containsAnyIgnoreCase(StringUtils.defaultString(focus), "排查", "优化")) {
            addTags(tags, "故障排查", "性能优化");
        }
        if (tags.isEmpty()) {
            addTags(tags, "面试题", "场景分析", "结构化表达");
        }
        return tags.stream().filter(StringUtils::isNotBlank).distinct().limit(8).collect(Collectors.toList());
    }

    private void addTags(List<String> tags, String... candidates) {
        if (tags == null || candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            if (StringUtils.isNotBlank(candidate) && !tags.contains(candidate)) {
                tags.add(candidate);
            }
        }
    }

    private AiAdminBankDraftVO buildAdminBankDraftByQwen(String topic, String currentTitle, String currentDescription) {
        if (!qwenManager.isAvailable()) {
            return null;
        }
        String prompt = String.format(
                "你是程序员刷题平台的题库运营助手。请输出 JSON，对象字段必须严格为 title,description,picture,suggestedQuestionTitles,operationTips,summary。"
                        + "其中 suggestedQuestionTitles 和 operationTips 都是字符串数组，description 要适合直接作为题库简介。"
                        + "生成主题：%s\n当前标题：%s\n当前描述：%s",
                StringUtils.defaultString(topic),
                StringUtils.defaultString(currentTitle),
                StringUtils.defaultString(currentDescription)
        );
        String content = qwenManager.chat("你只输出合法 JSON，不要附加任何解释。", prompt);
        String json = qwenManager.extractJson(content);
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(json);
            AiAdminBankDraftVO draftVO = new AiAdminBankDraftVO();
            draftVO.setTitle(jsonObject.getStr("title"));
            draftVO.setDescription(jsonObject.getStr("description"));
            draftVO.setPicture(jsonObject.getStr("picture"));
            draftVO.setSuggestedQuestionTitles(qwenManager.parseStringList(
                    normalizeArrayJson(jsonObject.get("suggestedQuestionTitles"))));
            draftVO.setOperationTips(qwenManager.parseStringList(
                    normalizeArrayJson(jsonObject.get("operationTips"))));
            draftVO.setSummary(jsonObject.getStr("summary"));
            draftVO.setSource("qwen");
            if (StringUtils.isAnyBlank(draftVO.getTitle(), draftVO.getDescription())) {
                return null;
            }
            return draftVO;
        } catch (Exception e) {
            return null;
        }
    }

    private UserAiReportVO buildUserReport(List<UserQuestionRecord> userQuestionRecordList, String reportType,
                                           LocalDate startDate, LocalDate endDate) {
        UserAiReportVO userAiReportVO = new UserAiReportVO();
        userAiReportVO.setReportType(reportType);
        userAiReportVO.setStartDate(startDate.toString());
        userAiReportVO.setEndDate(endDate.toString());
        userAiReportVO.setSource("generated");
        long totalRecords = userQuestionRecordList.size();
        long aiUsageCount = userQuestionRecordList.stream()
                .filter(record -> Integer.valueOf(1).equals(record.getUsedAi()))
                .count();
        userAiReportVO.setTotalRecords(totalRecords);
        userAiReportVO.setAiUsageCount(aiUsageCount);
        if (userQuestionRecordList.isEmpty()) {
            userAiReportVO.setSummary("这段时间还没有刷题记录，建议先从一个小题库开始，连续刷 3 到 5 天，再看 AI 学习报告会更有价值。");
            userAiReportVO.setWeakPoints(Arrays.asList("刷题习惯尚未建立", "缺少连续复盘"));
            userAiReportVO.setRecommendations(Arrays.asList(
                    "先选一个题库，连续完成 5 道题。",
                    "每道题至少点一次“帮我理解”，形成自己的笔记。",
                    "做完一轮后再来看学习报告，重点会更明确。"
            ));
            return userAiReportVO;
        }

        Set<Long> questionIdSet = userQuestionRecordList.stream()
                .map(UserQuestionRecord::getQuestionId)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionService.listByIds(questionIdSet).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        Map<String, Integer> tagCounter = new LinkedHashMap<>();
        Map<String, Integer> weakTagCounter = new LinkedHashMap<>();
        for (UserQuestionRecord record : userQuestionRecordList) {
            Question question = questionMap.get(record.getQuestionId());
            List<String> tags = question == null ? Collections.emptyList() : parseJsonArray(question.getTags());
            for (String tag : tags) {
                tagCounter.put(tag, tagCounter.getOrDefault(tag, 0) + 1);
                if (Integer.valueOf(1).equals(record.getUsedAi())
                        || QuestionRecordActionConstant.INCORRECT.equals(record.getActionType())
                        || QuestionRecordActionConstant.USE_AI_HINT.equals(record.getActionType())) {
                    weakTagCounter.put(tag, weakTagCounter.getOrDefault(tag, 0) + 1);
                }
            }
        }

        List<String> weakPoints = topKeys(weakTagCounter.isEmpty() ? tagCounter : weakTagCounter, 3);
        if (weakPoints.isEmpty()) {
            weakPoints = Arrays.asList("结构化表达", "复盘总结");
        }
        List<String> hotTags = topKeys(tagCounter, 3);
        userAiReportVO.setSummary(String.format(
                "最近 %s 天你一共产生了 %s 条刷题行为，其中 %s 次借助了 AI。当前最集中的题目方向是 %s；但 %s 相关题目上更依赖提示，建议优先补强。",
                totalDays(startDate, endDate),
                totalRecords,
                aiUsageCount,
                hotTags.isEmpty() ? "通用基础题" : String.join("、", hotTags),
                String.join("、", weakPoints)
        ));
        List<String> recommendations = new ArrayList<>();
        recommendations.add(String.format("优先复习 %s 相关题目，至少再做 3 道，并用自己的话复述标准答案。", weakPoints.get(0)));
        if (weakPoints.size() > 1) {
            recommendations.add(String.format("把 %s 和 %s 两类题目放在同一天对比刷，重点总结它们的差异和取舍。", weakPoints.get(0), weakPoints.get(1)));
        } else {
            recommendations.add("做题时固定按“题意 -> 思路 -> 场景 -> 风险”四段来回答，减少对提示的依赖。");
        }
        recommendations.add(aiUsageCount > totalRecords / 2
                ? "这段时间对 AI 的依赖偏高，下一轮刷题先自己想 2 分钟，再打开提示。"
                : "可以继续保留现在的节奏，每做完 5 道题再集中复盘一次。");
        userAiReportVO.setWeakPoints(weakPoints);
        userAiReportVO.setRecommendations(recommendations);
        return userAiReportVO;
    }

    private void upsertUserReport(long userId, UserAiReportVO reportVO) {
        UserAiReport userAiReport = userAiReportService.getOne(
                Wrappers.lambdaQuery(UserAiReport.class)
                        .eq(UserAiReport::getUserId, userId)
                        .eq(UserAiReport::getReportType, reportVO.getReportType())
                        .eq(UserAiReport::getStartDate, java.sql.Date.valueOf(reportVO.getStartDate()))
                        .eq(UserAiReport::getEndDate, java.sql.Date.valueOf(reportVO.getEndDate()))
                        .last("limit 1")
        );
        if (userAiReport == null) {
            userAiReport = new UserAiReport();
            userAiReport.setUserId(userId);
            userAiReport.setReportType(reportVO.getReportType());
        }
        userAiReport.setStartDate(java.sql.Date.valueOf(reportVO.getStartDate()));
        userAiReport.setEndDate(java.sql.Date.valueOf(reportVO.getEndDate()));
        userAiReport.setSummary(reportVO.getSummary());
        userAiReport.setWeakPointsJson(JSONUtil.toJsonStr(reportVO.getWeakPoints()));
        userAiReport.setRecommendationsJson(JSONUtil.toJsonStr(reportVO.getRecommendations()));
        userAiReport.setModelName(MODEL_NAME);
        userAiReport.setStatus(1);
        userAiReportService.saveOrUpdate(userAiReport);
    }

    private UserAiReportVO buildReportFromCache(UserAiReport userAiReport, long userId, Date startTime, Date endTime) {
        UserAiReportVO userAiReportVO = new UserAiReportVO();
        userAiReportVO.setReportType(userAiReport.getReportType());
        userAiReportVO.setStartDate(userAiReport.getStartDate().toString());
        userAiReportVO.setEndDate(userAiReport.getEndDate().toString());
        userAiReportVO.setSummary(userAiReport.getSummary());
        userAiReportVO.setWeakPoints(parseJsonArray(userAiReport.getWeakPointsJson()));
        userAiReportVO.setRecommendations(parseJsonArray(userAiReport.getRecommendationsJson()));
        userAiReportVO.setSource("cache");
        long totalRecords = userQuestionRecordService.count(
                Wrappers.lambdaQuery(UserQuestionRecord.class)
                        .eq(UserQuestionRecord::getUserId, userId)
                        .between(UserQuestionRecord::getCreateTime, startTime, endTime)
        );
        long aiUsageCount = userQuestionRecordService.count(
                Wrappers.lambdaQuery(UserQuestionRecord.class)
                        .eq(UserQuestionRecord::getUserId, userId)
                        .eq(UserQuestionRecord::getUsedAi, 1)
                        .between(UserQuestionRecord::getCreateTime, startTime, endTime)
        );
        userAiReportVO.setTotalRecords(totalRecords);
        userAiReportVO.setAiUsageCount(aiUsageCount);
        return userAiReportVO;
    }

    private String normalizeReportType(String reportType) {
        return "month".equalsIgnoreCase(reportType) ? "month" : "week";
    }

    private QueryIntent buildSearchIntent(String query) {
        QueryIntent localIntent = buildLocalSearchIntent(query);
        if (!qwenManager.isAvailable()) {
            return localIntent;
        }
        String prompt = String.format(
                "你是程序员刷题平台的搜索助手。用户搜索：%s。请输出 JSON，对象字段必须为 rewrittenQuery,tags,reason。tags 为字符串数组。rewrittenQuery 用于题库搜索，尽量精简成关键词，不要超过 20 个字。",
                query
        );
        String content = qwenManager.chat("你只输出合法 JSON，不要附加解释。", prompt);
        String json = qwenManager.extractJson(content);
        if (StringUtils.isBlank(json)) {
            return localIntent;
        }
        try {
            cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(json);
            QueryIntent queryIntent = new QueryIntent();
            queryIntent.setRewrittenQuery(StringUtils.defaultIfBlank(jsonObject.getStr("rewrittenQuery"), localIntent.getRewrittenQuery()));
            queryIntent.setReason(StringUtils.defaultIfBlank(jsonObject.getStr("reason"), localIntent.getReason()));
            queryIntent.setTags(qwenManager.parseStringList(normalizeArrayJson(jsonObject.get("tags"))));
            queryIntent.setSource("qwen");
            return queryIntent;
        } catch (Exception e) {
            return localIntent;
        }
    }

    private QueryIntent buildLocalSearchIntent(String query) {
        QueryIntent queryIntent = new QueryIntent();
        queryIntent.setRewrittenQuery(query.trim());
        queryIntent.setReason("基于你输入的自然语言描述，系统先做关键词抽取，再结合标签进行本地搜索。");
        queryIntent.setTags(extractTagsFromQuery(query));
        queryIntent.setSource("local");
        return queryIntent;
    }

    private List<QuestionVO> searchQuestionsByIntent(QueryIntent queryIntent, int pageSize) {
        QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
        questionQueryRequest.setCurrent(1);
        questionQueryRequest.setPageSize(pageSize);
        questionQueryRequest.setSearchText(queryIntent.getRewrittenQuery());
        if (CollUtil.isNotEmpty(queryIntent.getTags())) {
            questionQueryRequest.setTags(queryIntent.getTags());
        }
        questionQueryRequest.setSortField("createTime");
        questionQueryRequest.setSortOrder("descend");
        return questionService.getQuestionVOPage(questionService.listQuestionByPage(questionQueryRequest), null)
                .getRecords();
    }

    private QuestionQueryRequest buildLatestQuestionRequest(int pageSize) {
        QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
        questionQueryRequest.setCurrent(1);
        questionQueryRequest.setPageSize(pageSize);
        questionQueryRequest.setSortField("createTime");
        questionQueryRequest.setSortOrder("descend");
        return questionQueryRequest;
    }

    private List<String> extractTagsFromQuery(String query) {
        if (StringUtils.isBlank(query)) {
            return new ArrayList<>();
        }
        List<Question> questionList = questionService.list(
                Wrappers.lambdaQuery(Question.class)
                        .select(Question::getTags)
                        .last("limit 200")
        );
        List<String> allTags = questionList.stream()
                .flatMap(question -> parseJsonArray(question.getTags()).stream())
                .distinct()
                .collect(Collectors.toList());
        return allTags.stream()
                .filter(tag -> query.toLowerCase().contains(tag.toLowerCase()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<String> extractTagsFromText(String text) {
        List<String> tags = extractTagsFromQuery(text);
        if (StringUtils.isBlank(text)) {
            return tags;
        }
        List<String> keywordTags = Arrays.asList(
                "Java", "MySQL", "Redis", "Spring", "并发", "索引", "缓存", "消息队列", "微服务", "算法", "网络", "操作系统",
                "土木工程", "施工管理", "现场管理", "项目管理", "质量控制", "安全管理", "进度管理", "造价管理"
        );
        for (String keywordTag : keywordTags) {
            if (text.toLowerCase().contains(keywordTag.toLowerCase()) && !tags.contains(keywordTag)) {
                tags.add(keywordTag);
            }
        }
        if (isCivilEngineeringContext(text)) {
            addTags(tags, "土木工程", "施工管理", "项目管理");
        }
        if (containsAnyIgnoreCase(text, "Redis")) {
            addTags(tags, "Redis");
        }
        return tags.stream().distinct().limit(8).collect(Collectors.toList());
    }

    private String buildAdminQuestionStandardAnswer(String topic, String content, List<String> tags) {
        String context = StringUtils.defaultString(topic) + "\n" + StringUtils.defaultString(content);
        if (containsAnyIgnoreCase(context, "缓存击穿", "hot key", "热点 key")) {
            return "### 标准答案\n"
                    + "Redis 缓存击穿指某个热点 key 在失效的瞬间，大量并发请求同时访问这个 key，导致缓存未命中并一起回源数据库，瞬时把数据库压垮。它和缓存穿透、缓存雪崩的区别在于：击穿强调的是**热点数据**失效；穿透强调的是**查不存在的数据**；雪崩强调的是**大量 key 同时失效或缓存集群整体不可用**。\n\n"
                    + "### 为什么会出问题\n"
                    + "热点 key 本来承受的流量就很高，一旦缓存失效，所有请求都会短时间落到数据库。如果数据库本身没有为这种尖峰做隔离和削峰，就可能出现连接耗尽、慢查询增多、线程池阻塞，最后把上游接口一并拖慢。\n\n"
                    + "### 核心解决方案\n"
                    + "1. **互斥锁 / 分布式锁重建缓存**：发现缓存失效后，只允许一个线程回源数据库并重建缓存，其他线程短暂等待或返回旧值。\n"
                    + "2. **逻辑过期**：缓存中的数据不立即删除，而是加一个逻辑过期时间。即使过期了，也先返回旧值，再异步重建缓存。\n"
                    + "3. **热点数据不过期**：对于极热点 key，可以通过主动刷新或后台预热避免它自然过期。\n\n"
                    + "### 方案分析\n"
                    + "- 互斥锁方案实现简单，但要注意锁超时、锁误删和高并发下等待时间过长的问题。\n"
                    + "- 逻辑过期方案用户体验更稳定，但读到的可能是短时间旧数据，适合允许最终一致性的业务。\n"
                    + "- 热点数据不过期适合固定热点场景，但需要额外的监控和刷新机制。\n\n"
                    + "### 如果要落地，我会怎么设计\n"
                    + "第一步先识别哪些 key 真正是热点数据，对这类 key 单独配置更长 TTL、预热机制或逻辑过期。\n"
                    + "第二步在缓存重建路径上增加互斥控制，并限制等待时间，避免大量线程一直阻塞。\n"
                    + "第三步给数据库和下游服务再加限流、熔断或降级兜底，保证即使缓存策略失效，系统也不会立刻被打穿。\n"
                    + "第四步补监控，重点看热点 key 命中率、数据库 QPS、缓存重建次数、接口 RT 和异常比例。\n\n"
                    + "### 常见坑点\n"
                    + "- 没有双重检查缓存，导致多个线程重复回源。\n"
                    + "- 锁的过期时间过短或过长，分别会导致重复构建或锁阻塞。\n"
                    + "- 数据更新后没有及时删除或刷新缓存，导致脏数据。\n\n"
                    + "### 总结\n"
                    + "面试里如果问缓存击穿，核心回答就是：**热点 key 失效导致大量请求同时回源数据库**，常见解法是 **互斥锁、逻辑过期、热点数据预热**，再补充它们各自的适用场景和取舍。";
        }
        if (containsAnyIgnoreCase(context, "缓存穿透")) {
            return "### 标准答案\n"
                    + "缓存穿透指请求的数据既不在缓存，也不在数据库中，导致每次请求都会穿过缓存直接打到数据库。攻击者如果持续请求不存在的数据，或者业务本身对非法参数缺少校验，就会让数据库长期承受无意义请求。\n\n"
                    + "### 问题本质\n"
                    + "它的本质不是缓存失效，而是**缓存层根本没有机会挡住请求**。如果系统对不存在的数据没有兜底策略，缓存的保护能力会被绕开。\n\n"
                    + "### 核心解决方案\n"
                    + "1. **缓存空对象**：数据库查不到时也把空结果缓存一段时间，避免重复回源。\n"
                    + "2. **布隆过滤器**：先判断 key 是否可能存在，不存在就直接拦截。\n"
                    + "3. **参数校验与限流**：对明显非法的请求参数、异常频率请求做前置拦截。\n\n"
                    + "### 方案分析\n"
                    + "- 空对象缓存实现简单，但要控制过期时间，避免影响正常数据新增后的可见性。\n"
                    + "- 布隆过滤器适合大规模 key 集合场景，但有误判率，不能保证 100% 准确。\n"
                    + "- 参数校验和限流适合作为外围防护，通常和前两者组合使用。\n\n"
                    + "### 业务落地建议\n"
                    + "如果是普通业务系统，我通常会把参数校验放在最前面，先把明显无效请求挡掉；接着对数据库查不到的结果做短期空值缓存；如果 key 规模很大、请求量又高，再引入布隆过滤器做第二道前置过滤。\n"
                    + "如果是对抗恶意流量的高风险接口，还会结合 IP 限流、用户维度限流、风控策略和日志告警一起使用，避免单一手段被轻易绕过。\n\n"
                    + "### 常见误区\n"
                    + "- 只加空对象缓存，但缓存时间过长，导致正常新数据迟迟不可见。\n"
                    + "- 以为布隆过滤器能百分百判断存在性，忽略了误判率。\n"
                    + "- 没有把非法请求在网关或应用入口拦住，导致无效请求依旧大量打进服务内部。\n\n"
                    + "### 总结\n"
                    + "缓存穿透的关键词是：**查的是不存在的数据**。标准回答一般是 **空对象缓存 + 布隆过滤器 + 参数校验/限流**。";
        }
        if (containsAnyIgnoreCase(context, "缓存雪崩")) {
            return "### 标准答案\n"
                    + "缓存雪崩指大量缓存 key 在同一时间集中失效，或者缓存服务整体不可用，导致海量请求同时回源数据库，最终把后端服务拖垮。它往往不是单个热点问题，而是**系统层面的保护层失效**。\n\n"
                    + "### 典型诱因\n"
                    + "最常见的诱因包括：大量 key 使用了相同 TTL；大促前没有做缓存预热；Redis 单点或集群异常；业务上线后热点数据突然放大但缓存策略没有及时调整。\n\n"
                    + "### 核心解决方案\n"
                    + "1. **给过期时间增加随机值**，避免大量 key 同时失效。\n"
                    + "2. **热点数据预热**，在大促或高峰前提前加载热点缓存。\n"
                    + "3. **多级缓存 + 限流降级**，即使 Redis 异常，也不要让所有请求直冲数据库。\n"
                    + "4. **高可用部署**，通过 Redis 主从、哨兵或集群降低单点故障风险。\n\n"
                    + "### 设计思路\n"
                    + "真正防雪崩不能只靠一个技巧，而是要从“过期打散、容量规划、高可用、服务降级”四层一起做。"
                    + "也就是说，你既要减少缓存同一时刻失效的概率，也要让系统在缓存异常时还能以可控方式运行。\n\n"
                    + "### 落地建议\n"
                    + "- 给不同业务 key 设置分层 TTL，并加随机扰动。\n"
                    + "- 对大促、活动、首页等热点数据提前预热，并持续刷新。\n"
                    + "- 应用层准备限流、熔断、默认兜底数据或静态化页面，避免流量全部打到数据库。\n"
                    + "- Redis 层做好哨兵或集群部署，同时监控命中率、内存、慢日志、主从延迟和异常切换情况。\n\n"
                    + "### 总结\n"
                    + "雪崩关注的是**大量 key 同时失效或缓存整体故障**，回答时要同时覆盖“过期打散、预热、限流降级、高可用”四类手段。";
        }
        if (containsAnyIgnoreCase(context, "索引失效", "mysql 索引")) {
            return "### 标准答案\n"
                    + "MySQL 索引失效通常指查询本来可以利用索引，但因为 SQL 写法、联合索引设计或数据分布问题，优化器最终没有使用索引，导致全表扫描或扫描行数明显增加。\n\n"
                    + "### 本质理解\n"
                    + "面试里不要只背“哪些写法会失效”，更重要的是说明：**索引失效的本质是优化器判断走索引不划算，或者 SQL 写法让索引根本用不上**。只要把这个底层逻辑讲清楚，回答就会更像真正做过排查的人。\n\n"
                    + "### 常见原因\n"
                    + "1. 对索引列做函数计算、表达式运算或隐式类型转换。\n"
                    + "2. 使用前导模糊查询，例如 `like '%abc'`。\n"
                    + "3. 违反最左前缀原则。\n"
                    + "4. 选择性太差，优化器认为走索引收益不高。\n"
                    + "5. 使用 `or`、范围查询后，后续联合索引列无法继续高效命中。\n\n"
                    + "### 排查方法\n"
                    + "- 先看 `explain`，确认是否用了索引、走的是哪个索引、扫描行数是多少。\n"
                    + "- 再看 SQL 是否存在函数、类型不一致、模糊匹配或联合索引顺序不合理。\n"
                    + "- 最后结合数据量和选择性判断是否需要调整索引设计。\n\n"
                    + "### 如果我来优化\n"
                    + "我一般会先从最小代价开始处理：先改 SQL 写法，保证条件命中联合索引顺序，避免不必要的函数和类型转换；如果还不够，再评估是否需要补充覆盖索引或重新设计联合索引；最后再结合业务场景确认分页策略、排序字段和历史数据归档是否要一起调整。\n\n"
                    + "### 容易被忽略的点\n"
                    + "- 索引建了不代表一定走，数据分布变化后优化器选择可能会变。\n"
                    + "- 联合索引不仅看有没有建，还要看查询条件、范围条件和排序字段的组合。\n"
                    + "- 只盯着索引本身不够，还要关注回表成本、扫描行数和 SQL 是否真的符合业务需求。\n\n"
                    + "### 总结\n"
                    + "面试回答时要先说“索引失效的本质是优化器没有选择索引”，然后给出典型场景、`explain` 排查思路和优化方式。";
        }
        if (containsAnyIgnoreCase(context, "并发", "锁", "redis")) {
            return "### 标准答案\n"
                    + "如果题目围绕 Redis 并发控制，核心目标通常是避免并发写冲突、重复执行、热点请求放大或者下游系统被同时打穿。常见做法是利用 Redis 做分布式锁、原子计数、幂等控制和限流削峰。\n\n"
                    + "### 先讲清楚目标\n"
                    + "这里的重点不是“会不会用 Redis 命令”，而是你能不能说清楚：为什么这个场景需要并发控制、需要保护哪个关键资源、如果不做控制会出什么问题。例如库存超卖、任务重复执行、下游接口被打爆，都是不同的控制目标。\n\n"
                    + "### 常见方案\n"
                    + "1. **分布式锁**：通过 `SET key value NX EX` 抢锁，保证同一时刻只有一个线程处理关键逻辑。\n"
                    + "2. **Lua 脚本保证原子性**：把“判断 + 修改”放在同一个脚本里执行，避免并发条件竞争。\n"
                    + "3. **幂等控制**：把请求唯一标识写入 Redis，处理过的请求直接拦截。\n"
                    + "4. **限流与削峰**：利用计数器、令牌桶或消息队列减少瞬时并发冲击。\n\n"
                    + "### 方案该怎么选\n"
                    + "如果问题是“同一资源只能被一个线程修改”，优先考虑分布式锁；如果问题是“多步操作要么一起成功要么一起失败”，优先考虑 Lua 脚本或原子命令；如果问题是“请求不能重复执行”，优先考虑幂等控制；如果问题是“流量太大”，则要考虑限流、排队和削峰，而不是只想着加锁。\n\n"
                    + "### 使用 Redis 锁时的注意点\n"
                    + "- 锁要带唯一 value，释放时要校验 value，避免误删别人的锁。\n"
                    + "- 要设置过期时间，防止业务异常导致死锁。\n"
                    + "- 如果是主从切换等高可用场景，还要考虑锁一致性问题。\n\n"
                    + "### 如果业务要落地\n"
                    + "我会先确认关键资源和失败代价，再选控制手段。对真正高价值、不能出错的流程，不会只靠 Redis 锁单点兜底，而会再结合数据库约束、消息补偿、幂等记录和监控告警一起设计，这样系统在异常场景下也更稳。\n\n"
                    + "### 总结\n"
                    + "Redis 并发控制并不是只有“加锁”这一种思路，回答时最好把 **分布式锁、原子操作、幂等控制、限流削峰** 组合起来讲。";
        }
        if (isCivilEngineeringContext(context)) {
            return "### 标准答案\n"
                    + "这类题目的核心不是单独背某个概念，而是看你能不能站在项目推进者的视角，把现场目标、执行动作、责任分工和风险控制讲完整。\n\n"
                    + "### 先明确管理目标\n"
                    + "土木项目现场往往不是单一目标，而是进度、质量、安全、成本同时存在。高质量回答不能只说“都重要”，而是要根据当前阶段判断什么最优先、什么必须守底线、什么可以通过协调和计划去平衡。\n\n"
                    + "### 核心回答\n"
                    + "1. 先明确当前阶段最关键的目标，是保进度、保质量、保安全，还是在多目标之间做平衡。\n"
                    + "2. 再把现场管理拆成可执行动作，例如计划安排、技术交底、过程检查、问题整改、资料闭环和多方协调。\n"
                    + "3. 对于质量、安全、进度、成本之间的冲突，不能只停留在原则表态，而要说明你会如何排序、如何协调责任主体、如何建立检查和反馈机制。\n"
                    + "4. 最后补充监理、甲方、分包、班组之间的沟通方式，以及出现偏差时如何纠偏。\n\n"
                    + "### 真正落地时怎么做\n"
                    + "如果是质量问题，我会把控制点放在材料进场、技术交底、样板先行、过程检查和验收闭环上；如果是安全问题，会把高风险工序、班前交底、隐患排查和整改复查做成固定节奏；如果是进度问题，会把关键线路、资源到场、工序穿插和日周例会结合起来，保证问题暴露得足够早。\n\n"
                    + "### 面试里要体现的能力\n"
                    + "- 不是只会说原则，而是能把动作拆到现场执行层。\n"
                    + "- 不是只会说一个部门，而是知道施工单位、监理、甲方、分包之间怎么协同。\n"
                    + "- 不是只看当下问题，而是知道如何通过计划、检查、复盘形成闭环。\n\n"
                    + "### 建议展开的重点\n"
                    + "- 现场问题要落到具体环节，比如材料进场、工序交接、隐蔽验收、班组执行和整改闭环。\n"
                    + "- 只说“加强管理”不够，需要说明由谁负责、怎么检查、什么时候复盘。\n"
                    + "- 如果项目约束很强，还要体现你对进度、质量、安全之间取舍的判断。\n\n"
                    + "### 总结\n"
                    + "高质量回答要体现工程现场感和执行细节，让人听完能感受到你不是只会说原则，而是真的知道项目该怎么推进。";
        }
        String joinedTags = CollUtil.isEmpty(tags) ? "核心概念、适用场景、风险取舍" : String.join("、", tags);
        return String.format(
                "### 标准答案\n"
                        + "%s 这道题本质上是在考察候选人是否能把问题背景、核心方案和风险取舍讲清楚。标准答案不能只停留在概念层，而要说明它要解决什么问题、为什么这么设计、在真实项目里怎么落地，以及如果场景变化该怎么调整。\n\n"
                        + "### 问题理解\n"
                        + "先从问题本身入手：%s 一般会和业务中的效率、正确性、稳定性或协同成本相关。回答时要先讲清楚它解决的核心矛盾，而不是一上来就堆方案名词。\n\n"
                        + "### 核心回答\n"
                        + "1. %s 的核心目标是解决相关业务中的效率、正确性或稳定性问题。\n"
                        + "2. 实际落地时，通常会先明确输入输出、边界条件和关键约束，再选择一套主方案实现。\n"
                        + "3. 在实现过程中，需要重点关注 %s，同时兼顾性能、可维护性和异常处理。\n"
                        + "4. 如果业务规模继续扩大，还要补充扩展性设计、监控告警和降级兜底方案。\n\n"
                        + "### 落地思路\n"
                        + "如果我来做，一般会先梳理场景边界，再给出主方案，然后明确几个关键问题：数据怎么流转、异常怎么处理、性能瓶颈可能在哪里、如何验证方案有效。这样回答会更像真正做过设计，而不是只会背知识点。\n\n"
                        + "### 风险与取舍\n"
                        + "这类问题没有绝对万能解。高质量回答还要体现取舍意识，例如实现复杂度和稳定性怎么平衡、短期交付和长期扩展怎么平衡、性能和一致性怎么平衡。把这些讲出来，答案会明显更完整。\n\n"
                        + "### 进一步说明\n"
                        + "%s 相关题目在面试中往往不是只看你会不会背概念，更看你能不能把方案讲完整，包括为什么这么做、什么时候适用、有哪些风险，以及如果场景变化要怎么调整。\n\n"
                        + "### 总结\n"
                        + "因此，这类题的高质量答案应该既有概念定义，也有可落地方案，还要明确风险取舍和扩展思路。",
                topic, topic, topic, joinedTags, topic);
    }

    private String buildAdminQuestionContent(String topic) {
        if (containsAnyIgnoreCase(topic, "RabbitMQ", "延迟队列")) {
            return "在订单超时关闭、延迟通知、定时重试等业务场景中，经常需要延迟消息能力。"
                    + "假设你的项目当前使用 RabbitMQ 作为消息中间件，现在需要设计一套延迟队列方案。\n\n"
                    + "请回答：\n"
                    + "1. RabbitMQ 为什么默认不直接支持延迟队列？\n"
                    + "2. 在实际项目中可以通过哪些方式实现延迟队列？\n"
                    + "3. 不同实现方式分别适用于什么场景？\n"
                    + "4. 如果让你在生产环境落地，你会如何做方案选型和风险控制？";
        }
        if (containsAnyIgnoreCase(topic, "缓存击穿", "hot key", "热点 key")) {
            return "在高并发场景下，如果某个热点数据突然过期，可能导致大量请求同时访问数据库，进而引发服务压力激增。"
                    + "假设你负责系统缓存设计，需要分析并解决这个问题。\n\n"
                    + "请回答：\n"
                    + "1. 什么是缓存击穿？它和缓存穿透、缓存雪崩有什么区别？\n"
                    + "2. 常见的解决方案有哪些？\n"
                    + "3. 各种方案的适用场景和风险点是什么？\n"
                    + "4. 如果业务是高并发热点场景，你会如何做最终方案设计？";
        }
        if (containsAnyIgnoreCase(topic, "缓存穿透")) {
            return "在某些接口中，攻击者可能反复请求数据库和缓存里都不存在的数据，导致系统频繁回源数据库。"
                    + "假设你负责接口的缓存防护设计，需要分析这个问题并给出解决方案。\n\n"
                    + "请回答：\n"
                    + "1. 什么是缓存穿透？\n"
                    + "2. 为什么它会对系统造成压力？\n"
                    + "3. 常见的解决方案有哪些？\n"
                    + "4. 如果需要兼顾性能和准确性，你会怎么设计？";
        }
        if (containsAnyIgnoreCase(topic, "缓存雪崩")) {
            return "在业务高峰期，如果大量缓存同时失效，或者缓存服务整体异常，可能会导致请求集中打到数据库。"
                    + "假设你正在设计系统的缓存高可用方案，需要分析这个问题并提出改进思路。\n\n"
                    + "请回答：\n"
                    + "1. 什么是缓存雪崩？\n"
                    + "2. 缓存雪崩通常由哪些原因引发？\n"
                    + "3. 该如何从缓存设计、服务治理和高可用角度进行防护？\n"
                    + "4. 如果让你设计完整治理方案，你会如何落地？";
        }
        if (containsAnyIgnoreCase(topic, "索引失效", "MySQL")) {
            return "在 MySQL 性能优化中，明明已经建立了索引，但有些 SQL 执行时仍然没有走索引。"
                    + "假设你在排查慢查询，需要分析索引失效的原因并给出优化方案。\n\n"
                    + "请回答：\n"
                    + "1. 什么情况下会出现索引失效？\n"
                    + "2. 常见的索引失效场景有哪些？\n"
                    + "3. 应该如何借助 explain 等工具进行排查？\n"
                    + "4. 如果你来优化这类 SQL，会优先从哪些方面入手？";
        }
        if (isCivilEngineeringContext(topic)) {
            return "一个土木工程项目进入关键施工阶段，现场同时面临工期压力、质量要求和多专业协同问题。"
                    + "项目负责人需要在有限资源下保证现场平稳推进。\n\n"
                    + "请回答：\n"
                    + "1. 你会如何识别当前阶段最重要的管理目标？\n"
                    + "2. 针对现场组织、质量控制、安全管理和沟通协调，你会先抓哪些动作？\n"
                    + "3. 如果计划推进和现场实际出现偏差，你会怎么纠偏？\n"
                    + "4. 如何让你的方案真正落到班组和施工现场，而不是只停留在纸面上？";
        }
        return String.format(
                "你正在负责 %s 相关工作，需要结合一个真实业务场景设计并讲清楚方案。\n\n"
                        + "请回答：\n"
                        + "1. 先说明这个主题在实际工作中主要解决什么问题。\n"
                        + "2. 结合一个具体场景，分析实施时最容易遇到的约束或难点。\n"
                        + "3. 给出一套可执行的思路，并说明为什么这样设计。\n"
                        + "4. 如果面试官继续追问风险、边界和取舍，你会如何展开。",
                topic);
    }

    private boolean isGuidanceStyleAnswer(String answer) {
        if (StringUtils.isBlank(answer)) {
            return true;
        }
        String normalized = answer.replaceAll("\\s+", "");
        return containsAnyIgnoreCase(normalized,
                "回答思路", "答题思路", "面试表达建议", "可展开的关键点", "可以从以下几个方面回答", "作答思路");
    }

    private boolean isAnswerStyleContent(String content) {
        if (StringUtils.isBlank(content)) {
            return true;
        }
        String normalized = content.replaceAll("\\s+", "");
        return containsAnyIgnoreCase(normalized,
                "标准答案", "核心解决方案", "总结", "注意：", "注意:", "推荐：", "推荐:",
                "可以通过以下", "主流方式实现", "方案分析", "优缺点", "常见坑点", "适用场景");
    }

    private List<String> getWeakTags(long userId, int limit) {
        List<UserQuestionRecord> questionRecordList = userQuestionRecordService.list(
                Wrappers.lambdaQuery(UserQuestionRecord.class)
                        .eq(UserQuestionRecord::getUserId, userId)
                        .orderByDesc(UserQuestionRecord::getCreateTime)
                        .last("limit 50")
        );
        if (CollUtil.isEmpty(questionRecordList)) {
            return new ArrayList<>();
        }
        Set<Long> questionIdSet = questionRecordList.stream()
                .map(UserQuestionRecord::getQuestionId)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionService.listByIds(questionIdSet).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        Map<String, Integer> weakTagCounter = new LinkedHashMap<>();
        for (UserQuestionRecord record : questionRecordList) {
            List<String> tags = questionMap.containsKey(record.getQuestionId())
                    ? parseJsonArray(questionMap.get(record.getQuestionId()).getTags()) : Collections.emptyList();
            for (String tag : tags) {
                if (Integer.valueOf(1).equals(record.getUsedAi())
                        || QuestionRecordActionConstant.INCORRECT.equals(record.getActionType())
                        || QuestionRecordActionConstant.USE_AI_HINT.equals(record.getActionType())) {
                    weakTagCounter.put(tag, weakTagCounter.getOrDefault(tag, 0) + 1);
                }
            }
        }
        return topKeys(weakTagCounter, limit);
    }

    private int scoreQuestion(Question question, List<String> weakTags) {
        List<String> tags = parseJsonArray(question.getTags());
        if (CollUtil.isEmpty(weakTags)) {
            return tags.size();
        }
        int score = 0;
        for (String weakTag : weakTags) {
            if (tags.contains(weakTag)) {
                score += 3;
            }
        }
        return score + tags.size();
    }

    private String buildRecommendReasonByQwen(List<String> weakTags, List<QuestionVO> questionVOList) {
        if (!qwenManager.isAvailable() || CollUtil.isEmpty(questionVOList)) {
            return null;
        }
        String questionTitles = questionVOList.stream().map(QuestionVO::getTitle).collect(Collectors.joining("、"));
        String prompt = String.format(
                "你是程序员刷题平台的学习规划助手。用户薄弱标签：%s。推荐题目：%s。请用 1 到 2 句话说明推荐理由，不要使用 markdown。",
                CollUtil.isEmpty(weakTags) ? "暂无明显薄弱标签" : String.join("、", weakTags),
                questionTitles
        );
        return qwenManager.chat("输出简洁自然语言，不要超过 60 个字。", prompt);
    }

    private boolean containsAnyIgnoreCase(String source, String... keywords) {
        if (StringUtils.isBlank(source) || keywords == null) {
            return false;
        }
        String lowerSource = source.toLowerCase();
        for (String keyword : keywords) {
            if (StringUtils.isNotBlank(keyword) && lowerSource.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCivilEngineeringContext(String source) {
        if (StringUtils.isBlank(source)) {
            return false;
        }
        if (containsAnyIgnoreCase(source, "土木", "土建", "施工现场", "工地", "监理", "甲方", "总包", "分包",
                "劳务班组", "隐蔽工程", "混凝土", "钢筋", "浇筑", "模板支撑", "高处作业", "桩基", "基坑", "验收")) {
            return true;
        }
        return containsAnyIgnoreCase(source, "工程")
                && containsAnyIgnoreCase(source, "施工", "工期", "造价", "班组", "监理", "验收", "现场");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String getNormalizedTopic(String seed) {
        if (StringUtils.isBlank(seed)) {
            return "通用";
        }
        String normalized = seed.replaceAll("[#>*`\\r\\n]", " ")
                .replaceAll("[，。；：、,.;:()（）【】\\[\\]\"“”‘’]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        normalized = normalized
                .replaceAll("(请帮我|帮我|请结合|结合|请以|请围绕|围绕)", " ")
                .replaceAll("(批量生成\\d+道|生成\\d+道|出\\d+道|设计\\d+道|做\\d+道)", " ")
                .replaceAll("(题目|题|面试题|高频考点|真实使用场景|真实业务场景|并尽量结合真实.*)", " ")
                .replaceAll("(偏中高级|偏高级|偏基础|偏进阶|重点考察|重点关注)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (containsAnyIgnoreCase(normalized, "Redis")) {
            if (containsAnyIgnoreCase(normalized, "并发", "锁")) {
                return "Redis 并发控制";
            }
            if (containsAnyIgnoreCase(normalized, "缓存", "热点")) {
                return "Redis 缓存设计";
            }
            return "Redis 核心应用";
        }
        if (containsAnyIgnoreCase(normalized, "MySQL")) {
            if (containsAnyIgnoreCase(normalized, "索引")) {
                return "MySQL 索引优化";
            }
            return "MySQL 性能优化";
        }
        if (isCivilEngineeringContext(normalized)) {
            if (containsAnyIgnoreCase(normalized, "质量")) {
                return "土木工程质量管理";
            }
            if (containsAnyIgnoreCase(normalized, "安全")) {
                return "土木工程安全管理";
            }
            if (containsAnyIgnoreCase(normalized, "进度", "工期")) {
                return "土木工程进度管理";
            }
            if (containsAnyIgnoreCase(normalized, "成本", "造价")) {
                return "土木工程成本控制";
            }
            return "土木工程施工管理";
        }
        if (normalized.length() > 18) {
            normalized = normalized.substring(0, 18);
        }
        return normalized;
    }

    private String getSourceByModelName(String modelName) {
        if (StringUtils.isBlank(modelName)) {
            return "local";
        }
        return modelName.startsWith("qwen") ? "qwen" : "local";
    }

    private String normalizeArrayJson(Object value) {
        if (value == null) {
            return "[]";
        }
        if (value instanceof String) {
            String str = (String) value;
            if (StringUtils.isBlank(str)) {
                return "[]";
            }
            try {
                JSONUtil.parseArray(str);
                return str;
            } catch (Exception e) {
                return JSONUtil.toJsonStr(Arrays.asList(str));
            }
        }
        return JSONUtil.toJsonStr(value);
    }

    private List<String> parseJsonArray(String value) {
        if (StringUtils.isBlank(value)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(JSONUtil.parseArray(value), String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String getFirstMeaningfulLine(String markdown, String fallback) {
        if (StringUtils.isBlank(markdown)) {
            return fallback;
        }
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            String normalized = line.replaceAll("[#>*`-]", " ").replaceAll("\\s+", " ").trim();
            if (StringUtils.isNotBlank(normalized)) {
                return normalized.length() > 48 ? normalized.substring(0, 48) + "..." : normalized;
            }
        }
        return fallback;
    }

    private List<String> topKeys(Map<String, Integer> sourceMap, int limit) {
        return sourceMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private long totalDays(LocalDate startDate, LocalDate endDate) {
        return endDate.toEpochDay() - startDate.toEpochDay() + 1;
    }

    /**
     * 搜索意图
     */
    @lombok.Data
    private static class QueryIntent {
        private String rewrittenQuery;
        private String reason;
        private List<String> tags = new ArrayList<>();
        private String source;
    }
}
