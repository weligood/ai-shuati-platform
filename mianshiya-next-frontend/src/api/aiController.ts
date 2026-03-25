// @ts-ignore
/* eslint-disable */
import request from '@/libs/request';

/** getQuestionExplain POST /api/ai/question/explain */
export async function getQuestionExplainUsingPost(
  body: API.AiQuestionExplainRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiQuestionExplainVO_>('/api/ai/question/explain', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** getQuestionHint POST /api/ai/question/hint */
export async function getQuestionHintUsingPost(
  body: API.AiQuestionHintRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiQuestionHintVO_>('/api/ai/question/hint', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** recommendQuestions POST /api/ai/question/recommend */
export async function recommendQuestionsUsingPost(
  body: API.AiQuestionRecommendRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiQuestionRecommendVO_>('/api/ai/question/recommend', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** aiSearchQuestions POST /api/ai/search/questions */
export async function aiSearchQuestionsUsingPost(
  body: API.AiQuestionSearchRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiQuestionSearchVO_>('/api/ai/search/questions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** getUserAiReport GET /api/ai/user/report */
export async function getUserAiReportUsingGet(
  params?: API.getUserAiReportUsingGETParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseUserAiReportVO_>('/api/ai/user/report', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** generateAdminQuestionDraft POST /api/ai/admin/question/draft */
export async function generateAdminQuestionDraftUsingPost(
  body: API.AiAdminQuestionDraftRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiAdminQuestionDraftVO_>('/api/ai/admin/question/draft', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** generateAdminQuestionBatchDraft POST /api/ai/admin/question/draft/batch */
export async function generateAdminQuestionBatchDraftUsingPost(
  body: API.AiAdminQuestionBatchDraftRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiAdminQuestionBatchDraftVO_>(
    '/api/ai/admin/question/draft/batch',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      data: body,
      ...(options || {}),
    },
  );
}

/** generateAdminBankDraft POST /api/ai/admin/bank/draft */
export async function generateAdminBankDraftUsingPost(
  body: API.AiAdminBankDraftRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAiAdminBankDraftVO_>('/api/ai/admin/bank/draft', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
