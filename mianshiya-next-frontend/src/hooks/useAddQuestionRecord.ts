import { useEffect } from "react";
import { addQuestionRecordUsingPost } from "@/api/questionRecordController";

/**
 * 进入题目页时记录一次浏览行为
 *
 * @param questionId 题目 id
 * @param questionBankId 题库 id
 */
const useAddQuestionRecord = (questionId?: number, questionBankId?: number) => {
  useEffect(() => {
    if (!questionId) {
      return;
    }
    addQuestionRecordUsingPost({
      questionId,
      questionBankId,
      actionType: "view",
    }).catch(() => {
      // 行为记录失败不影响正常刷题
    });
  }, [questionId, questionBankId]);
};

export default useAddQuestionRecord;
