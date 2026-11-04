import { describe, expect, it } from "vitest";
import type { AnswerResponse } from "../api/questions";
import { createInitialState, isLastQuestion, quizReducer } from "./quizReducer";
import type { QuizAction, QuizPhase, QuizState } from "./quizReducer";

const CHOICE_ID = 10;
const OTHER_CHOICE_ID = 20;

const CORRECT_RESULT: AnswerResponse = {
  correct: true,
  correctChoiceId: CHOICE_ID,
  explanation: "해설",
};
const WRONG_RESULT: AnswerResponse = {
  correct: false,
  correctChoiceId: OTHER_CHOICE_ID,
  explanation: null,
};

const SELECT: QuizAction = { type: "select", choiceId: CHOICE_ID };
const SELECT_OTHER: QuizAction = { type: "select", choiceId: OTHER_CHOICE_ID };
const SUBMIT_START: QuizAction = { type: "submitStart" };
const SUBMIT_CORRECT: QuizAction = {
  type: "submitSuccess",
  result: CORRECT_RESULT,
};
const SUBMIT_WRONG: QuizAction = {
  type: "submitSuccess",
  result: WRONG_RESULT,
};
const SUBMIT_FAILURE: QuizAction = { type: "submitFailure" };
const NEXT: QuizAction = { type: "next" };

// 얼린 상태를 넘겨서, reducer가 상태를 직접 수정하면 TypeError로 드러나게 한다
function run(state: QuizState, ...actions: readonly QuizAction[]): QuizState {
  return actions.reduce(
    (current, action) => quizReducer(Object.freeze(current), action),
    state,
  );
}

const noChoice = () => createInitialState(3);
const chosen = () => run(noChoice(), SELECT);
const submitting = () => run(chosen(), SUBMIT_START);
const failed = () => run(submitting(), SUBMIT_FAILURE);
const graded = () => run(submitting(), SUBMIT_CORRECT);
const finished = () =>
  run(createInitialState(1), SELECT, SUBMIT_START, SUBMIT_CORRECT, NEXT);

describe("quizReducer", () => {
  describe("초기 상태", () => {
    it("첫 문제에서 보기를 고르지 않고 0점으로 시작한다", () => {
      expect(createInitialState(3)).toEqual({
        phase: "answering",
        currentIndex: 0,
        totalQuestions: 3,
        selectedChoiceId: null,
        result: null,
        correctCount: 0,
        submitFailed: false,
      });
    });
  });

  describe("select", () => {
    it("답을 고르는 단계에서 고른 보기를 기록한다", () => {
      expect(run(noChoice(), SELECT)).toEqual({
        ...noChoice(),
        selectedChoiceId: CHOICE_ID,
      });
    });

    it("제출하기 전에는 다른 보기로 바꿀 수 있다", () => {
      expect(run(chosen(), SELECT_OTHER)).toEqual({
        ...chosen(),
        selectedChoiceId: OTHER_CHOICE_ID,
      });
    });
  });

  describe("submitStart", () => {
    it("보기를 고른 뒤에 제출하면 제출 중 단계로 가고 고른 보기를 유지한다", () => {
      expect(run(chosen(), SUBMIT_START)).toEqual({
        ...chosen(),
        phase: "submitting",
      });
    });
  });

  describe("submitSuccess", () => {
    it("정답이면 채점 결과를 기록하고 정답 수를 1 늘린다", () => {
      expect(run(submitting(), SUBMIT_CORRECT)).toEqual({
        ...submitting(),
        phase: "graded",
        result: CORRECT_RESULT,
        correctCount: 1,
      });
    });

    it("오답이면 채점 결과를 기록하고 정답 수를 늘리지 않는다", () => {
      expect(run(submitting(), SUBMIT_WRONG)).toEqual({
        ...submitting(),
        phase: "graded",
        result: WRONG_RESULT,
        correctCount: 0,
      });
    });
  });

  describe("submitFailure (R7)", () => {
    it("답을 고르는 단계로 돌아가 고른 보기를 유지하고 실패를 표시한다", () => {
      expect(run(submitting(), SUBMIT_FAILURE)).toEqual({
        ...submitting(),
        phase: "answering",
        submitFailed: true,
      });
    });

    it("실패한 뒤 다시 제출하면 실패 표시를 지우고 제출 중 단계로 간다", () => {
      expect(run(failed(), SUBMIT_START)).toEqual({
        ...failed(),
        phase: "submitting",
        submitFailed: false,
      });
    });

    it("다시 제출해 성공하면 실패 표시 없이 채점 결과를 기록한다", () => {
      expect(run(failed(), SUBMIT_START, SUBMIT_CORRECT)).toEqual({
        ...submitting(),
        phase: "graded",
        result: CORRECT_RESULT,
        correctCount: 1,
        submitFailed: false,
      });
    });

    it("다시 제출해도 또 실패하면 다시 실패를 표시한다", () => {
      expect(run(failed(), SUBMIT_START, SUBMIT_FAILURE)).toEqual(failed());
    });

    it("실패한 뒤에는 아직 채점 전이므로 다른 보기로 바꿀 수 있다", () => {
      expect(run(failed(), SELECT_OTHER)).toMatchObject({
        phase: "answering",
        selectedChoiceId: OTHER_CHOICE_ID,
      });
    });
  });

  describe("next", () => {
    it("마지막 문제가 아니면 다음 문제로 가서 보기와 결과를 비우고 정답 수를 유지한다", () => {
      expect(run(graded(), NEXT)).toEqual({
        ...graded(),
        phase: "answering",
        currentIndex: 1,
        selectedChoiceId: null,
        result: null,
        correctCount: 1,
      });
    });

    it("마지막 문제면 끝 단계로 가고 점수를 유지한다 (R6)", () => {
      const lastGraded = run(
        createInitialState(2),
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
        SELECT,
        SUBMIT_START,
        SUBMIT_WRONG,
      );

      expect(run(lastGraded, NEXT)).toMatchObject({
        phase: "finished",
        totalQuestions: 2,
        correctCount: 1,
      });
    });

    it("문제가 하나뿐이면 첫 다음 동작에서 끝 단계로 간다", () => {
      expect(finished()).toMatchObject({
        phase: "finished",
        totalQuestions: 1,
        correctCount: 1,
      });
    });
  });

  describe("isLastQuestion (D17)", () => {
    const answerAndNext: readonly QuizAction[] = [
      SELECT,
      SUBMIT_START,
      SUBMIT_CORRECT,
      NEXT,
    ];

    it("문제가 하나뿐이면 첫 문제가 마지막이다", () => {
      expect(isLastQuestion(createInitialState(1))).toBe(true);
    });

    it("첫 문제 뒤에 문제가 남아 있으면 마지막이 아니다", () => {
      expect(isLastQuestion(createInitialState(3))).toBe(false);
    });

    it("마지막 바로 앞 문제는 마지막이 아니다", () => {
      const state = run(createInitialState(3), ...answerAndNext);

      expect(state.currentIndex).toBe(1);
      expect(isLastQuestion(state)).toBe(false);
    });

    it("마지막 문제로 넘어오면 마지막이다", () => {
      const state = run(
        createInitialState(3),
        ...answerAndNext,
        ...answerAndNext,
      );

      expect(state.currentIndex).toBe(2);
      expect(isLastQuestion(state)).toBe(true);
    });

    it("채점 뒤에도 현재 문제 기준으로 판단한다", () => {
      expect(isLastQuestion(graded())).toBe(false);
      expect(
        isLastQuestion(
          run(createInitialState(1), SELECT, SUBMIT_START, SUBMIT_CORRECT),
        ),
      ).toBe(true);
    });
  });

  describe("흐름에 없는 전이는 상태를 바꾸지 않는다", () => {
    type IgnoredCase = {
      readonly name: string;
      readonly phase: QuizPhase;
      readonly from: () => QuizState;
      readonly action: QuizAction;
    };

    const cases: readonly IgnoredCase[] = [
      {
        name: "보기를 고르기 전에는 제출할 수 없다 (R4)",
        phase: "answering",
        from: noChoice,
        action: SUBMIT_START,
      },
      {
        name: "보기를 고르기 전의 성공 응답은 무시한다",
        phase: "answering",
        from: noChoice,
        action: SUBMIT_CORRECT,
      },
      {
        name: "보기를 고르기 전의 실패 응답은 무시한다",
        phase: "answering",
        from: noChoice,
        action: SUBMIT_FAILURE,
      },
      {
        name: "보기를 고르기 전에는 다음으로 갈 수 없다",
        phase: "answering",
        from: noChoice,
        action: NEXT,
      },
      {
        name: "제출하지 않았을 때의 성공 응답은 무시한다",
        phase: "answering",
        from: chosen,
        action: SUBMIT_CORRECT,
      },
      {
        name: "제출하지 않았을 때의 실패 응답은 무시한다",
        phase: "answering",
        from: chosen,
        action: SUBMIT_FAILURE,
      },
      {
        name: "채점 전에는 다음으로 갈 수 없다",
        phase: "answering",
        from: chosen,
        action: NEXT,
      },
      {
        name: "제출 중에는 보기를 바꿀 수 없다 (R5)",
        phase: "submitting",
        from: submitting,
        action: SELECT_OTHER,
      },
      {
        name: "제출 중에는 다시 제출할 수 없다 (R4)",
        phase: "submitting",
        from: submitting,
        action: SUBMIT_START,
      },
      {
        name: "제출 중에는 다음으로 갈 수 없다",
        phase: "submitting",
        from: submitting,
        action: NEXT,
      },
      {
        name: "채점 뒤에는 답을 바꿀 수 없다 (R5)",
        phase: "graded",
        from: graded,
        action: SELECT_OTHER,
      },
      {
        name: "채점 뒤에는 다시 제출할 수 없다 (R5)",
        phase: "graded",
        from: graded,
        action: SUBMIT_START,
      },
      {
        name: "채점 뒤의 성공 응답으로 점수가 두 번 오르지 않는다",
        phase: "graded",
        from: graded,
        action: SUBMIT_CORRECT,
      },
      {
        name: "채점 뒤의 실패 응답은 결과를 지우지 않는다",
        phase: "graded",
        from: graded,
        action: SUBMIT_FAILURE,
      },
      {
        name: "끝난 뒤에는 보기를 고를 수 없다",
        phase: "finished",
        from: finished,
        action: SELECT_OTHER,
      },
      {
        name: "끝난 뒤에는 제출할 수 없다",
        phase: "finished",
        from: finished,
        action: SUBMIT_START,
      },
      {
        name: "끝난 뒤의 성공 응답으로 점수가 오르지 않는다",
        phase: "finished",
        from: finished,
        action: SUBMIT_CORRECT,
      },
      {
        name: "끝난 뒤의 실패 응답은 무시한다",
        phase: "finished",
        from: finished,
        action: SUBMIT_FAILURE,
      },
      {
        name: "끝난 뒤에는 다음으로 갈 수 없다",
        phase: "finished",
        from: finished,
        action: NEXT,
      },
    ];

    it.each(cases)("$name", ({ phase, from, action }) => {
      const before = from();

      // 아무 동작도 하지 않는 reducer가 시작 단계가 틀린 채로 통과하지 못하게 한다
      expect(before.phase).toBe(phase);
      expect(run(before, action)).toEqual(before);
    });
  });

  describe("전체 풀이 (R6)", () => {
    it("세 문제를 끝까지 풀면 정답 수를 집계한다", () => {
      const state = run(
        createInitialState(3),
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
        SELECT,
        SUBMIT_START,
        SUBMIT_WRONG,
        NEXT,
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
      );

      expect(state).toMatchObject({
        phase: "finished",
        totalQuestions: 3,
        correctCount: 2,
      });
    });

    it("실패 후 다시 제출한 문제는 정답을 한 번만 센다", () => {
      const state = run(
        createInitialState(1),
        SELECT,
        SUBMIT_START,
        SUBMIT_FAILURE,
        SUBMIT_START,
        SUBMIT_FAILURE,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
      );

      expect(state).toMatchObject({
        phase: "finished",
        totalQuestions: 1,
        correctCount: 1,
      });
    });
  });
});
