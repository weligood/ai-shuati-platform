"use client";

import { Card, List, Skeleton, Typography, message } from "antd";
import { useEffect, useState } from "react";
import { getUserAiReportUsingGet } from "@/api/aiController";

const { Paragraph, Text, Title } = Typography;

/**
 * AI 学习报告
 *
 * @constructor
 */
export default function AiReportCard() {
  const [loading, setLoading] = useState<boolean>(true);
  const [report, setReport] = useState<API.UserAiReportVO>();

  const fetchReport = async () => {
    setLoading(true);
    try {
      const res = await getUserAiReportUsingGet({
        reportType: "week",
      });
      setReport(res.data);
    } catch (e) {
      message.error("获取 AI 学习报告失败，" + (e as Error).message);
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchReport();
  }, []);

  if (loading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }

  if (!report) {
    return <div>暂无 AI 学习报告</div>;
  }

  return (
    <Card bordered={false}>
      <Title level={5} style={{ marginTop: 0 }}>
        最近 7 天学习报告
      </Title>
      <Paragraph type="secondary" style={{ marginBottom: 8 }}>
        {report.startDate} 至 {report.endDate}
      </Paragraph>
      <Paragraph>{report.summary}</Paragraph>
      <Paragraph style={{ marginBottom: 8 }}>
        <Text strong>刷题行为：</Text>
        {report.totalRecords ?? 0} 次
        <Text strong style={{ marginLeft: 16 }}>
          AI 使用：
        </Text>
        {report.aiUsageCount ?? 0} 次
      </Paragraph>
      <Title level={5}>薄弱点</Title>
      <List
        size="small"
        bordered
        dataSource={report.weakPoints ?? []}
        renderItem={(item) => <List.Item>{item}</List.Item>}
      />
      <div style={{ marginBottom: 16 }} />
      <Title level={5}>下一步建议</Title>
      <List
        size="small"
        bordered
        dataSource={report.recommendations ?? []}
        renderItem={(item) => <List.Item>{item}</List.Item>}
      />
    </Card>
  );
}
