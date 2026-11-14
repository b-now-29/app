import { describe, expect, it } from "vitest";
import type {
  AnswerResponse,
  QuestionType,
  TextAnswerResponse,
} from "../api/questions";
import { ApiError, NotFoundError } from "../api/http";
import {
  canSubmit,
  createInitialState,
  isLastQuestion,
  quizReducer,
  selfGradeSaveFailureAction,
  shouldSaveSelfGrade,
} from "./quizReducer";
import type { QuizAction, QuizPhase, QuizState } from "./quizReducer";

const CHOICE_ID = 10;
const OTHER_CHOICE_ID = 20;

const CORRECT_RESULT: AnswerResponse = {
  correct: true,
  correctChoiceId: CHOICE_ID,
  explanation: "해설",
  recorded: true,
};
const WRONG_RESULT: AnswerResponse = {
  correct: false,
  correctChoiceId: OTHER_CHOICE_ID,
  explanation: null,
  recorded: true,
};

const SELECT: QuizAction = { type: "select", choiceId: CHOICE_ID };
const SELECT_OTHER: QuizAction = { type: "select", choiceId: OTHER_CHOICE_ID };
const SUBMIT_START: QuizAction = { type: "submitStart" };
// 저장되지 않은 채점 응답. 회원이 받으면 로그인 만료로 본다 (D13)
const CORRECT_UNRECORDED: AnswerResponse = {
  ...CORRECT_RESULT,
  recorded: false,
};
const WRONG_UNRECORDED: AnswerResponse = { ...WRONG_RESULT, recorded: false };

const submitSuccess = (
  result: AnswerResponse,
  isMember: boolean,
): QuizAction => ({ type: "submitSuccess", result, isMember });
const SUBMIT_CORRECT = submitSuccess(CORRECT_RESULT, true);
const SUBMIT_WRONG = submitSuccess(WRONG_RESULT, true);
const SUBMIT_FAILURE: QuizAction = { type: "submitFailure" };
const NEXT: QuizAction = { type: "next" };

const ANSWER = "내가 쓴 답\n둘째 줄";
const OTHER_ANSWER = "고쳐 쓴 답";
const REVEAL: TextAnswerResponse = {
  modelAnswer: "모범 답안",
  explanation: "해설",
};

const inputText = (text: string): QuizAction => ({ type: "inputText", text });
const INPUT = inputText(ANSWER);
const REVEAL_SUCCESS: QuizAction = { type: "revealSuccess", reveal: REVEAL };
const selfGrade = (
  questionType: "SHORT_ANSWER" | "ESSAY",
  correct: boolean,
): QuizAction => ({ type: "selfGrade", questionType, correct });
const SHORT_ANSWER_RIGHT = selfGrade("SHORT_ANSWER", true);
const SHORT_ANSWER_WRONG = selfGrade("SHORT_ANSWER", false);
const ESSAY_RIGHT = selfGrade("ESSAY", true);
const ESSAY_WRONG = selfGrade("ESSAY", false);

// 값을 준 유형 말고는 모두 0인 맞은 수를 만든다
const correctCountsOf = (
  counts: Partial<Record<QuestionType, number>> = {},
): Record<QuestionType, number> => ({
  MULTIPLE_CHOICE: 0,
  SHORT_ANSWER: 0,
  ESSAY: 0,
  ...counts,
});

// 얼린 상태를 넘겨서, reducer가 상태를 직접 수정하면 TypeError로 드러나게 한다.
// Object.freeze는 한 단계만 얼리므로 안쪽의 correctCounts도 따로 얼린다.
function run(state: QuizState, ...actions: readonly QuizAction[]): QuizState {
  return actions.reduce((current, action) => {
    Object.freeze(current.correctCounts);
    return quizReducer(Object.freeze(current), action);
  }, state);
}

const noChoice = () => createInitialState(3);
const chosen = () => run(noChoice(), SELECT);
const submitting = () => run(chosen(), SUBMIT_START);
const failed = () => run(submitting(), SUBMIT_FAILURE);
const graded = () => run(submitting(), SUBMIT_CORRECT);
const finished = () =>
  run(createInitialState(1), SELECT, SUBMIT_START, SUBMIT_CORRECT, NEXT);

const typed = () => run(noChoice(), INPUT);
const submittingText = () => run(typed(), SUBMIT_START);
const failedText = () => run(submittingText(), SUBMIT_FAILURE);
const revealed = () => run(submittingText(), REVEAL_SUCCESS);

// 회원의 자기 채점 저장 (R2, D9)
const SAVE_START: QuizAction = { type: "selfGradeSaveStart" };
const SAVE_FAILURE: QuizAction = { type: "selfGradeSaveFailure" };
// 401이면 고른 결과를 점수에 넣고 바로 넘어가야 하므로 자기 채점 결과를 함께 싣는다 (D12)
const saveUnauthorized = (
  questionType: "SHORT_ANSWER" | "ESSAY",
  correct: boolean,
): QuizAction => ({ type: "selfGradeSaveUnauthorized", questionType, correct });
const SAVE_UNAUTHORIZED = saveUnauthorized("SHORT_ANSWER", true);
const savingSelfGrade = () => run(revealed(), SAVE_START);
const saveFailed = () => run(savingSelfGrade(), SAVE_FAILURE);
// 첫 문제에서 401을 받아 둘째 문제로 넘어온 상태
const loginExpired = () => run(savingSelfGrade(), SAVE_UNAUTHORIZED);
// 로그인 만료 뒤 다음 문제에서 모범 답안까지 본 상태
const revealedAfterLoginExpired = () =>
  run(loginExpired(), INPUT, SUBMIT_START, REVEAL_SUCCESS);

// 회원인데 객관식 제출 응답이 recorded: false라서 로그인 만료로 보는 경우 (D13)
const MEMBER_CORRECT_UNRECORDED = submitSuccess(CORRECT_UNRECORDED, true);
const MEMBER_WRONG_UNRECORDED = submitSuccess(WRONG_UNRECORDED, true);
// 비회원은 늘 recorded: false를 받으며 로그인 만료가 아니다
const GUEST_CORRECT = submitSuccess(CORRECT_UNRECORDED, false);
const GUEST_WRONG = submitSuccess(WRONG_UNRECORDED, false);
// 첫 문제(객관식) 채점 응답에서 만료를 감지한 상태
const gradedLoginExpired = () => run(submitting(), MEMBER_CORRECT_UNRECORDED);

describe("quizReducer", () => {
  describe("초기 상태", () => {
    it("첫 문제에서 보기를 고르지 않고 답도 쓰지 않은 채 0점으로 시작한다", () => {
      expect(createInitialState(3)).toEqual({
        phase: "answering",
        currentIndex: 0,
        totalQuestions: 3,
        selectedChoiceId: null,
        textAnswer: "",
        result: null,
        reveal: null,
        correctCounts: { MULTIPLE_CHOICE: 0, SHORT_ANSWER: 0, ESSAY: 0 },
        submitFailed: false,
        selfGradeSaveFailed: false,
        loginExpiredAt: null,
      });
    });
  });

  describe("inputText", () => {
    it("답을 쓰는 단계에서 입력을 줄바꿈과 공백까지 그대로 기록한다 (R3)", () => {
      const raw = "  첫 줄\n둘째 줄  ";

      expect(run(noChoice(), inputText(raw))).toEqual({
        ...noChoice(),
        textAnswer: raw,
      });
    });

    it("제출하기 전에는 답을 고칠 수 있다", () => {
      expect(run(typed(), inputText(OTHER_ANSWER))).toEqual({
        ...noChoice(),
        textAnswer: OTHER_ANSWER,
      });
    });
  });

  describe("제출 가능 조건 (D7, R3)", () => {
    it("공백이 아닌 답이 있으면 보기를 고르지 않아도 제출 중 단계로 가고 답을 유지한다", () => {
      expect(run(typed(), SUBMIT_START)).toEqual({
        ...noChoice(),
        phase: "submitting",
        textAnswer: ANSWER,
      });
    });

    it.each([
      { name: "스페이스", blank: "   " },
      { name: "줄바꿈과 탭", blank: "\n\t \n" },
    ])("공백뿐인 답은 제출할 수 없다: $name", ({ blank }) => {
      const before = run(noChoice(), inputText(blank));

      // 입력을 기록하지 않는 reducer가 빈 답이라서 통과하지 못하게 한다
      expect(before.textAnswer).toBe(blank);
      expect(run(before, SUBMIT_START)).toEqual(before);
    });

    it("썼던 답을 모두 지우면 제출할 수 없다", () => {
      expect(typed().textAnswer).toBe(ANSWER);

      const erased = run(typed(), inputText(""));

      expect(run(erased, SUBMIT_START)).toEqual(erased);
      expect(erased.phase).toBe("answering");
    });

    it("보기를 골랐으면 답이 공백뿐이어도 제출할 수 있다", () => {
      expect(run(chosen(), inputText("   "), SUBMIT_START)).toEqual({
        ...chosen(),
        phase: "submitting",
        textAnswer: "   ",
      });
    });
  });

  describe("revealSuccess (R4)", () => {
    it("모범 답안 단계로 가서 공개 내용을 기록하고 내 답을 유지하며 점수는 그대로 둔다", () => {
      expect(run(submittingText(), REVEAL_SUCCESS)).toEqual({
        ...noChoice(),
        phase: "revealed",
        textAnswer: ANSWER,
        reveal: REVEAL,
      });
    });
  });

  describe("주관식 제출 실패 (R7)", () => {
    it("답을 쓰는 단계로 돌아가 입력한 답을 유지하고 실패를 표시한다", () => {
      expect(run(submittingText(), SUBMIT_FAILURE)).toEqual({
        ...noChoice(),
        textAnswer: ANSWER,
        submitFailed: true,
      });
    });

    it("실패한 뒤 다시 제출하면 실패 표시를 지우고 제출 중 단계로 간다", () => {
      expect(run(failedText(), SUBMIT_START)).toEqual({
        ...noChoice(),
        phase: "submitting",
        textAnswer: ANSWER,
      });
    });

    it("다시 제출해 성공하면 실패 표시 없이 모범 답안을 기록한다", () => {
      expect(run(failedText(), SUBMIT_START, REVEAL_SUCCESS)).toEqual({
        ...noChoice(),
        phase: "revealed",
        textAnswer: ANSWER,
        reveal: REVEAL,
      });
    });

    it("실패한 뒤에는 아직 공개 전이므로 답을 고칠 수 있다", () => {
      expect(run(failedText(), inputText(OTHER_ANSWER))).toMatchObject({
        phase: "answering",
        textAnswer: OTHER_ANSWER,
      });
    });
  });

  describe("selfGrade (D2, R5)", () => {
    const NO_SCORE = { correctCounts: correctCountsOf() };
    const types = [
      {
        name: "주관식",
        right: SHORT_ANSWER_RIGHT,
        wrong: SHORT_ANSWER_WRONG,
        scored: { correctCounts: correctCountsOf({ SHORT_ANSWER: 1 }) },
      },
      {
        name: "서술형",
        right: ESSAY_RIGHT,
        wrong: ESSAY_WRONG,
        scored: { correctCounts: correctCountsOf({ ESSAY: 1 }) },
      },
    ];

    it.each(types)(
      "맞았어요를 고르면 그 유형의 맞은 수만 늘리고 다음 문제로 가서 답과 공개 내용을 비운다: $name",
      ({ right, scored }) => {
        expect(run(revealed(), right)).toEqual({
          ...noChoice(),
          currentIndex: 1,
          ...scored,
        });
      },
    );

    it.each(types)(
      "틀렸어요를 고르면 점수를 늘리지 않고 다음 문제로 가서 답과 공개 내용을 비운다: $name",
      ({ wrong }) => {
        expect(run(revealed(), wrong)).toEqual({
          ...noChoice(),
          currentIndex: 1,
          ...NO_SCORE,
        });
      },
    );

    it.each(types)(
      "마지막 문제에서 맞았어요를 고르면 next 없이 바로 끝 단계로 가고 점수를 유지한다: $name",
      ({ right, scored }) => {
        const state = run(
          createInitialState(1),
          INPUT,
          SUBMIT_START,
          REVEAL_SUCCESS,
          right,
        );

        expect(state).toMatchObject({
          phase: "finished",
          totalQuestions: 1,
          ...scored,
        });
      },
    );

    it.each(types)(
      "마지막 문제에서 틀렸어요를 고르면 점수 없이 끝 단계로 간다: $name",
      ({ wrong }) => {
        const state = run(
          createInitialState(1),
          INPUT,
          SUBMIT_START,
          REVEAL_SUCCESS,
          wrong,
        );

        expect(state).toMatchObject({
          phase: "finished",
          ...NO_SCORE,
        });
      },
    );

    it.each(types)(
      "넘어간 뒤 같은 동작이 또 와도 다음 문제의 점수로 세지 않는다: $name",
      ({ right, scored }) => {
        expect(run(revealed(), right, right)).toEqual({
          ...noChoice(),
          currentIndex: 1,
          ...scored,
        });
      },
    );

    // reducer는 문제 유형을 모르므로, 이전 답이 남으면 다음 객관식을 보기 없이 제출할 수 있게 된다
    it.each(types)(
      "넘어간 뒤에는 이전 답이 남지 않아 입력 없이 제출할 수 없다: $name",
      ({ right }) => {
        expect(run(revealed(), right, SUBMIT_START)).toMatchObject({
          phase: "answering",
          currentIndex: 1,
          textAnswer: "",
        });
      },
    );

    it("문제를 넘어가도 유형별 맞은 수가 각자 쌓인다", () => {
      const answerAndGrade = (grade: QuizAction): readonly QuizAction[] => [
        INPUT,
        SUBMIT_START,
        REVEAL_SUCCESS,
        grade,
      ];

      const state = run(
        createInitialState(5),
        ...answerAndGrade(ESSAY_RIGHT),
        ...answerAndGrade(SHORT_ANSWER_RIGHT),
        ...answerAndGrade(ESSAY_RIGHT),
        ...answerAndGrade(SHORT_ANSWER_WRONG),
        ...answerAndGrade(ESSAY_WRONG),
      );

      expect(state).toMatchObject({
        phase: "finished",
        correctCounts: correctCountsOf({ SHORT_ANSWER: 1, ESSAY: 2 }),
      });
    });
  });

  describe("자기 채점 저장 (R2, D9)", () => {
    const REVEALED_FIRST = {
      ...noChoice(),
      phase: "revealed" as const,
      textAnswer: ANSWER,
      reveal: REVEAL,
    };

    describe("selfGradeSaveStart", () => {
      it("모범 답안 단계에서 저장 중 단계로 가고 답, 공개 내용, 점수를 그대로 둔다", () => {
        expect(run(revealed(), SAVE_START)).toEqual({
          ...REVEALED_FIRST,
          phase: "savingSelfGrade",
        });
      });

      it("저장에 실패한 뒤 다시 시도하면 실패 표시를 지우고 저장 중 단계로 간다", () => {
        expect(run(saveFailed(), SAVE_START)).toEqual({
          ...REVEALED_FIRST,
          phase: "savingSelfGrade",
          selfGradeSaveFailed: false,
        });
      });
    });

    describe("저장 성공 뒤 selfGrade", () => {
      it.each([
        {
          name: "주관식 맞았어요",
          grade: SHORT_ANSWER_RIGHT,
          counts: correctCountsOf({ SHORT_ANSWER: 1 }),
        },
        {
          name: "서술형 맞았어요",
          grade: ESSAY_RIGHT,
          counts: correctCountsOf({ ESSAY: 1 }),
        },
        {
          name: "주관식 틀렸어요",
          grade: SHORT_ANSWER_WRONG,
          counts: correctCountsOf(),
        },
      ])(
        "저장 중 단계에서 받으면 점수를 세고 다음 문제로 간다: $name",
        ({ grade, counts }) => {
          expect(run(savingSelfGrade(), grade)).toEqual({
            ...noChoice(),
            currentIndex: 1,
            correctCounts: counts,
          });
        },
      );

      it("마지막 문제에서 저장이 끝나면 끝 단계로 간다", () => {
        const state = run(
          createInitialState(1),
          INPUT,
          SUBMIT_START,
          REVEAL_SUCCESS,
          SAVE_START,
          ESSAY_RIGHT,
        );

        expect(state).toMatchObject({
          phase: "finished",
          correctCounts: correctCountsOf({ ESSAY: 1 }),
        });
      });

      it("저장 성공 뒤 같은 동작이 또 와도 다음 문제의 점수로 세지 않는다", () => {
        expect(run(savingSelfGrade(), ESSAY_RIGHT, ESSAY_RIGHT)).toEqual({
          ...noChoice(),
          currentIndex: 1,
          correctCounts: correctCountsOf({ ESSAY: 1 }),
        });
      });
    });

    describe("selfGradeSaveFailure (통신 오류, 500, 400, 404)", () => {
      it("모범 답안 단계로 돌아가 답과 공개 내용을 유지하고 실패를 표시하며 점수는 그대로 둔다", () => {
        expect(run(savingSelfGrade(), SAVE_FAILURE)).toEqual({
          ...REVEALED_FIRST,
          selfGradeSaveFailed: true,
        });
      });

      it("실패해도 다음 문제로 넘어가지 않는다", () => {
        expect(saveFailed()).toMatchObject({
          phase: "revealed",
          currentIndex: 0,
        });
      });

      it("다시 시도해 성공하면 점수를 한 번만 세고 다음 문제에서는 실패 표시가 없다", () => {
        expect(
          run(
            saveFailed(),
            SAVE_START,
            SAVE_FAILURE,
            SAVE_START,
            SHORT_ANSWER_RIGHT,
          ),
        ).toEqual({
          ...noChoice(),
          currentIndex: 1,
          correctCounts: correctCountsOf({ SHORT_ANSWER: 1 }),
        });
      });

      it("실패 뒤 저장 없이 넘어가는 동작이 와도 다음 문제에 실패 표시를 남기지 않는다", () => {
        expect(run(saveFailed(), SHORT_ANSWER_WRONG)).toEqual({
          ...noChoice(),
          currentIndex: 1,
        });
      });

      it("저장 실패는 로그인 만료가 아니므로 이후 저장을 막지 않는다", () => {
        expect(saveFailed().loginExpiredAt).toBeNull();
        expect(run(saveFailed(), SAVE_START).phase).toBe("savingSelfGrade");
      });
    });

    describe("selfGradeSaveUnauthorized (로그인 만료, D12)", () => {
      it.each([
        {
          name: "주관식 맞았어요",
          action: saveUnauthorized("SHORT_ANSWER", true),
          counts: correctCountsOf({ SHORT_ANSWER: 1 }),
        },
        {
          name: "서술형 맞았어요",
          action: saveUnauthorized("ESSAY", true),
          counts: correctCountsOf({ ESSAY: 1 }),
        },
        {
          name: "서술형 틀렸어요",
          action: saveUnauthorized("ESSAY", false),
          counts: correctCountsOf(),
        },
      ])(
        "고른 결과를 점수에 넣고 추가 동작 없이 다음 문제로 넘어가며 401이 난 문제 번호를 기억한다: $name",
        ({ action, counts }) => {
          expect(run(savingSelfGrade(), action)).toEqual({
            ...noChoice(),
            currentIndex: 1,
            correctCounts: counts,
            loginExpiredAt: 0,
          });
        },
      );

      it("앞서 실패 표시가 있었어도 다음 문제에는 남기지 않는다", () => {
        expect(run(saveFailed(), SAVE_START, SAVE_UNAUTHORIZED)).toEqual({
          ...noChoice(),
          currentIndex: 1,
          correctCounts: correctCountsOf({ SHORT_ANSWER: 1 }),
          loginExpiredAt: 0,
        });
      });

      it("둘째 문제에서 401이 나면 넘어간 뒤에도 401이 난 둘째 문제의 위치를 기억한다 (D13)", () => {
        const state = run(
          graded(),
          NEXT,
          INPUT,
          SUBMIT_START,
          REVEAL_SUCCESS,
          SAVE_START,
          SAVE_UNAUTHORIZED,
        );

        expect(state).toMatchObject({
          phase: "answering",
          currentIndex: 2,
          loginExpiredAt: 1,
        });
      });

      it("마지막 문제에서 받으면 점수를 넣고 끝 단계로 간다", () => {
        const state = run(
          createInitialState(1),
          INPUT,
          SUBMIT_START,
          REVEAL_SUCCESS,
          SAVE_START,
          saveUnauthorized("ESSAY", false),
        );

        expect(state).toMatchObject({
          phase: "finished",
          correctCounts: correctCountsOf(),
          loginExpiredAt: 0,
        });
      });

      it("넘어간 뒤 같은 응답이 또 와도 다음 문제의 점수로 세지 않는다", () => {
        expect(run(loginExpired(), SAVE_UNAUTHORIZED)).toEqual(loginExpired());
      });

      it("로그인 만료는 문제가 바뀌어도 그 퀴즈 끝까지 유지된다", () => {
        expect(revealedAfterLoginExpired()).toMatchObject({
          phase: "revealed",
          currentIndex: 1,
          loginExpiredAt: 0,
        });

        const state = run(revealedAfterLoginExpired(), SHORT_ANSWER_WRONG);

        expect(state).toMatchObject({
          phase: "answering",
          currentIndex: 2,
          loginExpiredAt: 0,
          correctCounts: correctCountsOf({ SHORT_ANSWER: 1 }),
        });

        const lastQuestion = run(
          state,
          INPUT,
          SUBMIT_START,
          REVEAL_SUCCESS,
          ESSAY_RIGHT,
        );

        expect(lastQuestion).toMatchObject({
          phase: "finished",
          loginExpiredAt: 0,
          correctCounts: correctCountsOf({ SHORT_ANSWER: 1, ESSAY: 1 }),
        });
      });

      it("객관식 문제를 거쳐도 로그인 만료가 유지된다", () => {
        const state = run(
          createInitialState(3),
          INPUT,
          SUBMIT_START,
          REVEAL_SUCCESS,
          SAVE_START,
          SAVE_UNAUTHORIZED,
          SELECT,
          SUBMIT_START,
          SUBMIT_CORRECT,
          NEXT,
        );

        expect(state).toMatchObject({
          phase: "answering",
          currentIndex: 2,
          loginExpiredAt: 0,
        });
      });

      it("이미 로그인 만료를 감지했으면 뒤 문제의 객관식 만료 감지로 번호가 바뀌지 않는다 (D13)", () => {
        const submittingAfterLoginExpired = run(
          loginExpired(),
          SELECT,
          SUBMIT_START,
        );

        expect(submittingAfterLoginExpired).toMatchObject({
          phase: "submitting",
          currentIndex: 1,
          loginExpiredAt: 0,
        });
        expect(
          run(submittingAfterLoginExpired, MEMBER_CORRECT_UNRECORDED),
        ).toEqual({
          ...submittingAfterLoginExpired,
          phase: "graded",
          result: CORRECT_UNRECORDED,
          correctCounts: correctCountsOf({
            SHORT_ANSWER: 1,
            MULTIPLE_CHOICE: 1,
          }),
          loginExpiredAt: 0,
        });
      });
    });
  });

  describe("submitSuccess의 로그인 만료 감지 (객관식 recorded: false, D13)", () => {
    it("회원이 recorded: false를 받으면 채점 결과와 점수를 기록하고 그 문제의 위치를 기억한다", () => {
      expect(run(submitting(), MEMBER_CORRECT_UNRECORDED)).toEqual({
        ...submitting(),
        phase: "graded",
        result: CORRECT_UNRECORDED,
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
        loginExpiredAt: 0,
      });
    });

    it("회원이 오답을 recorded: false로 받아도 점수 없이 위치를 기억한다", () => {
      expect(run(submitting(), MEMBER_WRONG_UNRECORDED)).toEqual({
        ...submitting(),
        phase: "graded",
        result: WRONG_UNRECORDED,
        loginExpiredAt: 0,
      });
    });

    it("회원이 recorded: true를 받으면 만료로 보지 않는다", () => {
      expect(run(submitting(), SUBMIT_CORRECT).loginExpiredAt).toBeNull();
    });

    it.each([
      {
        name: "정답",
        action: GUEST_CORRECT,
        result: CORRECT_UNRECORDED,
        counts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
      },
      {
        name: "오답",
        action: GUEST_WRONG,
        result: WRONG_UNRECORDED,
        counts: correctCountsOf(),
      },
    ])(
      "비회원은 recorded: false여도 만료로 보지 않고 채점 결과만 기록한다: $name",
      ({ action, result, counts }) => {
        expect(run(submitting(), action)).toEqual({
          ...submitting(),
          phase: "graded",
          result,
          correctCounts: counts,
          loginExpiredAt: null,
        });
      },
    );

    it("비회원이 recorded: true를 받아도 만료로 보지 않는다", () => {
      expect(
        run(submitting(), submitSuccess(CORRECT_RESULT, false)).loginExpiredAt,
      ).toBeNull();
    });

    it("둘째 문제에서 받으면 둘째 문제의 위치를 기억한다", () => {
      const secondSubmitting = run(graded(), NEXT, SELECT, SUBMIT_START);

      expect(run(secondSubmitting, MEMBER_WRONG_UNRECORDED)).toEqual({
        ...secondSubmitting,
        phase: "graded",
        result: WRONG_UNRECORDED,
        loginExpiredAt: 1,
      });
    });

    it("이전 문제에서 만료를 감지했으면 다시 recorded: false가 와도 처음 번호를 유지한다", () => {
      const state = run(
        gradedLoginExpired(),
        NEXT,
        SELECT,
        SUBMIT_START,
        MEMBER_WRONG_UNRECORDED,
      );

      expect(state).toMatchObject({
        phase: "graded",
        currentIndex: 1,
        result: WRONG_UNRECORDED,
        loginExpiredAt: 0,
      });
    });

    it("만료를 감지한 뒤 recorded: true가 와도 만료 표시를 지우지 않는다", () => {
      const state = run(
        gradedLoginExpired(),
        NEXT,
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
      );

      expect(state).toMatchObject({ currentIndex: 1, loginExpiredAt: 0 });
    });

    it("다음 문제로 넘어가도 위치가 유지된다", () => {
      expect(run(gradedLoginExpired(), NEXT)).toMatchObject({
        phase: "answering",
        currentIndex: 1,
        loginExpiredAt: 0,
      });
    });

    it("이후 주관식·서술형에서는 자기 채점 저장을 시작하지 않는다", () => {
      const revealedNext = run(
        gradedLoginExpired(),
        NEXT,
        INPUT,
        SUBMIT_START,
        REVEAL_SUCCESS,
      );

      expect(revealedNext.phase).toBe("revealed");
      expect(run(revealedNext, SAVE_START)).toEqual(revealedNext);
    });

    it("마지막 문제에서 받으면 끝 단계까지 위치가 유지된다", () => {
      const state = run(
        createInitialState(1),
        SELECT,
        SUBMIT_START,
        MEMBER_CORRECT_UNRECORDED,
        NEXT,
      );

      expect(state).toMatchObject({
        phase: "finished",
        loginExpiredAt: 0,
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
      });
    });

    it("같은 문제에서 또 받아도 그대로다", () => {
      expect(run(gradedLoginExpired(), MEMBER_CORRECT_UNRECORDED)).toEqual(
        gradedLoginExpired(),
      );
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
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
      });
    });

    it("오답이면 채점 결과를 기록하고 정답 수를 늘리지 않는다", () => {
      expect(run(submitting(), SUBMIT_WRONG)).toEqual({
        ...submitting(),
        phase: "graded",
        result: WRONG_RESULT,
        correctCounts: correctCountsOf(),
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
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
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
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
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
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
      });
    });

    it("문제가 하나뿐이면 첫 다음 동작에서 끝 단계로 간다", () => {
      expect(finished()).toMatchObject({
        phase: "finished",
        totalQuestions: 1,
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
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
      {
        name: "답을 쓰지 않았을 때의 공개 응답은 무시한다",
        phase: "answering",
        from: noChoice,
        action: REVEAL_SUCCESS,
      },
      {
        name: "제출하지 않았을 때의 공개 응답은 무시한다",
        phase: "answering",
        from: typed,
        action: REVEAL_SUCCESS,
      },
      {
        name: "모범 답안을 보기 전에는 자기 채점을 할 수 없다",
        phase: "answering",
        from: typed,
        action: SHORT_ANSWER_RIGHT,
      },
      {
        name: "모범 답안을 보기 전에는 서술형 자기 채점을 할 수 없다",
        phase: "answering",
        from: typed,
        action: ESSAY_RIGHT,
      },
      {
        name: "제출 중에는 답을 고칠 수 없다 (R3)",
        phase: "submitting",
        from: submittingText,
        action: inputText(OTHER_ANSWER),
      },
      {
        name: "제출 중에는 다시 제출할 수 없다 (R3)",
        phase: "submitting",
        from: submittingText,
        action: SUBMIT_START,
      },
      {
        name: "제출 중에는 자기 채점을 할 수 없다",
        phase: "submitting",
        from: submittingText,
        action: SHORT_ANSWER_RIGHT,
      },
      {
        name: "제출 중에는 서술형 자기 채점을 할 수 없다",
        phase: "submitting",
        from: submittingText,
        action: ESSAY_RIGHT,
      },
      {
        name: "모범 답안을 본 뒤에는 답을 고칠 수 없다 (R4)",
        phase: "revealed",
        from: revealed,
        action: inputText(OTHER_ANSWER),
      },
      {
        name: "모범 답안을 본 뒤에는 보기를 고를 수 없다",
        phase: "revealed",
        from: revealed,
        action: SELECT,
      },
      {
        name: "모범 답안을 본 뒤에는 다시 제출할 수 없다 (R4)",
        phase: "revealed",
        from: revealed,
        action: SUBMIT_START,
      },
      {
        name: "자기 채점을 고르지 않으면 다음으로 갈 수 없다 (R5)",
        phase: "revealed",
        from: revealed,
        action: NEXT,
      },
      {
        name: "모범 답안을 본 뒤의 공개 응답은 무시한다",
        phase: "revealed",
        from: revealed,
        action: REVEAL_SUCCESS,
      },
      {
        name: "모범 답안을 본 뒤의 채점 응답으로 점수가 오르지 않는다",
        phase: "revealed",
        from: revealed,
        action: SUBMIT_CORRECT,
      },
      {
        name: "모범 답안을 본 뒤의 실패 응답은 공개 내용을 지우지 않는다",
        phase: "revealed",
        from: revealed,
        action: SUBMIT_FAILURE,
      },
      {
        name: "채점 뒤에는 답을 입력할 수 없다",
        phase: "graded",
        from: graded,
        action: INPUT,
      },
      {
        name: "채점 뒤의 공개 응답은 무시한다",
        phase: "graded",
        from: graded,
        action: REVEAL_SUCCESS,
      },
      {
        name: "객관식 채점 뒤에는 자기 채점으로 넘어가거나 점수가 오르지 않는다",
        phase: "graded",
        from: graded,
        action: SHORT_ANSWER_RIGHT,
      },
      {
        name: "객관식 채점 뒤에는 서술형 자기 채점으로 넘어가거나 점수가 오르지 않는다",
        phase: "graded",
        from: graded,
        action: ESSAY_RIGHT,
      },
      {
        name: "끝난 뒤에는 답을 입력할 수 없다",
        phase: "finished",
        from: finished,
        action: INPUT,
      },
      {
        name: "끝난 뒤의 공개 응답은 무시한다",
        phase: "finished",
        from: finished,
        action: REVEAL_SUCCESS,
      },
      {
        name: "끝난 뒤의 자기 채점으로 점수가 오르지 않는다",
        phase: "finished",
        from: finished,
        action: SHORT_ANSWER_RIGHT,
      },
      {
        name: "끝난 뒤의 서술형 자기 채점으로 점수가 오르지 않는다",
        phase: "finished",
        from: finished,
        action: ESSAY_RIGHT,
      },
      // 자기 채점 저장 (R2, R3, D9)
      {
        name: "저장 중에는 다시 저장을 시작하지 않는다 (연속 클릭으로 중복 저장 방지, R3)",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: SAVE_START,
      },
      {
        name: "저장 중에는 답을 고칠 수 없다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: inputText(OTHER_ANSWER),
      },
      {
        name: "저장 중에는 보기를 고를 수 없다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: SELECT,
      },
      {
        name: "저장 중에는 다시 제출할 수 없다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: SUBMIT_START,
      },
      {
        name: "저장 중에는 다음으로 갈 수 없다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: NEXT,
      },
      {
        name: "저장 중의 공개 응답은 무시한다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: REVEAL_SUCCESS,
      },
      {
        name: "저장 중의 채점 응답으로 점수가 오르지 않는다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: SUBMIT_CORRECT,
      },
      {
        name: "저장 중의 답 제출 실패 응답은 무시한다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: SUBMIT_FAILURE,
      },
      {
        name: "저장을 시작하지 않았을 때의 저장 실패 응답은 무시한다",
        phase: "revealed",
        from: revealed,
        action: SAVE_FAILURE,
      },
      {
        name: "저장을 시작하지 않았을 때의 로그인 만료 응답은 무시한다",
        phase: "revealed",
        from: revealed,
        action: SAVE_UNAUTHORIZED,
      },
      {
        name: "실패를 표시한 뒤 늦게 온 실패 응답은 무시한다",
        phase: "revealed",
        from: saveFailed,
        action: SAVE_FAILURE,
      },
      {
        name: "로그인 만료로 넘어간 다음 문제에서 답을 쓰기 전에는 저장을 시작하지 않는다",
        phase: "answering",
        from: loginExpired,
        action: SAVE_START,
      },
      {
        name: "모범 답안 단계의 로그인 만료 응답은 저장을 시작하지 않았으므로 무시한다",
        phase: "revealed",
        from: revealedAfterLoginExpired,
        action: SAVE_UNAUTHORIZED,
      },
      {
        name: "로그인 만료 뒤 다음 문제에서도 저장을 시작하지 않는다",
        phase: "revealed",
        from: revealedAfterLoginExpired,
        action: SAVE_START,
      },
      {
        name: "모범 답안을 보기 전에는 저장을 시작할 수 없다",
        phase: "answering",
        from: typed,
        action: SAVE_START,
      },
      {
        name: "답 제출 중에는 저장을 시작할 수 없다",
        phase: "submitting",
        from: submittingText,
        action: SAVE_START,
      },
      {
        name: "객관식 채점 뒤에는 자기 채점 저장을 시작할 수 없다",
        phase: "graded",
        from: graded,
        action: SAVE_START,
      },
      {
        name: "객관식 채점 뒤의 저장 실패 응답은 무시한다",
        phase: "graded",
        from: graded,
        action: SAVE_FAILURE,
      },
      {
        name: "끝난 뒤에는 저장을 시작할 수 없다",
        phase: "finished",
        from: finished,
        action: SAVE_START,
      },
      {
        name: "끝난 뒤의 저장 실패 응답은 무시한다",
        phase: "finished",
        from: finished,
        action: SAVE_FAILURE,
      },
      {
        name: "끝난 뒤의 로그인 만료 응답은 무시한다",
        phase: "finished",
        from: finished,
        action: SAVE_UNAUTHORIZED,
      },
      {
        name: "다음 문제로 넘어간 뒤 늦게 온 저장 실패 응답은 무시한다",
        phase: "answering",
        from: () => run(savingSelfGrade(), SHORT_ANSWER_RIGHT),
        action: SAVE_FAILURE,
      },
      // 만료를 알리는 객관식 채점 응답도 제출 중이 아니면 무시한다 (D13)
      {
        name: "제출하지 않았을 때 회원의 recorded: false 응답은 무시한다",
        phase: "answering",
        from: chosen,
        action: MEMBER_CORRECT_UNRECORDED,
      },
      {
        name: "채점 뒤 늦게 온 회원의 recorded: false 응답은 무시한다",
        phase: "graded",
        from: graded,
        action: MEMBER_CORRECT_UNRECORDED,
      },
      {
        name: "모범 답안 단계에서는 회원의 recorded: false 응답을 무시한다",
        phase: "revealed",
        from: revealed,
        action: MEMBER_CORRECT_UNRECORDED,
      },
      {
        name: "자기 채점 저장 중에는 회원의 recorded: false 응답을 무시한다",
        phase: "savingSelfGrade",
        from: savingSelfGrade,
        action: MEMBER_CORRECT_UNRECORDED,
      },
      {
        name: "끝난 뒤에는 회원의 recorded: false 응답을 무시한다",
        phase: "finished",
        from: finished,
        action: MEMBER_CORRECT_UNRECORDED,
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
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 2 }),
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
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1 }),
      });
    });

    it("객관식과 주관식을 섞어 풀면 서버 채점과 자기 채점을 따로 센다 (D10)", () => {
      const state = run(
        createInitialState(4),
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
        INPUT,
        SUBMIT_START,
        REVEAL_SUCCESS,
        SHORT_ANSWER_RIGHT,
        SELECT,
        SUBMIT_START,
        SUBMIT_WRONG,
        NEXT,
        INPUT,
        SUBMIT_START,
        REVEAL_SUCCESS,
        SHORT_ANSWER_WRONG,
      );

      expect(state).toMatchObject({
        phase: "finished",
        totalQuestions: 4,
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 1, SHORT_ANSWER: 1 }),
      });
    });

    it("세 유형을 섞어 풀면 객관식, 주관식, 서술형을 합치지 않고 따로 센다 (D1, R5)", () => {
      const state = run(
        createInitialState(5),
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
        SELECT,
        SUBMIT_START,
        SUBMIT_CORRECT,
        NEXT,
        INPUT,
        SUBMIT_START,
        REVEAL_SUCCESS,
        SHORT_ANSWER_WRONG,
        INPUT,
        SUBMIT_START,
        REVEAL_SUCCESS,
        ESSAY_RIGHT,
      );

      expect(state).toMatchObject({
        phase: "finished",
        totalQuestions: 5,
        correctCounts: correctCountsOf({ MULTIPLE_CHOICE: 3, ESSAY: 1 }),
      });
    });

    it.each([
      {
        name: "주관식",
        right: SHORT_ANSWER_RIGHT,
        scored: { correctCounts: correctCountsOf({ SHORT_ANSWER: 1 }) },
      },
      {
        name: "서술형",
        right: ESSAY_RIGHT,
        scored: { correctCounts: correctCountsOf({ ESSAY: 1 }) },
      },
    ])(
      "실패 후 다시 제출해도 자기 채점을 한 번만 센다: $name",
      ({ right, scored }) => {
        const state = run(
          createInitialState(1),
          INPUT,
          SUBMIT_START,
          SUBMIT_FAILURE,
          SUBMIT_START,
          REVEAL_SUCCESS,
          right,
        );

        expect(state).toMatchObject({ phase: "finished", ...scored });
      },
    );
  });
});

describe("shouldSaveSelfGrade (D9, D12, D13)", () => {
  // 첫 문제에서 401을 받은 뒤 둘째 문제에서 모범 답안까지 본 상태
  const expiredRevealed = revealedAfterLoginExpired;

  it("회원이고 로그인 만료 전이면 저장한다", () => {
    expect(shouldSaveSelfGrade(revealed(), true)).toBe(true);
  });

  it("비회원이면 저장하지 않는다", () => {
    expect(shouldSaveSelfGrade(revealed(), false)).toBe(false);
  });

  it("회원이어도 자기 채점 저장에서 로그인 만료를 감지한 뒤에는 저장하지 않는다", () => {
    expect(expiredRevealed().loginExpiredAt).toBe(0);
    expect(shouldSaveSelfGrade(expiredRevealed(), true)).toBe(false);
  });

  it("회원이어도 객관식 응답에서 로그인 만료를 감지한 뒤에는 저장하지 않는다", () => {
    const state = run(
      gradedLoginExpired(),
      NEXT,
      INPUT,
      SUBMIT_START,
      REVEAL_SUCCESS,
    );

    expect(state.loginExpiredAt).toBe(0);
    expect(shouldSaveSelfGrade(state, true)).toBe(false);
  });

  it("만료 표시가 있는 상태의 비회원도 저장하지 않는다", () => {
    expect(shouldSaveSelfGrade(expiredRevealed(), false)).toBe(false);
  });
});

describe("selfGradeSaveFailureAction (D9, D12)", () => {
  it.each([
    { questionType: "SHORT_ANSWER" as const, correct: true },
    { questionType: "ESSAY" as const, correct: false },
  ])(
    "401이면 고른 결과를 실은 로그인 만료 액션을 만든다: $questionType $correct",
    ({ questionType, correct }) => {
      const error = new ApiError(401, "로그인이 필요합니다.");

      expect(selfGradeSaveFailureAction(error, questionType, correct)).toEqual({
        type: "selfGradeSaveUnauthorized",
        questionType,
        correct,
      });
    },
  );

  it.each([
    { name: "400", error: new ApiError(400, "잘못된 요청") },
    { name: "403", error: new ApiError(403, "권한 없음") },
    { name: "500", error: new ApiError(500, "서버 오류") },
    { name: "404 NotFoundError", error: new NotFoundError("없는 문제") },
    { name: "통신 오류", error: new TypeError("Failed to fetch") },
    { name: "일반 Error", error: new Error("오류") },
    { name: "문자열", error: "오류" },
    { name: "undefined", error: undefined },
    { name: "status 401만 가진 객체", error: { status: 401 } },
  ])(
    "401 ApiError가 아니면 다시 시도할 수 있는 실패 액션을 만든다: $name",
    ({ error }) => {
      expect(selfGradeSaveFailureAction(error, "SHORT_ANSWER", true)).toEqual({
        type: "selfGradeSaveFailure",
      });
    },
  );
});

describe("canSubmit (D7, R3, R4)", () => {
  const SELF_GRADED = [
    { name: "주관식", questionType: "SHORT_ANSWER" as const },
    { name: "서술형", questionType: "ESSAY" as const },
  ];

  it.each([
    { name: "객관식", questionType: "MULTIPLE_CHOICE" as const },
    ...SELF_GRADED,
  ])("아무것도 입력하지 않았으면 제출할 수 없다: $name", ({ questionType }) => {
    expect(canSubmit(noChoice(), questionType)).toBe(false);
  });

  it("객관식은 보기를 고르면 제출할 수 있다", () => {
    expect(canSubmit(chosen(), "MULTIPLE_CHOICE")).toBe(true);
  });

  it("객관식은 보기 없이 답만 있으면 제출할 수 없다", () => {
    expect(canSubmit(typed(), "MULTIPLE_CHOICE")).toBe(false);
  });

  it.each(SELF_GRADED)(
    "공백이 아닌 답이 있으면 제출할 수 있다: $name",
    ({ questionType }) => {
      expect(canSubmit(typed(), questionType)).toBe(true);
    },
  );

  it.each(SELF_GRADED)(
    "보기를 골랐어도 답이 없으면 제출할 수 없다: $name",
    ({ questionType }) => {
      expect(canSubmit(chosen(), questionType)).toBe(false);
    },
  );

  it.each([
    { name: "스페이스", blank: "   " },
    { name: "줄바꿈과 탭", blank: "\n\t \n" },
  ])("공백뿐인 답은 주관식·서술형 모두 제출할 수 없다: $name", ({ blank }) => {
    const state = run(noChoice(), inputText(blank));

    expect(state.textAnswer).toBe(blank);
    expect(canSubmit(state, "SHORT_ANSWER")).toBe(false);
    expect(canSubmit(state, "ESSAY")).toBe(false);
  });

  it("앞뒤 공백이 있어도 내용이 있으면 제출할 수 있다", () => {
    expect(canSubmit(run(noChoice(), inputText("  답  ")), "ESSAY")).toBe(true);
  });

  it("단계는 보지 않고 입력만으로 판단한다", () => {
    expect(submitting().phase).toBe("submitting");
    expect(canSubmit(submitting(), "MULTIPLE_CHOICE")).toBe(true);
    expect(graded().phase).toBe("graded");
    expect(canSubmit(graded(), "MULTIPLE_CHOICE")).toBe(true);
    expect(revealed().phase).toBe("revealed");
    expect(canSubmit(revealed(), "ESSAY")).toBe(true);
  });
});
