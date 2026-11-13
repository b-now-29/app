import { Text, VStack } from "@seed-design/react";
import { Link, useLoaderData } from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import type { WrongAnswerQuestionResponse } from "../api/wrongAnswers";
import {
  wrongAnswerCategoryTitle,
  wrongAnswerQuestionMeta,
} from "../quiz/wrongAnswers";
import type { wrongAnswersLoader } from "./wrongAnswerLoaders";

export default function WrongAnswersPage() {
  const categories = useLoaderData<typeof wrongAnswersLoader>();

  return (
    <VStack gap="x4" alignItems="stretch">
      <Text as="h1" textStyle="screenTitle">
        오답 노트
      </Text>
      {categories.length === 0 ? (
        <Text as="p" textStyle="t5Regular">
          지금 오답이 없어요. 틀린 문제가 생기면 여기에 모여요.
        </Text>
      ) : (
        categories.map((category) => (
          <VStack
            key={category.slug}
            as="section"
            gap="x2"
            alignItems="stretch"
          >
            <Text as="h2" textStyle="t5Bold">
              {wrongAnswerCategoryTitle(category)}
            </Text>
            <ul style={listStyle}>
              {category.questions.map((question) => (
                <QuestionItem key={question.questionId} question={question} />
              ))}
            </ul>
            <ActionButton asChild variant="neutralSolid">
              <Link to={`/wrong-answers/${encodeURIComponent(category.slug)}`}>
                다시 풀기
              </Link>
            </ActionButton>
          </VStack>
        ))
      )}
    </VStack>
  );
}

const listStyle = {
  listStyle: "none",
  margin: 0,
  padding: 0,
  display: "flex",
  flexDirection: "column",
  gap: "16px",
} as const;

function QuestionItem({
  question,
}: {
  readonly question: WrongAnswerQuestionResponse;
}) {
  return (
    <li>
      <Text as="p" textStyle="t5Regular">
        {question.content}
      </Text>
      <Text as="p" textStyle="t4Regular" color="fg.neutralSubtle">
        {wrongAnswerQuestionMeta(question)}
      </Text>
    </li>
  );
}
