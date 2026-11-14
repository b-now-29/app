import type { QuestionType } from "./questions";
import { getJson } from "./http";

export type TypeStatResponse = {
  readonly type: QuestionType;
  readonly solvedQuestions: number;
  readonly correctQuestions: number;
};

export type CategoryStatResponse = {
  readonly slug: string;
  readonly name: string;
  readonly types: readonly TypeStatResponse[];
};

export type AttemptResponse = {
  readonly id: number;
  readonly questionId: number;
  readonly questionContent: string;
  readonly questionType: QuestionType;
  readonly categoryName: string;
  readonly correct: boolean;
  readonly attemptedAt: string;
};

export function getCategoryStats(): Promise<readonly CategoryStatResponse[]> {
  return getJson("/api/v1/users/me/category-stats");
}

export function getRecentAttempts(): Promise<readonly AttemptResponse[]> {
  return getJson("/api/v1/users/me/attempts");
}
