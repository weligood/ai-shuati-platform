package com.yupi.mianshiya.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 配置
 */
@Component
@ConfigurationProperties(prefix = "ai")
@Data
public class AiConfig {

    /**
     * API Key，为空时自动回退到本地规则模式
     */
    private String qwenApiKey;

    /**
     * 模型名称
     */
    private String qwenModel = "qwen-plus";

    /**
     * 通义千问 OpenAI 兼容接口地址
     */
    private String qwenEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /**
     * 系统提示词温度
     */
    private Double temperature = 0.2D;

    /**
     * 调用超时时间
     */
    private Integer timeoutMillis = 15000;
}
