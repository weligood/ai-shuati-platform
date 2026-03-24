package com.yupi.mianshiya.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.mianshiya.mapper.QuestionAiContentMapper;
import com.yupi.mianshiya.model.entity.QuestionAiContent;
import com.yupi.mianshiya.service.QuestionAiContentService;
import org.springframework.stereotype.Service;

/**
 * 题目 AI 内容服务实现
 */
@Service
public class QuestionAiContentServiceImpl extends ServiceImpl<QuestionAiContentMapper, QuestionAiContent>
        implements QuestionAiContentService {
}
