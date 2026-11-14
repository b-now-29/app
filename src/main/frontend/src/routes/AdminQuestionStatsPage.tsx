import { Text, VStack } from "@seed-design/react";
import {
  Link,
  useLoaderData,
  useParams,
  useRouteLoaderData,
} from "react-router";
import type {
  AdminCategoryResponse,
  AdminQuestionStatResponse,
} from "../api/admin";
import { questionStatLine } from "../quiz/adminStats";
import { QUESTION_TYPE_NAMES } from "../quiz/questionType";

export default function AdminQuestionStatsPage() {
  const questions = useLoaderData() as readonly AdminQuestionStatResponse[];
  const categories = useRouteLoaderData("admin") as
    readonly AdminCategoryResponse[] | undefined;
  const { slug } = useParams();
  const title = categories?.find((c) => c.slug === slug)?.name ?? slug;

  return (
    <VStack gap="x4">
      <Link to="/admin/stats">통계 목록</Link>
      <Text as="h1" textStyle="screenTitle">
        {title}
      </Text>
      <ul aria-label="문제 통계">
        {questions.map((question) => (
          <li key={question.id}>
            <VStack gap="x1">
              <Text textStyle="t3Regular">
                {QUESTION_TYPE_NAMES[question.type]}
              </Text>
              <Text textStyle="t4Bold">{question.content}</Text>
              <Text textStyle="t3Regular">{questionStatLine(question)}</Text>
            </VStack>
          </li>
        ))}
      </ul>
    </VStack>
  );
}
