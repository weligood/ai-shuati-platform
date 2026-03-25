package com.weligood.mianshiya.manager;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpException;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.weligood.mianshiya.config.AiConfig;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 通义千问调用管理器
 */
@Component
@Slf4j
public class QwenManager {

    @Resource
    private AiConfig aiConfig;

    /**
     * 是否可用
     *
     * @return 是否已配置 API Key
     */
    public boolean isAvailable() {
        return StringUtils.isNotBlank(aiConfig.getQwenApiKey());
    }

    /**
     * 调用通义千问生成文本
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 文本结果
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            return null;
        }
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", aiConfig.getQwenModel());
            requestBody.put("temperature", aiConfig.getTemperature());
            JSONArray messages = new JSONArray();
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.add(userMessage);
            requestBody.put("messages", messages);
            try (HttpResponse response = HttpRequest.post(aiConfig.getQwenEndpoint())
                    .header("Authorization", "Bearer " + aiConfig.getQwenApiKey())
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .timeout(aiConfig.getTimeoutMillis())
                    .body(requestBody.toString())
                    .execute()) {
                if (!response.isOk()) {
                    log.warn("Qwen request failed, status={}, body={}", response.getStatus(), response.body());
                    return null;
                }
                JSONObject responseJson = JSONUtil.parseObj(response.body());
                JSONArray choices = responseJson.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) {
                    return null;
                }
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.getJSONObject("message");
                if (message == null) {
                    return null;
                }
                return message.getStr("content");
            }
        } catch (HttpException e) {
            log.warn("Qwen request timeout or network error: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Qwen request unexpected error", e);
            return null;
        }
    }

    /**
     * 提取 Markdown 代码块中的 JSON
     *
     * @param text 文本
     * @return JSON 字符串
     */
    public String extractJson(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart >= 0) {
            int start = trimmed.indexOf('\n', jsonStart);
            int end = trimmed.indexOf("```", start + 1);
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return null;
    }

    /**
     * 解析 json 数组
     *
     * @param value 文本
     * @return 列表
     */
    public List<String> parseStringList(String value) {
        if (StringUtils.isBlank(value)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(JSONUtil.parseArray(value), String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}


