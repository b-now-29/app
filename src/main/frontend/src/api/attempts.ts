import type { QuestionType } from "./questions";
import { toApiError } from "./users";

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

// 비회원(401)을 로그인 화면으로 보내려면 상태 코드가 필요하므로 ApiError로 던진다
export async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

export function getCategoryStats(): Promise<readonly CategoryStatResponse[]> {
  return getJson("/api/v1/users/me/category-stats");
}

export function getRecentAttempts(): Promise<readonly AttemptResponse[]> {
  return getJson("/api/v1/users/me/attempts");
}
