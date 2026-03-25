package com.weligood.mianshiya.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weligood.mianshiya.mapper.QuestionAiContentMapper;
import com.weligood.mianshiya.model.entity.QuestionAiContent;
import com.weligood.mianshiya.service.QuestionAiContentService;
import org.springframework.stereotype.Service;

/**
 * 题目 AI 内容服务实现
 */
@Service
public class QuestionAiContentServiceImpl extends ServiceImpl<QuestionAiContentMapper, QuestionAiContent>
        implements QuestionAiContentService {
}


