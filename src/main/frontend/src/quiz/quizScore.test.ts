import { describe, expect, it } from "vitest";
import type { QuestionResponse, QuestionType } from "../api/questions";
import { countQuestionsByType, scoreLines } from "./quizScore";

function question(id: number, type: QuestionType): QuestionResponse {
  return { id, type, content: `문제 ${id}`, choices: [] };
}

// 값을 준 유형 말고는 모두 0인 유형별 수를 만든다
const countsOf = (
  counts: Partial<Record<QuestionType, number>> = {},
): Record<QuestionType, number> => ({
  MULTIPLE_CHOICE: 0,
  SHORT_ANSWER: 0,
  ESSAY: 0,
  ...counts,
});

describe("countQuestionsByType (D9)", () => {
  it("문제가 없으면 모두 0이다", () => {
    expect(countQuestionsByType([])).toEqual({
      MULTIPLE_CHOICE: 0,
      SHORT_ANSWER: 0,
      ESSAY: 0,
    });
  });

  it("객관식과 주관식을 따로 센다", () => {
    const questions = [
      question(1, "MULTIPLE_CHOICE"),
      question(2, "SHORT_ANSWER"),
      question(3, "MULTIPLE_CHOICE"),
    ];

    expect(countQuestionsByType(questions)).toEqual({
      MULTIPLE_CHOICE: 2,
      SHORT_ANSWER: 1,
      ESSAY: 0,
    });
  });

  it("한 유형만 있으면 다른 유형은 0이다", () => {
    expect(countQuestionsByType([question(1, "SHORT_ANSWER")])).toEqual({
      MULTIPLE_CHOICE: 0,
      SHORT_ANSWER: 1,
      ESSAY: 0,
    });
  });

  it("세 유형을 따로 센다 (R5)", () => {
    const questions = [
      question(1, "MULTIPLE_CHOICE"),
      question(2, "ESSAY"),
      question(3, "SHORT_ANSWER"),
      question(4, "ESSAY"),
      question(5, "MULTIPLE_CHOICE"),
      question(6, "ESSAY"),
    ];

    expect(countQuestionsByType(questions)).toEqual({
      MULTIPLE_CHOICE: 2,
      SHORT_ANSWER: 1,
      ESSAY: 3,
    });
  });

  it("서술형을 주관식 개수에 합치지 않는다 (R5)", () => {
    expect(countQuestionsByType([question(1, "ESSAY")])).toEqual({
      MULTIPLE_CHOICE: 0,
      SHORT_ANSWER: 0,
      ESSAY: 1,
    });
  });
});

describe("scoreLines (R5, D1, D11)", () => {
  it("세 유형이 있으면 객관식, 주관식, 서술형 순서로 정답과 오답 수를 함께 만든다", () => {
    expect(
      scoreLines(
        { MULTIPLE_CHOICE: 5, SHORT_ANSWER: 3, ESSAY: 4 },
        { MULTIPLE_CHOICE: 4, SHORT_ANSWER: 1, ESSAY: 2 },
      ),
    ).toEqual([
      "객관식 5문제 중 정답 4문제, 오답 1문제",
      "주관식 3문제 중 정답 1문제, 오답 2문제",
      "서술형 4문제 중 정답 2문제, 오답 2문제",
    ]);
  });

  it.each([
    {
      name: "객관식",
      type: "MULTIPLE_CHOICE",
      line: "객관식 3문제 중 정답 2문제, 오답 1문제",
    },
    {
      name: "주관식",
      type: "SHORT_ANSWER",
      line: "주관식 3문제 중 정답 2문제, 오답 1문제",
    },
    {
      name: "서술형",
      type: "ESSAY",
      line: "서술형 3문제 중 정답 2문제, 오답 1문제",
    },
  ] as const)(
    "한 유형만 있으면 그 유형의 줄만 만든다: $name",
    ({ type, line }) => {
      expect(
        scoreLines(countsOf({ [type]: 3 }), countsOf({ [type]: 2 })),
      ).toEqual([line]);
    },
  );

  it.each([
    {
      name: "서술형",
      counts: countsOf({ MULTIPLE_CHOICE: 3, SHORT_ANSWER: 1 }),
      correctCounts: countsOf({ MULTIPLE_CHOICE: 3, SHORT_ANSWER: 1 }),
      lines: [
        "객관식 3문제 중 정답 3문제, 오답 0문제",
        "주관식 1문제 중 정답 1문제, 오답 0문제",
      ],
    },
    {
      name: "주관식",
      counts: countsOf({ MULTIPLE_CHOICE: 2, ESSAY: 1 }),
      correctCounts: countsOf({ MULTIPLE_CHOICE: 1, ESSAY: 1 }),
      lines: [
        "객관식 2문제 중 정답 1문제, 오답 1문제",
        "서술형 1문제 중 정답 1문제, 오답 0문제",
      ],
    },
    {
      name: "객관식",
      counts: countsOf({ SHORT_ANSWER: 2, ESSAY: 2 }),
      correctCounts: countsOf({ SHORT_ANSWER: 1, ESSAY: 2 }),
      lines: [
        "주관식 2문제 중 정답 1문제, 오답 1문제",
        "서술형 2문제 중 정답 2문제, 오답 0문제",
      ],
    },
  ])(
    "없는 유형의 줄을 빼고 나머지 순서를 유지한다: $name",
    ({ counts, correctCounts, lines }) => {
      expect(scoreLines(counts, correctCounts)).toEqual(lines);
    },
  );

  it("문제가 하나도 없으면 줄이 없다", () => {
    expect(scoreLines(countsOf(), countsOf())).toEqual([]);
  });

  it("유형의 개수와 정답 수를 합산하지 않는다", () => {
    expect(
      scoreLines(
        countsOf({ SHORT_ANSWER: 1, ESSAY: 1 }),
        countsOf({ ESSAY: 1 }),
      ),
    ).toEqual([
      "주관식 1문제 중 정답 0문제, 오답 1문제",
      "서술형 1문제 중 정답 1문제, 오답 0문제",
    ]);
  });

  it("정답이 없으면 정답 0문제로 줄을 만든다", () => {
    expect(
      scoreLines({ MULTIPLE_CHOICE: 2, SHORT_ANSWER: 1, ESSAY: 3 }, countsOf()),
    ).toEqual([
      "객관식 2문제 중 정답 0문제, 오답 2문제",
      "주관식 1문제 중 정답 0문제, 오답 1문제",
      "서술형 3문제 중 정답 0문제, 오답 3문제",
    ]);
  });

  it("전부 정답이면 오답 0문제로 줄을 만든다", () => {
    const counts = { MULTIPLE_CHOICE: 2, SHORT_ANSWER: 1, ESSAY: 3 };

    expect(scoreLines(counts, counts)).toEqual([
      "객관식 2문제 중 정답 2문제, 오답 0문제",
      "주관식 1문제 중 정답 1문제, 오답 0문제",
      "서술형 3문제 중 정답 3문제, 오답 0문제",
    ]);
  });

  it("맞음, 틀림 표현을 쓰지 않는다 (D11)", () => {
    const lines = scoreLines(
      { MULTIPLE_CHOICE: 2, SHORT_ANSWER: 2, ESSAY: 2 },
      { MULTIPLE_CHOICE: 1, SHORT_ANSWER: 1, ESSAY: 1 },
    );

    expect(lines).toHaveLength(3);
    lines.forEach((line) => expect(line).not.toMatch(/맞음|틀림/));
  });
});
