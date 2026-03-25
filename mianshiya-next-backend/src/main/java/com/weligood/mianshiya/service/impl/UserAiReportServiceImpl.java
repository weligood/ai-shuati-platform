package com.weligood.mianshiya.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weligood.mianshiya.mapper.UserAiReportMapper;
import com.weligood.mianshiya.model.entity.UserAiReport;
import com.weligood.mianshiya.service.UserAiReportService;
import org.springframework.stereotype.Service;

/**
 * 用户 AI 学习报告服务实现
 */
@Service
public class UserAiReportServiceImpl extends ServiceImpl<UserAiReportMapper, UserAiReport>
        implements UserAiReportService {
}


