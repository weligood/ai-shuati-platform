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
