"use client";

import { generateAdminBankDraftUsingPost } from "@/api/aiController";
import { Alert, Button, Card, Input, List, Space, Tag, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";

const { Paragraph, Text } = Typography;

interface Props {
  currentData?: Partial<API.QuestionBank>;
  onApply: (draft: API.AiAdminBankDraftVO) => void;
}

const AiDraftPanel = ({ currentData, onApply }: Props) => {
  const [topic, setTopic] = useState("");
  const [loading, setLoading] = useState(false);
  const [draft, setDraft] = useState<API.AiAdminBankDraftVO>();

  const placeholder = useMemo(() => {
    return currentData?.title
      ? "可补充你希望这套题库覆盖的人群、场景或强化方向"
      : "输入题库主题，例如 Java 后端高频题、Redis 专项题库、前端八股冲刺";
  }, [currentData?.title]);

  useEffect(() => {
    setTopic(currentData?.title || "");
    setDraft(undefined);
  }, [currentData?.title, currentData?.description]);

  const generateDraft = async () => {
    const trimmedTopic = topic.trim();
    if (!trimmedTopic && !currentData?.title && !currentData?.description) {
      message.warning("请先输入题库主题，或者先填写部分题库信息");
      return;
    }
    setLoading(true);
    try {
      const res = await generateAdminBankDraftUsingPost({
        topic: trimmedTopic,
        currentTitle: currentData?.title,
        currentDescription: currentData?.description,
      });
      setDraft(res.data);
    } catch (e) {
      message.error("AI 生成题库草稿失败，" + (e as Error).message);
    }
    setLoading(false);
  };

  return (
    <Card
      size="small"
      title="AI 题库助手"
      extra={<Tag color="gold">先生成草稿，再由管理员确认</Tag>}
      style={{ marginBottom: 16, borderRadius: 14 }}
    >
      <Paragraph type="secondary" style={{ marginBottom: 12 }}>
        输入题库主题后，AI 会帮你生成题库标题、简介，以及推荐的题目方向，适合先搭一个可编辑的运营草案。
      </Paragraph>
      <Input.TextArea
        rows={3}
        value={topic}
        onChange={(e) => setTopic(e.target.value)}
        placeholder={placeholder}
      />
      <Space style={{ marginTop: 12 }} wrap>
        <Button type="primary" loading={loading} onClick={generateDraft}>
          {currentData?.id ? "AI 补全题库草稿" : "AI 生成题库"}
        </Button>
        {draft ? (
          <Button
            onClick={() => {
              onApply(draft);
              message.success("已将 AI 题库草稿填充到表单");
            }}
          >
            一键填充表单
          </Button>
        ) : null}
      </Space>
      {draft ? (
        <div style={{ marginTop: 16 }}>
          <Alert
            showIcon
            type="info"
            message={draft.summary || "AI 已生成题库草案，建议管理员再调整题目组织顺序。"}
          />
          <div style={{ marginTop: 12 }}>
            <Text strong>推荐题目方向：</Text>
            <List
              size="small"
              bordered={false}
              dataSource={draft.suggestedQuestionTitles ?? []}
              renderItem={(item) => <List.Item>{item}</List.Item>}
            />
          </div>
        </div>
      ) : null}
    </Card>
  );
};

export default AiDraftPanel;
