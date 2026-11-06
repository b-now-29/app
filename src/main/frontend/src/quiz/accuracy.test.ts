import { describe, expect, it } from "vitest";
import { accuracyLine, accuracyPercent } from "./accuracy";

describe("accuracyPercent (R6)", () => {
  it.each([
    { solved: 1, correct: 1, percent: 100 },
    { solved: 1, correct: 0, percent: 0 },
    { solved: 4, correct: 2, percent: 50 },
    // 소수점 아래는 반올림한다
    { solved: 6, correct: 5, percent: 83 },
    { solved: 3, correct: 2, percent: 67 },
    { solved: 3, correct: 1, percent: 33 },
    { solved: 8, correct: 1, percent: 13 },
  ])(
    "$solved문제 중 $correct문제를 맞히면 $percent%다",
    ({ solved, correct, percent }) => {
      expect(accuracyPercent(solved, correct)).toBe(percent);
    },
  );
});

describe("accuracyLine (R6, D11)", () => {
  it.each([
    {
      stat: {
        type: "MULTIPLE_CHOICE",
        solvedQuestions: 6,
        correctQuestions: 5,
      },
      line: "객관식 6문제 중 정답 5문제 (83%)",
    },
    {
      stat: { type: "SHORT_ANSWER", solvedQuestions: 1, correctQuestions: 0 },
      line: "주관식 1문제 중 정답 0문제 (0%)",
    },
    {
      stat: { type: "ESSAY", solvedQuestions: 4, correctQuestions: 2 },
      line: "서술형 4문제 중 정답 2문제 (50%)",
    },
  ] as const)("$line", ({ stat, line }) => {
    expect(accuracyLine(stat)).toBe(line);
  });
});
