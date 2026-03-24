"use client";
import {
  Button,
  Card,
  List,
  Skeleton,
  Typography,
  message,
} from "antd";
import Title from "antd/es/typography/Title";
import Paragraph from "antd/es/typography/Paragraph";
import TagList from "@/components/TagList";
import MdViewer from "@/components/MdViewer";
import useAddUserSignInRecord from "@/hooks/useAddUserSignInRecord";
import useAddQuestionRecord from "@/hooks/useAddQuestionRecord";
import {
  LeftOutlined,
  RightOutlined,
} from "@ant-design/icons";
import {
  recommendQuestionsUsingPost,
  getQuestionExplainUsingPost,
  getQuestionHintUsingPost,
} from "@/api/aiController";
import { addQuestionRecordUsingPost } from "@/api/questionRecordController";
import { useState } from "react";
import QuestionList from "@/components/QuestionList";
import "./index.css";

interface Props {
  question: API.QuestionVO;
  questionBankId?: number;
}

type AiViewMode = "explain" | "hint" | "recommend" | null;

/**
 * 题目卡片
 * @param props
 * @constructor
 */
const QuestionCard = (props: Props) => {
  const { question, questionBankId } = props;

  // 签到
  useAddUserSignInRecord();
  // 记录题目浏览
  useAddQuestionRecord(question.id, questionBankId);

  const [aiExplain, setAiExplain] = useState<API.AiQuestionExplainVO>();
  const [aiHint, setAiHint] = useState<API.AiQuestionHintVO>();
  const [recommendation, setRecommendation] =
    useState<API.AiQuestionRecommendVO>();
  const [activeAiView, setActiveAiView] = useState<AiViewMode>(null);
  const [currentHintLevel, setCurrentHintLevel] = useState<number>(0);
  const [explainLoading, setExplainLoading] = useState<boolean>(false);
  const [hintLoading, setHintLoading] = useState<boolean>(false);
  const [recommendLoading, setRecommendLoading] = useState<boolean>(false);
  const [markingIncorrect, setMarkingIncorrect] = useState<boolean>(false);

  const addAiRecord = async (actionType: string) => {
    if (!question.id) {
      return;
    }
    try {
      await addQuestionRecordUsingPost({
        questionId: question.id,
        questionBankId,
        actionType,
        usedAi: 1,
      });
    } catch (e) {
      // 行为记录失败不影响主流程
    }
  };

  const fetchExplain = async () => {
    if (!question.id) {
      return;
    }
    if (aiExplain) {
      setActiveAiView("explain");
      return;
    }
    setExplainLoading(true);
    try {
      const res = await getQuestionExplainUsingPost({
        questionId: question.id,
      });
      setAiExplain(res.data);
      setActiveAiView("explain");
      await addAiRecord("use_ai_explain");
    } catch (e) {
      message.error("获取 AI 讲解失败，" + (e as Error).message);
    }
    setExplainLoading(false);
  };

  const fetchHint = async (level: number) => {
    if (!question.id) {
      return;
    }
    if (aiHint && currentHintLevel === level) {
      setActiveAiView("hint");
      return;
    }
    setHintLoading(true);
    try {
      const res = await getQuestionHintUsingPost({
        questionId: question.id,
        level,
      });
      setAiHint(res.data);
      setCurrentHintLevel(level);
      setActiveAiView("hint");
      await addAiRecord("use_ai_hint");
    } catch (e) {
      message.error("获取 AI 提示失败，" + (e as Error).message);
    }
    setHintLoading(false);
  };

  const openHint = async () => {
    const nextLevel = currentHintLevel > 0 ? currentHintLevel : 1;
    await fetchHint(nextLevel);
  };

  const switchHint = async (delta: number) => {
    if (!aiHint?.totalLevels) {
      return;
    }
    const nextLevel = currentHintLevel + delta;
    if (nextLevel < 1 || nextLevel > aiHint.totalLevels) {
      return;
    }
    await fetchHint(nextLevel);
  };

  const fetchRecommendations = async () => {
    if (!question.id) {
      return;
    }
    if (recommendation?.questions?.length) {
      setActiveAiView("recommend");
      return;
    }
    setRecommendLoading(true);
    try {
      const res = await recommendQuestionsUsingPost({
        questionId: question.id,
      });
      setRecommendation(res.data);
      setActiveAiView("recommend");
    } catch (e) {
      message.error("获取推荐题目失败，" + (e as Error).message);
    }
    setRecommendLoading(false);
  };

  const markIncorrect = async () => {
    if (!question.id) {
      return;
    }
    setMarkingIncorrect(true);
    try {
      await addQuestionRecordUsingPost({
        questionId: question.id,
        questionBankId,
        actionType: "incorrect",
        isCorrect: 0,
      });
      message.success("已标记为当前薄弱题");
    } catch (e) {
      message.error("标记失败，" + (e as Error).message);
    }
    setMarkingIncorrect(false);
  };

  return (
    <div className="question-card">
      <Card>
        <Title level={1} style={{ fontSize: 24 }}>
          {question.title}
        </Title>
        <TagList tagList={question.tagList} />
        <div style={{ marginBottom: 16 }} />
        <MdViewer value={question.content} />
      </Card>
      <div style={{ marginBottom: 16 }} />
      <Card title="AI 助学">
        <div className="ai-mode-list">
          <Button
            type="default"
            className={`ai-mode-button ${
              activeAiView === "explain" ? "ai-mode-button-active" : ""
            }`}
            onClick={fetchExplain}
            loading={explainLoading}
          >
            帮我理解
          </Button>
          <Button
            type="default"
            className={`ai-mode-button ${
              activeAiView === "hint" ? "ai-mode-button-active" : ""
            }`}
            onClick={openHint}
            loading={hintLoading}
          >
            查看提示
          </Button>
          <Button
            type="default"
            className="ai-mode-button"
            onClick={markIncorrect}
            loading={markingIncorrect}
          >
            标记未掌握
          </Button>
          <Button
            type="default"
            className={`ai-mode-button ${
              activeAiView === "recommend" ? "ai-mode-button-active" : ""
            }`}
            onClick={fetchRecommendations}
            loading={recommendLoading}
          >
            推荐下一题
          </Button>
        </div>
        <div style={{ marginBottom: 16 }} />
        {(explainLoading || hintLoading) && <Skeleton active paragraph={{ rows: 4 }} />}
        {!explainLoading && activeAiView === "explain" && aiExplain && (
          <div>
            <Paragraph>{aiExplain.plainExplanation}</Paragraph>
            <Typography.Text strong>核心考点</Typography.Text>
            <List
              size="small"
              dataSource={aiExplain.keyPoints ?? []}
              renderItem={(item) => <List.Item>{item}</List.Item>}
            />
            <Typography.Text strong>常见误区</Typography.Text>
            <List
              size="small"
              dataSource={aiExplain.pitfalls ?? []}
              renderItem={(item) => <List.Item>{item}</List.Item>}
            />
            <Typography.Text strong>模拟追问</Typography.Text>
            <List
              size="small"
              dataSource={aiExplain.followUpQuestions ?? []}
              renderItem={(item) => <List.Item>{item}</List.Item>}
            />
          </div>
        )}
        {!hintLoading && activeAiView === "hint" && aiHint && (
          <Card
            type="inner"
            title={
              <div className="hint-header">
                <Typography.Text strong>{`提示 ${aiHint.level}/${aiHint.totalLevels}`}</Typography.Text>
                <div className="hint-nav">
                  <Button
                    type="text"
                    shape="circle"
                    className="hint-nav-button"
                    icon={<LeftOutlined />}
                    disabled={!aiHint.level || aiHint.level <= 1}
                    onClick={() => switchHint(-1)}
                  />
                  <Button
                    type="text"
                    shape="circle"
                    className="hint-nav-button"
                    icon={<RightOutlined />}
                    disabled={
                      !aiHint.level ||
                      !aiHint.totalLevels ||
                      aiHint.level >= aiHint.totalLevels
                    }
                    onClick={() => switchHint(1)}
                  />
                </div>
              </div>
            }
            className="hint-panel"
          >
            {aiHint.hintContent}
          </Card>
        )}
        {!recommendLoading &&
        activeAiView === "recommend" &&
        recommendation?.questions?.length ? (
          <div style={{ marginTop: 16 }}>
            <Paragraph>{recommendation.recommendationReason}</Paragraph>
            <QuestionList
              questionBankId={questionBankId}
              cardTitle="下一步推荐"
              questionList={recommendation.questions}
            />
          </div>
        ) : null}
      </Card>
      <div style={{ marginBottom: 16 }} />
      <Card title="推荐答案">
        <MdViewer value={question.answer} />
      </Card>
    </div>
  );
};

export default QuestionCard;
