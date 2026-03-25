import { updateQuestionUsingPost } from "@/api/questionController";
import AiDraftPanel from "./AiDraftPanel";
import { ProColumns, ProFormInstance, ProTable } from "@ant-design/pro-components";
import { message, Modal } from "antd";
import React, { useRef } from "react";

interface Props {
  oldData?: API.Question;
  visible: boolean;
  columns: ProColumns<API.Question>[];
  onSubmit: (values: API.QuestionAddRequest) => void;
  onCancel: () => void;
}

/**
 * 更新节点
 *
 * @param fields
 */
const handleUpdate = async (fields: API.QuestionUpdateRequest) => {
  const hide = message.loading("正在更新");
  try {
    await updateQuestionUsingPost(fields);
    hide();
    message.success("更新成功");
    return true;
  } catch (error: any) {
    hide();
    message.error("更新失败，" + error.message);
    return false;
  }
};

/**
 * 更新弹窗
 * @param props
 * @constructor
 */
const UpdateModal: React.FC<Props> = (props) => {
  const { oldData, visible, columns, onSubmit, onCancel } = props;
  const formRef = useRef<ProFormInstance<API.QuestionUpdateRequest>>();

  if (!oldData?.id) {
    return <></>;
  }

  // 表单初始化值格式转换
  const initValues = { ...oldData };
  if (oldData.tags) {
    initValues.tags = JSON.parse(oldData.tags) || [];
  }

  return (
    <Modal
      destroyOnClose
      title={"更新题目"}
      open={visible}
      footer={null}
      width={920}
      onCancel={() => {
        onCancel?.();
      }}
    >
      <AiDraftPanel
        currentData={oldData}
        onApply={(draft) => {
          formRef.current?.setFieldsValue({
            title: draft.title,
            content: draft.content,
            answer: draft.answer,
            tags: draft.tags,
          });
        }}
      />
      <ProTable
        type="form"
        formRef={formRef}
        columns={columns}
        form={{
          initialValues: initValues,
        }}
        onSubmit={async (values: API.QuestionAddRequest) => {
          const success = await handleUpdate({
            ...values,
            id: oldData?.id,
          });
          if (success) {
            onSubmit?.(values);
          }
        }}
      />
    </Modal>
  );
};
export default UpdateModal;
