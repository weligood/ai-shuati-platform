package com.yupi.mianshiya.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.mianshiya.mapper.UserQuestionRecordMapper;
import com.yupi.mianshiya.model.entity.UserQuestionRecord;
import com.yupi.mianshiya.service.UserQuestionRecordService;
import org.springframework.stereotype.Service;

/**
 * 用户题目行为记录服务实现
 */
@Service
public class UserQuestionRecordServiceImpl extends ServiceImpl<UserQuestionRecordMapper, UserQuestionRecord>
        implements UserQuestionRecordService {
}
