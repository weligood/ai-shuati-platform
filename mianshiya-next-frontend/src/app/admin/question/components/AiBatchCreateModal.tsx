"use client";

import { generateAdminQuestionBatchDraftUsingPost } from "@/api/aiController";
import { listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import { batchAddQuestionsToBankUsingPost } from "@/api/questionBankQuestionController";
import { addQuestionUsingPost } from "@/api/questionController";
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from "antd";
import React, { useEffect, useMemo, useState } from "react";

const { Paragraph, Text } = Typography;

interface Props {
  visible: boolean;
  onSubmit: () => void;
  onCancel: () => void;
}

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

const formatHistory = (history: ChatMessage[]) => {
  return history.map((item) =>
    `${item.role === "user" ? "管理员" : "AI"}：${item.content}`,
  );
};

const AiBatchCreateModal: React.FC<Props> = ({
  visible,
  onSubmit,
  onCancel,
}) => {
  const [prompt, setPrompt] = useState("");
  const [count, setCount] = useState<number>(5);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [history, setHistory] = useState<ChatMessage[]>([]);
  const [assistantMessage, setAssistantMessage] = useState("");
  const [drafts, setDrafts] = useState<API.AiAdminQuestionDraftVO[]>([]);
  const [selectedIndexes, setSelectedIndexes] = useState<number[]>([]);
  const [questionBankId, setQuestionBankId] = useState<number>();
  const [questionBankList, setQuestionBankList] = useState<API.QuestionBankVO[]>(
    [],
  );

  const selectedDrafts = useMemo(() => {
    return selectedIndexes
      .sort((a, b) => a - b)
      .map((index) => drafts[index])
      .filter(Boolean);
  }, [drafts, selectedIndexes]);

  const resetState = () => {
    setPrompt("");
    setCount(5);
    setHistory([]);
    setAssistantMessage("");
    setDrafts([]);
    setSelectedIndexes([]);
    setQuestionBankId(undefined);
  };

  const loadQuestionBanks = async () => {
    try {
      const res = (await listQuestionBankVoByPageUsingPost({
        pageSize: 200,
        sortField: "createTime",
        sortOrder: "descend",
      })) as unknown as API.BaseResponsePageQuestionBankVO_;
      setQuestionBankList(res.data?.records ?? []);
    } catch (e: any) {
      message.error("加载题库列表失败，" + e.message);
    }
  };

  useEffect(() => {
    if (visible) {
      loadQuestionBanks();
    }
  }, [visible]);

  const handleGenerate = async () => {
    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt) {
      message.warning("请先输入你想批量生成的题目要求");
      return;
    }
    setLoading(true);
    try {
      const res = (await generateAdminQuestionBatchDraftUsingPost({
        prompt: trimmedPrompt,
        history: formatHistory(history),
        count,
      })) as unknown as API.BaseResponseAiAdminQuestionBatchDraftVO_;
      const nextDrafts = res.data?.questionDrafts ?? [];
      const nextAssistantMessage =
        res.data?.assistantMessage ?? "已生成一批题目草稿，可继续补充要求。";
      setHistory((prev) => [
        ...prev,
        { role: "user", content: trimmedPrompt },
        { role: "assistant", content: nextAssistantMessage },
      ]);
      setAssistantMessage(nextAssistantMessage);
      setDrafts(nextDrafts);
      setSelectedIndexes(nextDrafts.map((_: API.AiAdminQuestionDraftVO, index: number) => index));
      setPrompt("");
    } catch (e: any) {
      message.error("AI 批量出题失败，" + e.message);
    }
    setLoading(false);
  };

  const handleImport = async () => {
    if (selectedDrafts.length === 0) {
      message.warning("请至少选择一道题目草稿");
      return;
    }
    setSaving(true);
    const createdQuestionIds: number[] = [];
    const failedTitles: string[] = [];
    for (const draft of selectedDrafts) {
      try {
        const res = (await addQuestionUsingPost({
          title: draft.title,
          content: draft.content,
          answer: draft.answer,
          tags: draft.tags,
        })) as unknown as API.BaseResponseLong_;
        if (res.data) {
          createdQuestionIds.push(res.data);
        } else {
          failedTitles.push(draft.title || "未命名题目");
        }
      } catch (e) {
        failedTitles.push(draft.title || "未命名题目");
      }
    }
    if (questionBankId && createdQuestionIds.length > 0) {
      try {
        await batchAddQuestionsToBankUsingPost({
          questionBankId,
          questionIdList: createdQuestionIds,
        });
      } catch (e: any) {
        message.error("题目已创建，但关联题库失败，" + e.message);
      }
    }
    setSaving(false);
    if (createdQuestionIds.length > 0) {
      if (failedTitles.length === 0) {
        message.success(`已创建 ${createdQuestionIds.length} 道题目`);
      } else {
        message.warning(
          `已创建 ${createdQuestionIds.length} 道题目，失败 ${failedTitles.length} 道`,
        );
      }
      resetState();
      onSubmit();
      return;
    }
    message.error("创建失败，请调整题目内容后重试");
  };

  return (
    <Modal
      destroyOnClose
      title="AI 对话批量出题"
      open={visible}
      width={1120}
      okText="批量入库"
      cancelText="关闭"
      okButtonProps={{ loading: saving, disabled: selectedDrafts.length === 0 }}
      onOk={handleImport}
      onCancel={onCancel}
      afterClose={resetState}
    >
      <Alert
        showIcon
        type="info"
        style={{ marginBottom: 16 }}
        message="管理员输入一段要求，AI 会按同一主题批量生成多道题目草稿；确认后可一键入库。"
      />
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Input.TextArea
            rows={4}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="例如：请帮我批量生成 5 道 Redis 题目，偏中高级，重点考察缓存、分布式锁和高并发场景。"
          />
          <Space wrap>
            <Select
              value={count}
              onChange={setCount}
              style={{ width: 120 }}
              options={[
                { label: "3 道", value: 3 },
                { label: "5 道", value: 5 },
                { label: "8 道", value: 8 },
              ]}
            />
            <Select
              allowClear
              placeholder="创建后可直接加入题库"
              value={questionBankId}
              onChange={setQuestionBankId}
              style={{ width: 280 }}
              options={questionBankList.map((questionBank) => ({
                label: questionBank.title,
                value: questionBank.id,
              }))}
            />
            <Button type="primary" loading={loading} onClick={handleGenerate}>
              发送给 AI
            </Button>
            <Button
              onClick={() => {
                setHistory([]);
                setAssistantMessage("");
                setDrafts([]);
                setSelectedIndexes([]);
              }}
            >
              清空对话
            </Button>
          </Space>
        </Space>
      </Card>

      {history.length > 0 ? (
        <Card size="small" title="对话上下文" style={{ marginBottom: 16 }}>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            {history.map((item, index) => (
              <div
                key={`${item.role}-${index}`}
                style={{
                  padding: 12,
                  borderRadius: 12,
                  background: item.role === "user" ? "#f6ffed" : "#f5f5f5",
                }}
              >
                <Text strong>{item.role === "user" ? "管理员" : "AI"}</Text>
                <div style={{ marginTop: 6, whiteSpace: "pre-wrap" }}>
                  {item.content}
                </div>
              </div>
            ))}
          </Space>
        </Card>
      ) : null}

      {assistantMessage ? (
        <Alert
          showIcon
          type="success"
          style={{ marginBottom: 16 }}
          message={assistantMessage}
        />
      ) : null}

      <Card
        size="small"
        title="题目草稿预览"
        extra={
          drafts.length > 0 ? (
            <Space>
              <Button
                size="small"
                onClick={() => setSelectedIndexes(drafts.map((_, index) => index))}
              >
                全选
              </Button>
              <Button size="small" onClick={() => setSelectedIndexes([])}>
                清空选择
              </Button>
              <Text type="secondary">已选 {selectedDrafts.length} 道</Text>
            </Space>
          ) : null
        }
      >
        {drafts.length === 0 ? (
          <Empty description="先和 AI 对话生成一批题目草稿" />
        ) : (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            {drafts.map((draft, index) => {
              const checked = selectedIndexes.includes(index);
              return (
                <Card
                  key={`${draft.title}-${index}`}
                  size="small"
                  style={{ borderRadius: 12 }}
                  title={
                    <Space>
                      <Checkbox
                        checked={checked}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setSelectedIndexes((prev) =>
                              Array.from(new Set([...prev, index])),
                            );
                          } else {
                            setSelectedIndexes((prev) =>
                              prev.filter((item) => item !== index),
                            );
                          }
                        }}
                      />
                      <span>{draft.title || `草稿 ${index + 1}`}</span>
                    </Space>
                  }
                  extra={
                    <Tag color={draft.source === "qwen" ? "blue" : "gold"}>
                      {draft.source || "local"}
                    </Tag>
                  }
                >
                  <Space wrap style={{ marginBottom: 8 }}>
                    {(draft.tags ?? []).map((tag) => (
                      <Tag key={`${tag}-${index}`}>{tag}</Tag>
                    ))}
                  </Space>
                  {draft.summary ? (
                    <Paragraph type="secondary" style={{ marginBottom: 12 }}>
                      {draft.summary}
                    </Paragraph>
                  ) : null}
                  <Text strong>题干</Text>
                  <Paragraph
                    style={{ whiteSpace: "pre-wrap", marginTop: 8 }}
                    ellipsis={{ rows: 6, expandable: true, symbol: "展开" }}
                  >
                    {draft.content || "-"}
                  </Paragraph>
                  <Text strong>答案</Text>
                  <Paragraph
                    style={{ whiteSpace: "pre-wrap", marginTop: 8, marginBottom: 0 }}
                    ellipsis={{ rows: 6, expandable: true, symbol: "展开" }}
                  >
                    {draft.answer || "-"}
                  </Paragraph>
                </Card>
              );
            })}
          </Space>
        )}
      </Card>
    </Modal>
  );
};

export default AiBatchCreateModal;
