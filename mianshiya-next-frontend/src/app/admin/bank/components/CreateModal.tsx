import { addQuestionBankUsingPost } from '@/api/questionBankController';
import AiDraftPanel from "./AiDraftPanel";
import { ProColumns, ProFormInstance, ProTable } from '@ant-design/pro-components';
import { message, Modal } from 'antd';
import React, { useRef } from 'react';

interface Props {
  visible: boolean;
  columns: ProColumns<API.QuestionBank>[];
  onSubmit: (values: API.QuestionBankAddRequest) => void;
  onCancel: () => void;
}

/**
 * 添加节点
 * @param fields
 */
const handleAdd = async (fields: API.QuestionBankAddRequest) => {
  const hide = message.loading('正在添加');
  try {
    await addQuestionBankUsingPost(fields);
    hide();
    message.success('创建成功');
    return true;
  } catch (error: any) {
    hide();
    message.error('创建失败，' + error.message);
    return false;
  }
};

/**
 * 创建弹窗
 * @param props
 * @constructor
 */
const CreateModal: React.FC<Props> = (props) => {
  const { visible, columns, onSubmit, onCancel } = props;
  const formRef = useRef<ProFormInstance<API.QuestionBankAddRequest>>();

  return (
    <Modal
      destroyOnClose
      title={'创建题库'}
      open={visible}
      footer={null}
      width={920}
      onCancel={() => {
        onCancel?.();
      }}
    >
      <AiDraftPanel
        onApply={(draft) => {
          formRef.current?.setFieldsValue({
            title: draft.title,
            description: draft.description,
            picture: draft.picture,
          });
        }}
      />
      <ProTable
        type="form"
        formRef={formRef}
        columns={columns}
        onSubmit={async (values: API.QuestionBankAddRequest) => {
          const success = await handleAdd(values);
          if (success) {
            onSubmit?.(values);
          }
        }}
      />
    </Modal>
  );
};
export default CreateModal;
