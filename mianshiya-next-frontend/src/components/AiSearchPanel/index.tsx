"use client";

import { Button, Card, Flex, Input, Space, Tag, Typography, message } from "antd";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { aiSearchQuestionsUsingPost } from "@/api/aiController";
import QuestionList from "@/components/QuestionList";
import "./index.css";

const { Paragraph, Text, Title } = Typography;

/**
 * AI 智能搜索面板
 *
 * @constructor
 */
export default function AiSearchPanel({ initialQuery = "" }: { initialQuery?: string }) {
  const router = useRouter();
  const [query, setQuery] = useState<string>(initialQuery);
  const [loading, setLoading] = useState<boolean>(false);
  const [result, setResult] = useState<API.AiQuestionSearchVO>();
  const [searchMode, setSearchMode] = useState<"normal" | "ai">("normal");

  useEffect(() => {
    setQuery(initialQuery);
    setResult(undefined);
    setSearchMode("normal");
  }, [initialQuery]);

  const doNormalSearch = () => {
    const trimmedQuery = query.trim();
    setSearchMode("normal");
    setResult(undefined);
    router.push(trimmedQuery ? `/questions?q=${encodeURIComponent(trimmedQuery)}` : "/questions");
  };

  const doSearch = async () => {
    const trimmedQuery = query.trim();
    if (!trimmedQuery) {
      message.warning("请输入你想搜索的内容");
      return;
    }
    setSearchMode("ai");
    setLoading(true);
    try {
      const res = await aiSearchQuestionsUsingPost({
        query: trimmedQuery,
        pageSize: 6,
      });
      setResult(res.data);
    } catch (e) {
      message.error("AI 智能搜索失败，" + (e as Error).message);
    }
    setLoading(false);
  };

  return (
    <Card id="aiSearchPanel">
      <div className="hero-copy">
        <Tag color="gold">题目搜索中心</Tag>
        <Title level={3} style={{ marginTop: 12, marginBottom: 8 }}>
          用一句自然语言，直接找到你现在该刷的题
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          支持普通关键词搜索，也支持 AI 理解你的意图，比如“我想刷 Redis 缓存击穿和热点 key”。
        </Paragraph>
      </div>
      <Input
        className="hero-input"
        size="large"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="输入关键词，或直接描述你想练的知识点 / 场景"
        onPressEnter={() => {
          if (searchMode === "ai") {
            void doSearch();
            return;
          }
          doNormalSearch();
        }}
      />
      <Flex gap={12} wrap="wrap" className="hero-actions hero-mode-list">
        <Button
          size="large"
          type={searchMode === "normal" ? "primary" : "default"}
          className="hero-mode-button"
          onClick={doNormalSearch}
        >
          普通搜索
        </Button>
        <Button
          type={searchMode === "ai" ? "primary" : "default"}
          size="large"
          loading={loading}
          className="hero-mode-button"
          onClick={() => {
            void doSearch();
          }}
        >
          AI 智能搜索
        </Button>
      </Flex>
      <div className="hero-meta">
        <Tag>普通搜索：适合精确关键词</Tag>
        <Tag color="blue">AI 搜索：适合场景化描述</Tag>
      </div>
      {searchMode === "ai" && result && (
        <Card className="result-card" bordered={false}>
          <Paragraph style={{ marginBottom: 8 }}>
            <Text strong>搜索意图：</Text>
            {result.reason}
          </Paragraph>
          <Paragraph style={{ marginBottom: 8 }}>
            <Text strong>改写关键词：</Text>
            {result.rewrittenQuery}
          </Paragraph>
          <div style={{ marginBottom: 12 }}>
            {(result.tags ?? []).map((tag) => (
              <Tag key={tag}>{tag}</Tag>
            ))}
          </div>
          <QuestionList cardTitle="AI 推荐结果" questionList={result.questions ?? []} />
        </Card>
      )}
    </Card>
  );
}
