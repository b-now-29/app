// 문제 유형 이름 표. 대상: src/quiz/questionType.ts
//
//   export const QUESTION_TYPE_NAMES: Record<QuestionType, string>
//   export const QUESTION_TYPES: readonly QuestionType[]  // 화면에 보여 줄 순서
import { describe, expect, it } from "vitest";
import { QUESTION_TYPE_NAMES, QUESTION_TYPES } from "./questionType";

describe("QUESTION_TYPE_NAMES", () => {
  it("유형마다 화면에 보여 줄 이름이 있다", () => {
    expect(QUESTION_TYPE_NAMES).toEqual({
      MULTIPLE_CHOICE: "객관식",
      SHORT_ANSWER: "주관식",
      ESSAY: "서술형",
    });
  });
});

describe("QUESTION_TYPES", () => {
  it("객관식, 주관식, 서술형 순서다", () => {
    expect(QUESTION_TYPES).toEqual([
      "MULTIPLE_CHOICE",
      "SHORT_ANSWER",
      "ESSAY",
    ]);
  });
});
