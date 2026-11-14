import { Text, VStack } from "@seed-design/react";
import { useLoaderData } from "react-router";
import type { AttemptResponse, CategoryStatResponse } from "../api/attempts";
import { accuracyLine } from "../quiz/accuracy";
import { formatDate } from "../quiz/date";
import { QUESTION_TYPE_NAMES } from "../quiz/quizScore";
import type { historyLoader } from "./historyLoader";

export default function HistoryPage() {
  const { categoryStats, attempts } = useLoaderData<typeof historyLoader>();

  return (
    <VStack gap="x4" alignItems="stretch">
      <Text as="h1" textStyle="screenTitle">
        내 기록
      </Text>
      {categoryStats.length === 0 && attempts.length === 0 ? (
        <Text as="p" textStyle="t5Regular">
          아직 풀이 기록이 없어요. 문제를 풀면 여기에 쌓여요.
        </Text>
      ) : (
        <>
          {categoryStats.length > 0 && (
            <VStack as="section" gap="x2" alignItems="stretch">
              <Text as="h2" textStyle="t5Bold">
                카테고리별 정답률
              </Text>
              <ul aria-label="정답률" style={listStyle}>
                {categoryStats.map((stat) => (
                  <CategoryStatItem key={stat.slug} stat={stat} />
                ))}
              </ul>
            </VStack>
          )}
          {attempts.length > 0 && (
            <VStack as="section" gap="x2" alignItems="stretch">
              <Text as="h2" textStyle="t5Bold">
                최근 풀이
              </Text>
              <ul aria-label="최근 풀이" style={listStyle}>
                {attempts.map((attempt) => (
                  <AttemptItem key={attempt.id} attempt={attempt} />
                ))}
              </ul>
            </VStack>
          )}
        </>
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

function CategoryStatItem({ stat }: { readonly stat: CategoryStatResponse }) {
  return (
    <li>
      <Text as="p" textStyle="t5Medium">
        {stat.name}
      </Text>
      {/* 유형마다 따로 보여 주고 카테고리 합계는 내지 않는다 (D11) */}
      {stat.types.map((typeStat) => (
        <Text
          key={typeStat.type}
          as="p"
          textStyle="t4Regular"
          color="fg.neutralSubtle"
        >
          {accuracyLine(typeStat)}
        </Text>
      ))}
    </li>
  );
}

function AttemptItem({ attempt }: { readonly attempt: AttemptResponse }) {
  const date = formatDate(attempt.attemptedAt);
  return (
    <li>
      <Text as="p" textStyle="t5Regular">
        {attempt.questionContent}
      </Text>
      <Text as="p" textStyle="t4Regular" color="fg.neutralSubtle">
        {`${attempt.categoryName} · ${QUESTION_TYPE_NAMES[attempt.questionType]} · ${attempt.correct ? "맞음" : "틀림"} · ${date}`}
      </Text>
    </li>
  );
}
