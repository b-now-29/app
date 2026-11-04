import type { AnswerResponse } from "../api/questions";

export type QuizPhase = "answering" | "submitting" | "graded" | "finished";

export type QuizState = {
  readonly phase: QuizPhase;
  readonly currentIndex: number;
  readonly totalQuestions: number;
  readonly selectedChoiceId: number | null;
  readonly result: AnswerResponse | null;
  readonly correctCount: number;
  readonly submitFailed: boolean;
};

export type QuizAction =
  | { readonly type: "select"; readonly choiceId: number }
  | { readonly type: "submitStart" }
  | { readonly type: "submitSuccess"; readonly result: AnswerResponse }
  | { readonly type: "submitFailure" }
  | { readonly type: "next" };

export function createInitialState(totalQuestions: number): QuizState {
  return {
    phase: "answering",
    currentIndex: 0,
    totalQuestions,
    selectedChoiceId: null,
    result: null,
    correctCount: 0,
    submitFailed: false,
  };
}

export function isLastQuestion(state: QuizState): boolean {
  return state.currentIndex + 1 >= state.totalQuestions;
}

// 흐름에 없는 전이는 예외 없이 state를 그대로 돌려준다.
// 화면 이벤트가 늦게 도착해도 풀이가 깨지지 않게 하기 위해서다.
export function quizReducer(state: QuizState, action: QuizAction): QuizState {
  switch (action.type) {
    case "select":
      if (state.phase !== "answering") return state;
      return { ...state, selectedChoiceId: action.choiceId };

    case "submitStart":
      if (state.phase !== "answering" || state.selectedChoiceId === null) {
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

    case "submitFailure":
      if (state.phase !== "submitting") return state;
      return { ...state, phase: "answering", submitFailed: true };

    case "next":
      if (state.phase !== "graded") return state;
      if (isLastQuestion(state)) return { ...state, phase: "finished" };
      return {
        ...state,
        phase: "answering",
        currentIndex: state.currentIndex + 1,
        selectedChoiceId: null,
        result: null,
      };

    default:
      // 액션을 추가하고 여기 처리를 빠뜨리면 타입 검사에서 걸린다
      action satisfies never;
      return state;
  }
}
