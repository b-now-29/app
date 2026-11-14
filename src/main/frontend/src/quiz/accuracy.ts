import type { TypeStatResponse } from "../api/attempts";
import { QUESTION_TYPE_NAMES } from "./questionType";

// 소수점 아래는 반올림한다
export function accuracyPercent(solved: number, correct: number): number {
  return Math.round((correct / solved) * 100);
}

export function accuracyLine(stat: TypeStatResponse): string {
  const percent = accuracyPercent(stat.solvedQuestions, stat.correctQuestions);
  return `${QUESTION_TYPE_NAMES[stat.type]} ${stat.solvedQuestions}문제 중 정답 ${stat.correctQuestions}문제 (${percent}%)`;
}
