"use client";

import { generateAdminQuestionDraftUsingPost } from "@/api/aiController";
import { Alert, Button, Card, Input, Space, Tag, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";

const { Paragraph, Text } = Typography;

interface Props {
  currentData?: Partial<API.Question>;
  onApply: (draft: API.AiAdminQuestionDraftVO) => void;
}

const parseTags = (tags?: string | string[]) => {
  if (Array.isArray(tags)) {
    return tags;
  }
  if (!tags) {
    return [];
  }
  try {
    return JSON.parse(tags);
  } catch (e) {
    return [];
  }
};

const AiDraftPanel = ({ currentData, onApply }: Props) => {
  const [topic, setTopic] = useState("");
  const [loading, setLoading] = useState(false);
  const [draft, setDraft] = useState<API.AiAdminQuestionDraftVO>();

  const placeholder = useMemo(() => {
    return currentData?.title
      ? "可补充你希望 AI 强化的方向，比如“更偏高并发场景”"
      : "输入主题，例如 Redis 缓存击穿、MySQL 索引失效、Java 并发";
  }, [currentData?.title]);

  useEffect(() => {
    setTopic(currentData?.title || "");
    setDraft(undefined);
  }, [currentData?.title, currentData?.content, currentData?.answer, currentData?.tags]);

  const generateDraft = async () => {
    const trimmedTopic = topic.trim();
    if (
      !trimmedTopic &&
      !currentData?.title &&
      !currentData?.content &&
      !currentData?.answer
    ) {
      message.warning("请先输入一个主题，或者先填写部分题目信息");
      return;
    }
    setLoading(true);
    try {
      const res = await generateAdminQuestionDraftUsingPost({
        topic: trimmedTopic,
        currentTitle: currentData?.title,
        currentContent: currentData?.content,
        currentAnswer: currentData?.answer,
        currentTags: parseTags(currentData?.tags),
      });
      setDraft(res.data);
    } catch (e) {
      message.error("AI 生成题目草稿失败，" + (e as Error).message);
    }
    setLoading(false);
  };

  return (
    <Card
      size="small"
      title="AI 出题助手"
      extra={<Tag color="blue">只生成草稿，不会直接入库</Tag>}
      style={{ marginBottom: 16, borderRadius: 14 }}
    >
      <Paragraph type="secondary" style={{ marginBottom: 12 }}>
        输入一个题目主题，AI 会帮你生成标题、题干、答案和标签；编辑题目时则会基于当前内容补全草稿。
      </Paragraph>
      <Input.TextArea
        rows={3}
        value={topic}
        onChange={(e) => setTopic(e.target.value)}
        placeholder={placeholder}
      />
      <Space style={{ marginTop: 12 }} wrap>
        <Button type="primary" loading={loading} onClick={generateDraft}>
          {currentData?.id ? "AI 补全草稿" : "AI 生成题目"}
        </Button>
        {draft ? (
          <Button
            onClick={() => {
              onApply(draft);
              message.success("已将 AI 草稿填充到表单");
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
            message={draft.summary || "AI 已生成一版题目草稿，建议人工再校验场景和表述。"}
          />
          <div style={{ marginTop: 12 }}>
            <Text strong>草稿标题：</Text>
            <div style={{ marginTop: 6 }}>{draft.title || "-"}</div>
          </div>
          <div style={{ marginTop: 12 }}>
            <Text strong>建议标签：</Text>
            <div style={{ marginTop: 8 }}>
              {(draft.tags ?? []).map((tag) => (
                <Tag key={tag}>{tag}</Tag>
              ))}
            </div>
          </div>
        </div>
      ) : null}
    </Card>
  );
};

export default AiDraftPanel;
