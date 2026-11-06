// 표시 순서 변환·검증 (D19)과 문제 폼 화면 검사 (D23). 대상: src/quiz/questionDraft.ts
//
//   export function parseSortOrder(value: string): number | null
//     앞뒤 공백을 무시하고 정수 문자열이면 그 수를, 비었거나 정수가 아니면 null을 돌려준다.
//
//   export type QuestionDraftErrors = {
//     readonly content?: string;        // "문제 내용을 입력해 주세요."
//     readonly modelAnswer?: string;    // "모범 답안을 입력해 주세요." (주관식·서술형만)
//     readonly choices?: Readonly<Record<number, string>>; // 보기 index(0부터) → "보기 N 내용을 입력해 주세요." (N은 1부터, 객관식만)
//     readonly correctChoice?: string;  // "정답 보기를 하나 선택해 주세요." (객관식만)
//     readonly sortOrder?: string;      // "표시 순서는 정수로 입력해 주세요."
//   };
//   export function validateQuestionDraft(draft: QuestionDraft): QuestionDraftErrors
//     에러가 없으면 빈 객체 {}. 에러가 없는 칸의 키는 넣지 않는다. 공백만 있는 값은 빈 값으로 본다.
import { describe, expect, it } from "vitest";
import { parseSortOrder, validateQuestionDraft } from "./questionDraft";
import type { QuestionDraft } from "./questionDraft";

describe("parseSortOrder (D19)", () => {
  it.each([
    ["0", 0],
    ["30", 30],
    ["-5", -5],
    [" 7 ", 7],
  ])("'%s'는 정수 %i이다", (value, expected) => {
    expect(parseSortOrder(value)).toBe(expected);
  });

  it.each(["", "   ", "1.5", "-0.1", "abc", "1a", "NaN", "Infinity"])(
    "'%s'는 정수가 아니므로 null이다",
    (value) => {
      expect(parseSortOrder(value)).toBeNull();
    },
  );
});

const CONTENT_ERROR = "문제 내용을 입력해 주세요.";
const MODEL_ANSWER_ERROR = "모범 답안을 입력해 주세요.";
const CORRECT_ERROR = "정답 보기를 하나 선택해 주세요.";
const SORT_ORDER_ERROR = "표시 순서는 정수로 입력해 주세요.";

function choice(key: number, content: string, correct = false) {
  return { key, content, correct };
}

function multipleChoice(overrides: Partial<QuestionDraft> = {}): QuestionDraft {
  return {
    categorySlug: "java",
    type: "MULTIPLE_CHOICE",
    content: "문제 내용",
    explanation: "",
    modelAnswer: "",
    sortOrder: "0",
    choices: [choice(1, "A", true), choice(2, "B")],
    ...overrides,
  };
}

function textQuestion(
  type: "SHORT_ANSWER" | "ESSAY",
  overrides: Partial<QuestionDraft> = {},
): QuestionDraft {
  return {
    categorySlug: "java",
    type,
    content: "문제 내용",
    explanation: "",
    modelAnswer: "모범 답안",
    sortOrder: "10",
    choices: [],
    ...overrides,
  };
}

describe("validateQuestionDraft (D23)", () => {
  it("규칙을 모두 지킨 객관식은 에러가 없다", () => {
    expect(validateQuestionDraft(multipleChoice())).toEqual({});
  });

  it.each(["SHORT_ANSWER", "ESSAY"] as const)(
    "규칙을 모두 지킨 %s는 에러가 없다",
    (type) => {
      expect(validateQuestionDraft(textQuestion(type))).toEqual({});
    },
  );

  it.each(["", "   ", "\n\t"])(
    "문제 내용이 '%s'이면 내용 에러다 (공백만 있어도 빈 값)",
    (content) => {
      expect(validateQuestionDraft(multipleChoice({ content }))).toEqual({
        content: CONTENT_ERROR,
      });
    },
  );

  it("객관식의 빈 보기는 1부터 센 번호로 칸마다 에러를 낸다", () => {
    const draft = multipleChoice({
      choices: [choice(1, "A", true), choice(2, " "), choice(3, "")],
    });

    expect(validateQuestionDraft(draft)).toEqual({
      choices: {
        1: "보기 2 내용을 입력해 주세요.",
        2: "보기 3 내용을 입력해 주세요.",
      },
    });
  });

  it("객관식에서 정답을 고르지 않으면 정답 에러다", () => {
    const draft = multipleChoice({
      choices: [choice(1, "A"), choice(2, "B")],
    });

    expect(validateQuestionDraft(draft)).toEqual({
      correctChoice: CORRECT_ERROR,
    });
  });

  it("빈 새 객관식은 내용·보기 둘·정답 에러를 함께 낸다", () => {
    const draft = multipleChoice({
      content: "",
      choices: [choice(1, ""), choice(2, "")],
    });

    expect(validateQuestionDraft(draft)).toEqual({
      content: CONTENT_ERROR,
      choices: {
        0: "보기 1 내용을 입력해 주세요.",
        1: "보기 2 내용을 입력해 주세요.",
      },
      correctChoice: CORRECT_ERROR,
    });
  });

  it("객관식은 모범 답안을 보지 않는다", () => {
    expect(validateQuestionDraft(multipleChoice({ modelAnswer: "" }))).toEqual(
      {},
    );
    expect(
      validateQuestionDraft(multipleChoice({ modelAnswer: "남은 값" })),
    ).toEqual({});
  });

  it.each([
    ["SHORT_ANSWER", ""],
    ["SHORT_ANSWER", "   "],
    ["ESSAY", ""],
    ["ESSAY", "\n"],
  ] as const)(
    "%s의 모범 답안이 '%s'이면 모범 답안 에러다",
    (type, modelAnswer) => {
      expect(
        validateQuestionDraft(textQuestion(type, { modelAnswer })),
      ).toEqual({ modelAnswer: MODEL_ANSWER_ERROR });
    },
  );

  it("주관식·서술형은 보기와 정답을 보지 않는다 (유형을 바꾸며 남은 빈 보기가 있어도)", () => {
    const draft = textQuestion("ESSAY", {
      choices: [choice(1, ""), choice(2, "")],
    });

    expect(validateQuestionDraft(draft)).toEqual({});
  });

  it.each(["", "1.5", "abc"])(
    "표시 순서가 '%s'이면 parseSortOrder 규칙대로 표시 순서 에러다",
    (sortOrder) => {
      expect(validateQuestionDraft(multipleChoice({ sortOrder }))).toEqual({
        sortOrder: SORT_ORDER_ERROR,
      });
      expect(
        validateQuestionDraft(textQuestion("SHORT_ANSWER", { sortOrder })),
      ).toEqual({ sortOrder: SORT_ORDER_ERROR });
    },
  );

  it("음수 표시 순서는 정수라 받는다", () => {
    expect(validateQuestionDraft(multipleChoice({ sortOrder: "-3" }))).toEqual(
      {},
    );
  });
});
