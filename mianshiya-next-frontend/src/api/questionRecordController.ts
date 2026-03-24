// @ts-ignore
/* eslint-disable */
import request from '@/libs/request';

/** addQuestionRecord POST /api/question_record/add */
export async function addQuestionRecordUsingPost(
  body: API.QuestionRecordAddRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question_record/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
