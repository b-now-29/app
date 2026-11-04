import { Text, VStack } from "@seed-design/react";
import { useId, useReducer } from "react";
import { Link, useLoaderData, useParams } from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import { Callout } from "seed-design/ui/callout";
import { submitAnswer } from "../api/questions";
import type { AnswerResponse, QuestionResponse } from "../api/questions";
import ChoiceList from "../components/ChoiceList";
import {
  createInitialState,
  isLastQuestion,
  quizReducer,
} from "../quiz/quizReducer";
import type { quizLoader } from "./quizLoader";

export default function QuizPage() {
  const questions = useLoaderData<typeof quizLoader>();
  const { slug } = useParams();

  if (questions.length === 0) {
    return (
      <VStack gap="x4" alignItems="flex-start">
        <Text as="h1" textStyle="screenTitle">
          아직 문제가 없습니다
        </Text>
        <CategoryListLink />
      </VStack>
    );
  }
  // 같은 route에서는 컴포넌트와 풀이 상태가 재사용된다.
  // key가 바뀌면 React가 새 컴포넌트로 만들므로, 카테고리가 바뀌면 처음부터 시작한다.
  return <QuizSession key={slug} questions={questions} />;
}

function QuizSession({
  questions,
}: {
  readonly questions: readonly QuestionResponse[];
}) {
  const [state, dispatch] = useReducer(
    quizReducer,
    questions.length,
    createInitialState,
  );
  const questionTitleId = useId();

  if (state.phase === "finished") {
    return (
      <VStack gap="x4" alignItems="flex-start">
        <Text as="h1" textStyle="screenTitle">
          {state.totalQuestions}문제 중 {state.correctCount}문제 정답
        </Text>
        <CategoryListLink />
      </VStack>
    );
  }

  const question = questions[state.currentIndex];
  const { selectedChoiceId } = state;

  async function handleSubmit() {
    if (selectedChoiceId === null) return;
    dispatch({ type: "submitStart" });
    try {
      const result = await submitAnswer(question.id, selectedChoiceId);
      dispatch({ type: "submitSuccess", result });
    } catch {
      dispatch({ type: "submitFailure" });
    }
  }

  return (
    <VStack gap="x4">
      <Text as="p" textStyle="t4Medium" color="fg.neutralSubtle">
        문제 {state.currentIndex + 1} / {state.totalQuestions}
      </Text>
      <Text as="h1" id={questionTitleId} textStyle="screenTitle">
        {question.content}
      </Text>
      <ChoiceList
        labelledBy={questionTitleId}
        choices={question.choices}
        selectedChoiceId={selectedChoiceId}
        correctChoiceId={state.result?.correctChoiceId ?? null}
        disabled={state.phase !== "answering"}
        onSelect={(choiceId) => dispatch({ type: "select", choiceId })}
      />
      {state.submitFailed && (
        <Callout
          role="alert"
          tone="critical"
          description="채점 요청에 실패했습니다. 다시 제출해 주세요."
        />
      )}
      {state.result !== null && <GradingResult result={state.result} />}
      {state.phase === "graded" ? (
        <ActionButton
          variant="neutralSolid"
          onClick={(event) => {
            // 제출을 더블클릭하면 두 번째 클릭이 같은 자리의 이 버튼에 걸려 결과를 못 보고 넘어간다.
            // detail은 연속 클릭 횟수이고 키보드 입력은 0이라 키보드 조작은 막히지 않는다.
            if (event.detail > 1) return;
            dispatch({ type: "next" });
          }}
        >
          {isLastQuestion(state) ? "결과 보기" : "다음 문제"}
        </ActionButton>
      ) : (
        <ActionButton
          variant="neutralSolid"
          loading={state.phase === "submitting"}
          // SEED 버튼의 loading은 모양만 바꾸고 클릭을 막지 않으므로 disabled도 같이 준다
          disabled={selectedChoiceId === null || state.phase === "submitting"}
          onClick={handleSubmit}
        >
          제출
        </ActionButton>
      )}
    </VStack>
  );
}

function GradingResult({ result }: { readonly result: AnswerResponse }) {
  const label = result.correct ? "정답입니다" : "오답입니다";
  return (
    <Callout
      role="status"
      tone={result.correct ? "positive" : "critical"}
      // 해설이 없으면 제목 없이 정답 여부만 본문으로 보여 준다 (본문은 비워 둘 수 없다)
      title={result.explanation === null ? undefined : label}
      description={result.explanation ?? label}
    />
  );
}

function CategoryListLink() {
  return (
    <ActionButton asChild variant="neutralSolid">
      <Link to="/">카테고리 목록</Link>
    </ActionButton>
  );
}
