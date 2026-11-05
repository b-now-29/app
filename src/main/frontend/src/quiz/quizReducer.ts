import type { AnswerResponse, TextAnswerResponse } from "../api/questions";

export type QuizPhase =
  "answering" | "submitting" | "graded" | "revealed" | "finished";

export type QuizState = {
  readonly phase: QuizPhase;
  readonly currentIndex: number;
  readonly totalQuestions: number;
  readonly selectedChoiceId: number | null;
  readonly textAnswer: string;
  readonly result: AnswerResponse | null;
  readonly reveal: TextAnswerResponse | null;
  readonly correctCount: number;
  readonly selfGradeCorrectCount: number;
  readonly submitFailed: boolean;
};

export type QuizAction =
  | { readonly type: "select"; readonly choiceId: number }
  | { readonly type: "inputText"; readonly text: string }
  | { readonly type: "submitStart" }
  | { readonly type: "submitSuccess"; readonly result: AnswerResponse }
  | {
      readonly type: "revealSuccess";
      readonly reveal: TextAnswerResponse;
    }
  | { readonly type: "selfGrade"; readonly correct: boolean }
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
    correctCount: 0,
    selfGradeCorrectCount: 0,
    submitFailed: false,
  };
}

export function isLastQuestion(state: QuizState): boolean {
  return state.currentIndex + 1 >= state.totalQuestions;
}

// 다음 문제로 넘어간다. 마지막이면 끝 단계로 가고, 아니면 이전 문제의 입력과 결과를 비운다.
function advance(state: QuizState): QuizState {
  if (isLastQuestion(state)) return { ...state, phase: "finished" };
  return {
    ...state,
    phase: "answering",
    currentIndex: state.currentIndex + 1,
    selectedChoiceId: null,
    textAnswer: "",
    result: null,
    reveal: null,
  };
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
        correctCount: state.correctCount + (action.result.correct ? 1 : 0),
      };

    case "revealSuccess":
      if (state.phase !== "submitting") return state;
      return { ...state, phase: "revealed", reveal: action.reveal };

    case "selfGrade":
      if (state.phase !== "revealed") return state;
      return advance({
        ...state,
        selfGradeCorrectCount:
          state.selfGradeCorrectCount + (action.correct ? 1 : 0),
      });

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
