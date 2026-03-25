package com.weligood.mianshiya.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weligood.mianshiya.mapper.UserQuestionRecordMapper;
import com.weligood.mianshiya.model.entity.UserQuestionRecord;
import com.weligood.mianshiya.service.UserQuestionRecordService;
import org.springframework.stereotype.Service;

/**
 * 用户题目行为记录服务实现
 */
@Service
public class UserQuestionRecordServiceImpl extends ServiceImpl<UserQuestionRecordMapper, UserQuestionRecord>
        implements UserQuestionRecordService {
}


