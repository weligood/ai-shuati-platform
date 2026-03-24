package com.yupi.mianshiya.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.mianshiya.mapper.UserAiReportMapper;
import com.yupi.mianshiya.model.entity.UserAiReport;
import com.yupi.mianshiya.service.UserAiReportService;
import org.springframework.stereotype.Service;

/**
 * 用户 AI 学习报告服务实现
 */
@Service
public class UserAiReportServiceImpl extends ServiceImpl<UserAiReportMapper, UserAiReport>
        implements UserAiReportService {
}
