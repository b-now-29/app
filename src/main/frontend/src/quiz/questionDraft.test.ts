// 문제 폼 화면 검사 (D23), 보기 편집, 저장 요청 만들기. 대상: src/quiz/questionDraft.ts
// 표시 순서 변환(parseSortOrder)은 src/quiz/sortOrder.test.ts에서 본다.
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
//
//   export const MIN_CHOICES = 2
//   export function withChoiceContent(draft: QuestionDraft, index: number, content: string): QuestionDraft
//   export function withNewChoice(draft: QuestionDraft): QuestionDraft
//   export function withoutChoice(draft: QuestionDraft, index: number): QuestionDraft
//   export function canRemoveChoice(draft: QuestionDraft): boolean
//     모두 원래 draft를 바꾸지 않고 새 draft를 돌려준다.
//
//   export function prepareQuestionSubmit(draft: QuestionDraft):
//     | { ok: false; errors: QuestionDraftErrors }
//     | { ok: true; request: QuestionRequest }
import { describe, expect, it } from "vitest";
import {
  MIN_CHOICES,
  canRemoveChoice,
  emptyDraft,
  prepareQuestionSubmit,
  validateQuestionDraft,
  withChoiceContent,
  withNewChoice,
  withoutChoice,
} from "./questionDraft";
import type { QuestionDraft } from "./questionDraft";

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

describe("withChoiceContent", () => {
  it("그 index 보기의 내용만 바꾸고 key·정답과 다른 보기는 그대로 둔다", () => {
    const draft = multipleChoice({
      choices: [choice(1, "A", true), choice(2, "B"), choice(3, "C")],
    });

    const next = withChoiceContent(draft, 1, "바뀐 B");

    expect(next).toEqual({
      ...draft,
      choices: [choice(1, "A", true), choice(2, "바뀐 B"), choice(3, "C")],
    });
  });

  it("원래 draft는 바꾸지 않는다", () => {
    const draft = multipleChoice();
    const before = structuredClone(draft);

    withChoiceContent(draft, 0, "바뀐 A");

    expect(draft).toEqual(before);
  });
});

describe("withNewChoice", () => {
  it("끝에 내용이 비고 정답이 아닌 보기를 하나 더한다", () => {
    const draft = multipleChoice();

    const next = withNewChoice(draft);

    expect(next.choices).toHaveLength(3);
    expect(next.choices.slice(0, 2)).toEqual(draft.choices);
    expect(next.choices[2]).toMatchObject({ content: "", correct: false });
    expect({ ...next, choices: draft.choices }).toEqual(draft);
  });

  it("더한 보기의 key는 다른 보기와 겹치지 않는다", () => {
    const next = withNewChoice(
      withNewChoice(withNewChoice(emptyDraft("java"))),
    );

    const keys = next.choices.map((c) => c.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it("원래 draft는 바꾸지 않는다", () => {
    const draft = multipleChoice();
    const before = structuredClone(draft);

    withNewChoice(draft);

    expect(draft).toEqual(before);
  });
});

describe("withoutChoice", () => {
  it("보기가 MIN_CHOICES보다 많으면 그 index 보기를 지운다", () => {
    const draft = multipleChoice({
      choices: [choice(1, "A", true), choice(2, "B"), choice(3, "C")],
    });

    const next = withoutChoice(draft, 1);

    expect(next).toEqual({
      ...draft,
      choices: [choice(1, "A", true), choice(3, "C")],
    });
  });

  it("보기가 MIN_CHOICES개이면 지우지 않고 draft를 그대로 돌려준다", () => {
    const draft = multipleChoice();

    expect(withoutChoice(draft, 0)).toBe(draft);
    expect(draft.choices).toHaveLength(MIN_CHOICES);
  });

  it("원래 draft는 바꾸지 않는다", () => {
    const draft = multipleChoice({
      choices: [choice(1, "A", true), choice(2, "B"), choice(3, "C")],
    });
    const before = structuredClone(draft);

    withoutChoice(draft, 0);

    expect(draft).toEqual(before);
  });
});

describe("canRemoveChoice", () => {
  it.each([
    [2, false],
    [3, true],
    [5, true],
  ])("보기가 %i개이면 %s다", (count, expected) => {
    const draft = multipleChoice({
      choices: Array.from({ length: count }, (_, i) =>
        choice(i + 1, `보기 ${i + 1}`, i === 0),
      ),
    });

    expect(canRemoveChoice(draft)).toBe(expected);
  });
});

describe("prepareQuestionSubmit", () => {
  it("검사 에러가 있으면 ok가 false이고 errors는 validateQuestionDraft 결과 그대로다", () => {
    const draft = multipleChoice({
      content: "",
      sortOrder: "abc",
      choices: [choice(1, ""), choice(2, "B")],
    });

    expect(prepareQuestionSubmit(draft)).toEqual({
      ok: false,
      errors: validateQuestionDraft(draft),
    });
    expect(prepareQuestionSubmit(draft)).toEqual({
      ok: false,
      errors: {
        content: CONTENT_ERROR,
        sortOrder: SORT_ORDER_ERROR,
        choices: { 0: "보기 1 내용을 입력해 주세요." },
        correctChoice: CORRECT_ERROR,
      },
    });
  });

  it("표시 순서만 틀려도 ok가 false다", () => {
    expect(
      prepareQuestionSubmit(textQuestion("ESSAY", { sortOrder: "1.5" })),
    ).toEqual({ ok: false, errors: { sortOrder: SORT_ORDER_ERROR } });
  });

  it("객관식은 표시 순서를 정수로 바꾸고, key를 빼고, 모범 답안은 null로 보낸다", () => {
    const draft = multipleChoice({
      sortOrder: " 7 ",
      modelAnswer: "유형을 바꾸며 남은 값",
      choices: [choice(11, "A"), choice(12, "B", true)],
    });

    expect(prepareQuestionSubmit(draft)).toEqual({
      ok: true,
      request: {
        categorySlug: "java",
        type: "MULTIPLE_CHOICE",
        content: "문제 내용",
        explanation: null,
        modelAnswer: null,
        sortOrder: 7,
        choices: [
          { content: "A", correct: false },
          { content: "B", correct: true },
        ],
      },
    });
  });

  it.each(["SHORT_ANSWER", "ESSAY"] as const)(
    "%s는 모범 답안을 보내고 남은 보기는 빈 배열로 보낸다",
    (type) => {
      const draft = textQuestion(type, {
        sortOrder: "-3",
        choices: [choice(1, "남은 보기", true), choice(2, "")],
      });

      expect(prepareQuestionSubmit(draft)).toEqual({
        ok: true,
        request: {
          categorySlug: "java",
          type,
          content: "문제 내용",
          explanation: null,
          modelAnswer: "모범 답안",
          sortOrder: -3,
          choices: [],
        },
      });
    },
  );

  it("해설이 있으면 그대로, 비어 있으면 null로 보낸다", () => {
    const withExplanation = prepareQuestionSubmit(
      multipleChoice({ explanation: "해설" }),
    );
    const withoutExplanation = prepareQuestionSubmit(
      multipleChoice({ explanation: "" }),
    );

    expect(withExplanation).toMatchObject({
      ok: true,
      request: { explanation: "해설" },
    });
    expect(withoutExplanation).toMatchObject({
      ok: true,
      request: { explanation: null },
    });
  });
});
