// 관리자 통계 문구 (docs/features/admin-stats.md R2, R3, 화면 흐름).
// 구현할 파일: src/quiz/adminStats.ts (React를 import하지 않는 순수 함수)
//   export function categoryTypeStatLine(stat: AdminTypeStatResponse): string
//     → "객관식 풀이 12건 중 정답 9건 (75%)". 유형 이름은 quizScore.ts의 QUESTION_TYPE_NAMES,
//       퍼센트는 accuracy.ts의 accuracyPercent (반올림)
//   export function questionStatLine(stat: Pick<AdminQuestionStatResponse, "solvedUsers" | "correctUsers">): string
//     → "5명 중 4명 정답 (80%)". 푼 회원이 0명이면 "풀이 기록 없음" (0으로 나누지 않는다)
import { describe, expect, it } from "vitest";
import { categoryTypeStatLine, questionStatLine } from "./adminStats";

describe("categoryTypeStatLine (R2, 화면 흐름)", () => {
  it.each([
    {
      stat: { type: "MULTIPLE_CHOICE", solved: 12, correct: 9 },
      line: "객관식 풀이 12건 중 정답 9건 (75%)",
    },
    {
      stat: { type: "SHORT_ANSWER", solved: 1, correct: 0 },
      line: "주관식 풀이 1건 중 정답 0건 (0%)",
    },
    {
      // 소수점 아래는 반올림한다 (accuracyPercent)
      stat: { type: "ESSAY", solved: 3, correct: 2 },
      line: "서술형 풀이 3건 중 정답 2건 (67%)",
    },
    {
      stat: { type: "ESSAY", solved: 4, correct: 4 },
      line: "서술형 풀이 4건 중 정답 4건 (100%)",
    },
  ] as const)("$line", ({ stat, line }) => {
    expect(categoryTypeStatLine(stat)).toBe(line);
  });
});

describe("questionStatLine (R3, 화면 흐름)", () => {
  it.each([
    { solvedUsers: 5, correctUsers: 4, line: "5명 중 4명 정답 (80%)" },
    { solvedUsers: 1, correctUsers: 0, line: "1명 중 0명 정답 (0%)" },
    { solvedUsers: 6, correctUsers: 5, line: "6명 중 5명 정답 (83%)" },
    { solvedUsers: 2, correctUsers: 2, line: "2명 중 2명 정답 (100%)" },
  ])("$line", ({ solvedUsers, correctUsers, line }) => {
    expect(questionStatLine({ solvedUsers, correctUsers })).toBe(line);
  });

  it("푼 회원이 0명이면 정답률 대신 풀이 기록 없음이다 (R3)", () => {
    expect(questionStatLine({ solvedUsers: 0, correctUsers: 0 })).toBe(
      "풀이 기록 없음",
    );
  });
});
