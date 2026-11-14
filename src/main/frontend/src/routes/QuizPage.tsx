import { Text, VStack } from "@seed-design/react";
import { useId, useReducer, useRef } from "react";
import {
  Link,
  useLoaderData,
  useParams,
  useRouteLoaderData,
} from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import { Callout } from "seed-design/ui/callout";
import {
  submitAnswer,
  submitSelfGrade,
  submitTextAnswer,
} from "../api/questions";
import { ApiError } from "../api/users";
import type { UserResponse } from "../api/users";
import type {
  AnswerResponse,
  QuestionResponse,
  TextAnswerResponse,
} from "../api/questions";
import ChoiceList from "../components/ChoiceList";
import SelfGradeButtons from "../components/SelfGradeButtons";
import TextAnswerInput from "../components/TextAnswerInput";
import {
  createInitialState,
  isLastQuestion,
  quizReducer,
} from "../quiz/quizReducer";
import type { SelfGradedQuestionType } from "../quiz/quizReducer";
import { countQuestionsByType, scoreLines } from "../quiz/quizScore";
import type { quizLoader } from "./quizLoader";

// 끝난 뒤(와 빈 화면)에 돌아갈 곳. route마다 다르게 넘긴다 (기본은 카테고리 목록)
export type BackLink = { readonly to: string; readonly label: string };

const CATEGORY_LIST_LINK: BackLink = { to: "/", label: "카테고리 목록" };

export default function QuizPage({
  backLink = CATEGORY_LIST_LINK,
  emptyTitle = "아직 문제가 없습니다",
}: {
  readonly backLink?: BackLink;
  readonly emptyTitle?: string;
}) {
  const questions = useLoaderData<typeof quizLoader>();
  const { slug } = useParams();

  if (questions.length === 0) {
    return (
      <VStack gap="x4" alignItems="flex-start">
        <Text as="h1" textStyle="screenTitle">
          {emptyTitle}
        </Text>
        <BackLinkButton backLink={backLink} />
      </VStack>
    );
  }
  // 같은 route에서는 컴포넌트와 풀이 상태가 재사용된다.
  // key가 바뀌면 React가 새 컴포넌트로 만들므로, 카테고리가 바뀌면 처음부터 시작한다.
  return <QuizSession key={slug} questions={questions} backLink={backLink} />;
}

function QuizSession({
  questions,
  backLink,
}: {
  readonly questions: readonly QuestionResponse[];
  readonly backLink: BackLink;
}) {
  const [state, dispatch] = useReducer(
    quizReducer,
    questions.length,
    createInitialState,
  );
  const questionTitleId = useId();
  // 회원 여부는 앱 맨 위 route(root)의 loader 데이터로 안다. 비회원이면 null이다.
  const user = useRouteLoaderData("root") as UserResponse | null;

  // 저장 중인지 즉시 기록한다. 빠른 연속 클릭은 같은 렌더의 state를 보므로 state만으로는 중복 호출을 못 막는다.
  const savingSelfGrade = useRef(false);

  if (state.phase === "finished") {
    const lines = scoreLines(
      countQuestionsByType(questions),
      state.correctCounts,
    );
    return (
      <VStack gap="x4" alignItems="flex-start">
        <Text as="h1" textStyle="screenTitle">
          풀이 결과
        </Text>
        {lines.map((line) => (
          <Text key={line} as="p" textStyle="t5Medium">
            {line}
          </Text>
        ))}
        {state.loginExpiredAt !== null && (
          <LoginExpiredNotice questionNumber={state.loginExpiredAt + 1} />
        )}
        <BackLinkButton backLink={backLink} />
      </VStack>
    );
  }

  const question = questions[state.currentIndex];
  const { selectedChoiceId, textAnswer } = state;

  async function submitChoice() {
    if (selectedChoiceId === null) return;
    dispatch({ type: "submitStart" });
    try {
      const result = await submitAnswer(question.id, selectedChoiceId);
      dispatch({ type: "submitSuccess", result });
      // 회원인데 저장되지 않았으면 로그인 만료로 본다. 이미 감지했으면 번호를 바꾸지 않는다
      if (user !== null && state.loginExpiredAt === null && !result.recorded) {
        dispatch({ type: "loginExpired" });
      }
    } catch {
      dispatch({ type: "submitFailure" });
    }
  }

  // 회원은 저장이 끝난 뒤에 다음 문제로 넘어간다. 비회원과 로그인이 만료된 퀴즈는 저장 없이 바로 넘어간다.
  async function selfGrade(
    questionType: SelfGradedQuestionType,
    correct: boolean,
  ) {
    if (user === null || state.loginExpiredAt !== null) {
      dispatch({ type: "selfGrade", questionType, correct });
      return;
    }
    if (savingSelfGrade.current) return;
    savingSelfGrade.current = true;
    dispatch({ type: "selfGradeSaveStart" });
    try {
      await submitSelfGrade(question.id, correct);
      dispatch({ type: "selfGrade", questionType, correct });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        dispatch({ type: "selfGradeSaveUnauthorized", questionType, correct });
      } else {
        dispatch({ type: "selfGradeSaveFailure" });
      }
    } finally {
      savingSelfGrade.current = false;
    }
  }

  async function submitText() {
    if (textAnswer.trim() === "") return;
    dispatch({ type: "submitStart" });
    try {
      const reveal = await submitTextAnswer(question.id, textAnswer);
      dispatch({ type: "revealSuccess", reveal });
    } catch {
      dispatch({ type: "submitFailure" });
    }
  }

  // 유형마다 화면과 동작이 달라지는 분기는 이 switch 한 곳에서만 한다.
  // 값만 다르면 Record를 쓴다 (D10).
  const questionType = question.type;
  let answerArea: React.ReactNode;
  let selfGradeArea: React.ReactNode;
  let canSubmit: boolean;
  let handleSubmit: () => Promise<void>;
  switch (questionType) {
    case "MULTIPLE_CHOICE":
      answerArea = (
        <ChoiceList
          labelledBy={questionTitleId}
          choices={question.choices}
          selectedChoiceId={selectedChoiceId}
          correctChoiceId={state.result?.correctChoiceId ?? null}
          disabled={state.phase !== "answering"}
          onSelect={(choiceId) => dispatch({ type: "select", choiceId })}
        />
      );
      selfGradeArea = null; // 객관식은 서버가 채점하므로 자기 채점이 없다
      canSubmit = selectedChoiceId !== null;
      handleSubmit = submitChoice;
      break;
    case "SHORT_ANSWER":
    case "ESSAY":
      answerArea = (
        <TextAnswerInput
          value={textAnswer}
          disabled={state.phase !== "answering"}
          onChange={(text) => dispatch({ type: "inputText", text })}
        />
      );
      selfGradeArea = (
        <SelfGradeButtons
          disabled={state.phase === "savingSelfGrade"}
          onSelect={(correct) => void selfGrade(questionType, correct)}
        />
      );
      canSubmit = textAnswer.trim() !== "";
      handleSubmit = submitText;
      break;
    default:
      // 문제 유형을 추가하고 여기 처리를 빠뜨리면 타입 검사에서 걸린다
      questionType satisfies never;
      return null;
  }

  return (
    <VStack gap="x4">
      <Text as="p" textStyle="t4Medium" color="fg.neutralSubtle">
        문제 {state.currentIndex + 1} / {state.totalQuestions}
      </Text>
      <Text as="h1" id={questionTitleId} textStyle="screenTitle">
        {question.content}
      </Text>
      {answerArea}
      {state.submitFailed && (
        <Callout
          role="alert"
          tone="critical"
          description="채점 요청에 실패했습니다. 다시 제출해 주세요."
        />
      )}
      {state.loginExpiredAt !== null && (
        <LoginExpiredNotice questionNumber={state.loginExpiredAt + 1} />
      )}
      {state.result !== null && <GradingResult result={state.result} />}
      {state.reveal !== null && <ModelAnswer reveal={state.reveal} />}
      {state.selfGradeSaveFailed && (
        <Callout
          role="alert"
          tone="critical"
          description="기록 저장에 실패했습니다. 다시 시도해 주세요."
        />
      )}
      {state.phase === "revealed" || state.phase === "savingSelfGrade" ? (
        selfGradeArea
      ) : state.phase === "graded" ? (
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
          disabled={!canSubmit || state.phase === "submitting"}
          onClick={handleSubmit}
        >
          제출
        </ActionButton>
      )}
    </VStack>
  );
}

function LoginExpiredNotice({
  questionNumber,
}: {
  readonly questionNumber: number;
}) {
  return (
    <Callout
      tone="neutral"
      description={
        "로그인이 만료되어 " +
        questionNumber +
        "번 문제부터 기록이 저장되지 않았어요"
      }
    />
  );
}

function ModelAnswer({ reveal }: { readonly reveal: TextAnswerResponse }) {
  return (
    <VStack gap="x2" alignItems="flex-start">
      <Text as="h2" textStyle="t5Bold">
        모범 답안
      </Text>
      {/* 줄바꿈이 있는 모범 답안을 그대로 보여 준다 */}
      <Text as="p" textStyle="t5Regular" style={{ whiteSpace: "pre-wrap" }}>
        {reveal.modelAnswer}
      </Text>
      {reveal.explanation !== null && (
        <Callout tone="neutral" title="해설" description={reveal.explanation} />
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

function BackLinkButton({ backLink }: { readonly backLink: BackLink }) {
  return (
    <ActionButton asChild variant="neutralSolid">
      <Link to={backLink.to}>{backLink.label}</Link>
    </ActionButton>
  );
}
