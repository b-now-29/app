import type {
  AnswerResponse,
  QuestionType,
  TextAnswerResponse,
} from "../api/questions";

export type QuizPhase =
  | "answering"
  | "submitting"
  | "graded"
  | "revealed"
  | "savingSelfGrade"
  | "finished";

export type QuizState = {
  readonly phase: QuizPhase;
  readonly currentIndex: number;
  readonly totalQuestions: number;
  readonly selectedChoiceId: number | null;
  readonly textAnswer: string;
  readonly result: AnswerResponse | null;
  readonly reveal: TextAnswerResponse | null;
  readonly correctCounts: Readonly<Record<QuestionType, number>>;
  readonly submitFailed: boolean;
  // 자기 채점 저장이 실패해 다시 시도할 수 있는 상태 (D9)
  readonly selfGradeSaveFailed: boolean;
  // 로그인 만료를 처음 감지한 문제의 0부터 시작하는 index. null이면 만료 전이다.
  // 값이 있으면 이 퀴즈의 남은 문제는 저장하지 않으며, 문제가 바뀌어도 유지된다 (D12, D13)
  readonly loginExpiredAt: number | null;
};

// 객관식은 서버가 채점하므로 자기 채점 액션으로 받지 않는다
export type SelfGradedQuestionType = Exclude<QuestionType, "MULTIPLE_CHOICE">;

export type QuizAction =
  | { readonly type: "select"; readonly choiceId: number }
  | { readonly type: "inputText"; readonly text: string }
  | { readonly type: "submitStart" }
  | { readonly type: "submitSuccess"; readonly result: AnswerResponse }
  | {
      readonly type: "revealSuccess";
      readonly reveal: TextAnswerResponse;
    }
  | {
      readonly type: "selfGrade";
      readonly questionType: SelfGradedQuestionType;
      readonly correct: boolean;
    }
  | { readonly type: "selfGradeSaveStart" }
  | { readonly type: "selfGradeSaveFailure" }
  | {
      // 저장이 401이면 고른 결과를 점수에 넣고 바로 다음 문제로 넘어간다
      readonly type: "selfGradeSaveUnauthorized";
      readonly questionType: SelfGradedQuestionType;
      readonly correct: boolean;
    }
  | {
      // 회원인데 객관식 제출 응답이 recorded: false라서 로그인 만료로 본다 (D13)
      readonly type: "loginExpired";
    }
  | { readonly type: "submitFailure" }
  | { readonly type: "next" };

export function createInitialState(totalQuestions: number): QuizState {
  return {
    phase: "answering",
    currentIndex: 0,
    totalQuestions,
    selectedChoiceId: null,
    textAnswer: "",
    result: null,
    reveal: null,
    correctCounts: { MULTIPLE_CHOICE: 0, SHORT_ANSWER: 0, ESSAY: 0 },
    submitFailed: false,
    selfGradeSaveFailed: false,
    loginExpiredAt: null,
  };
}

export function isLastQuestion(state: QuizState): boolean {
  return state.currentIndex + 1 >= state.totalQuestions;
}

// 다음 문제로 넘어간다. 마지막이면 끝 단계로 가고, 아니면 이전 문제의 입력과 결과를 비운다.
function advance(state: QuizState): QuizState {
  if (isLastQuestion(state)) {
    return { ...state, phase: "finished", selfGradeSaveFailed: false };
  }
  return {
    ...state,
    phase: "answering",
    selfGradeSaveFailed: false,
    currentIndex: state.currentIndex + 1,
    selectedChoiceId: null,
    textAnswer: "",
    result: null,
    reveal: null,
  };
}

// 맞은 문제를 그 유형의 칸에 하나 더한다. 틀렸으면 그대로 둔다.
function addCorrect(
  correctCounts: QuizState["correctCounts"],
  questionType: QuestionType,
  correct: boolean,
): QuizState["correctCounts"] {
  if (!correct) return correctCounts;
  return { ...correctCounts, [questionType]: correctCounts[questionType] + 1 };
}

// 흐름에 없는 전이는 예외 없이 state를 그대로 돌려준다.
// 화면 이벤트가 늦게 도착해도 풀이가 깨지지 않게 하기 위해서다.
export function quizReducer(state: QuizState, action: QuizAction): QuizState {
  switch (action.type) {
    case "select":
      if (state.phase !== "answering") return state;
      return { ...state, selectedChoiceId: action.choiceId };

    case "inputText":
      if (state.phase !== "answering") return state;
      return { ...state, textAnswer: action.text };

    case "submitStart":
      // 화면이 문제 유형에 맞는 입력만 받으므로, 고른 보기나 공백 아닌 답 중 하나만 있으면 제출할 수 있다
      if (
        state.phase !== "answering" ||
        (state.selectedChoiceId === null && state.textAnswer.trim() === "")
      ) {
        return state;
      }
      return { ...state, phase: "submitting", submitFailed: false };

    case "submitSuccess":
      if (state.phase !== "submitting") return state;
      return {
        ...state,
        phase: "graded",
        result: action.result,
        // 서버 채점 결과는 객관식에서만 온다
        correctCounts: addCorrect(
          state.correctCounts,
          "MULTIPLE_CHOICE",
          action.result.correct,
        ),
      };

    case "revealSuccess":
      if (state.phase !== "submitting") return state;
      return { ...state, phase: "revealed", reveal: action.reveal };

    case "selfGradeSaveStart":
      // 로그인이 만료된 뒤에는 저장하지 않는다. 저장 중에 또 받으면 무시해 중복 저장을 막는다
      if (state.phase !== "revealed" || state.loginExpiredAt !== null)
        return state;
      return { ...state, phase: "savingSelfGrade", selfGradeSaveFailed: false };

    case "selfGrade":
      // 비회원·로그인 만료는 revealed에서 바로, 회원은 저장이 끝난 savingSelfGrade에서 받는다
      if (state.phase !== "revealed" && state.phase !== "savingSelfGrade") {
        return state;
      }
      return advance({
        ...state,
        correctCounts: addCorrect(
          state.correctCounts,
          action.questionType,
          action.correct,
        ),
      });

    case "selfGradeSaveFailure":
      if (state.phase !== "savingSelfGrade") return state;
      return { ...state, phase: "revealed", selfGradeSaveFailed: true };

    case "selfGradeSaveUnauthorized":
      if (state.phase !== "savingSelfGrade") return state;
      return advance({
        ...state,
        // 이미 감지한 번호가 있으면 유지한다. advance 전이라 currentIndex가 401이 난 문제다
        loginExpiredAt: state.loginExpiredAt ?? state.currentIndex,
        correctCounts: addCorrect(
          state.correctCounts,
          action.questionType,
          action.correct,
        ),
      });

    case "loginExpired":
      if (state.phase !== "graded") return state;
      // 처음 감지한 번호를 유지한다
      return {
        ...state,
        loginExpiredAt: state.loginExpiredAt ?? state.currentIndex,
      };

    case "submitFailure":
      if (state.phase !== "submitting") return state;
      return { ...state, phase: "answering", submitFailed: true };

    case "next":
      if (state.phase !== "graded") return state;
      return advance(state);

    default:
      // 액션을 추가하고 여기 처리를 빠뜨리면 타입 검사에서 걸린다
      action satisfies never;
      return state;
  }
}
