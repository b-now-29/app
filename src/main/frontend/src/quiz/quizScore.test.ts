import { describe, expect, it } from "vitest";
import type { QuestionResponse, QuestionType } from "../api/questions";
import { countQuestionsByType, scoreLines } from "./quizScore";

function question(id: number, type: QuestionType): QuestionResponse {
  return { id, type, content: `문제 ${id}`, choices: [] };
}

describe("countQuestionsByType", () => {
  it("문제가 없으면 모두 0이다", () => {
    expect(countQuestionsByType([])).toEqual({
      multipleChoice: 0,
      shortAnswer: 0,
    });
  });

  it("객관식과 주관식을 따로 센다", () => {
    const questions = [
      question(1, "MULTIPLE_CHOICE"),
      question(2, "SHORT_ANSWER"),
      question(3, "MULTIPLE_CHOICE"),
    ];

    expect(countQuestionsByType(questions)).toEqual({
      multipleChoice: 2,
      shortAnswer: 1,
    });
  });

  it("한 유형만 있으면 다른 유형은 0이다", () => {
    expect(countQuestionsByType([question(1, "SHORT_ANSWER")])).toEqual({
      multipleChoice: 0,
      shortAnswer: 1,
    });
  });
});

describe("scoreLines (R6, D10)", () => {
  it("두 유형이 있으면 객관식, 주관식 순서로 점수를 합치지 않고 따로 만든다", () => {
    expect(scoreLines({ multipleChoice: 3, shortAnswer: 1 }, 2, 1)).toEqual([
      "객관식 3문제 중 2문제 정답",
      "주관식 1문제 중 1문제 맞음",
    ]);
  });

  it("객관식만 있으면 주관식 줄을 만들지 않는다", () => {
    expect(scoreLines({ multipleChoice: 3, shortAnswer: 0 }, 2, 0)).toEqual([
      "객관식 3문제 중 2문제 정답",
    ]);
  });

  it("주관식만 있으면 객관식 줄을 만들지 않는다", () => {
    expect(scoreLines({ multipleChoice: 0, shortAnswer: 2 }, 0, 1)).toEqual([
      "주관식 2문제 중 1문제 맞음",
    ]);
  });

  it("맞은 문제가 없어도 그 유형의 줄을 만든다", () => {
    expect(scoreLines({ multipleChoice: 2, shortAnswer: 1 }, 0, 0)).toEqual([
      "객관식 2문제 중 0문제 정답",
      "주관식 1문제 중 0문제 맞음",
    ]);
  });

  it("문제가 하나도 없으면 줄이 없다", () => {
    expect(scoreLines({ multipleChoice: 0, shortAnswer: 0 }, 0, 0)).toEqual([]);
  });
});
