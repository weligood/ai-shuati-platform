package com.yupi.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 用户 AI 学习报告视图
 */
@Data
public class UserAiReportVO implements Serializable {

    private String reportType;

    private String startDate;

    private String endDate;

    private String summary;

    private List<String> weakPoints;

    private List<String> recommendations;

    private Long totalRecords;

    private Long aiUsageCount;

    private String source;

    private static final long serialVersionUID = 1L;
}
