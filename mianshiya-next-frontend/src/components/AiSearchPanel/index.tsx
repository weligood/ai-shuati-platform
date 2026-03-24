"use client";

import { Button, Card, Input, Space, Tag, Typography, message } from "antd";
import { useState } from "react";
import { aiSearchQuestionsUsingPost } from "@/api/aiController";
import QuestionList from "@/components/QuestionList";

const { Paragraph, Text } = Typography;

/**
 * AI 智能搜索面板
 *
 * @constructor
 */
export default function AiSearchPanel() {
  const [query, setQuery] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [result, setResult] = useState<API.AiQuestionSearchVO>();

  const doSearch = async () => {
    const trimmedQuery = query.trim();
    if (!trimmedQuery) {
      message.warning("请输入你想搜索的内容");
      return;
    }
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
    <Card title="AI 智能搜索" style={{ marginBottom: 24 }}>
      <Space.Compact style={{ width: "100%" }}>
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="比如：我想刷 Redis 缓存击穿、布隆过滤器、热点 key 相关题"
          onPressEnter={doSearch}
        />
        <Button type="primary" loading={loading} onClick={doSearch}>
          AI 搜索
        </Button>
      </Space.Compact>
      {result && (
        <div style={{ marginTop: 16 }}>
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
        </div>
      )}
    </Card>
  );
}
