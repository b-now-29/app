import type { QuestionType } from "./questions";
import { getJson, sendJson, sendWithoutBody } from "./http";

export type AdminCategoryResponse = {
  readonly slug: string;
  readonly name: string;
  readonly description: string | null;
  readonly sortOrder: number;
  readonly questionCount: number;
};

export type CategoryCreateRequest = {
  readonly slug: string;
  readonly name: string;
  readonly description: string | null;
  readonly sortOrder: number;
};

export type CategoryUpdateRequest = {
  readonly name: string;
  readonly description: string | null;
  readonly sortOrder: number;
};

export type AdminChoiceResponse = {
  readonly id: number;
  readonly content: string;
  readonly correct: boolean;
};

export type AdminQuestionResponse = {
  readonly id: number;
  readonly categorySlug: string;
  readonly type: QuestionType;
  readonly content: string;
  readonly explanation: string | null;
  readonly modelAnswer: string | null;
  readonly sortOrder: number;
  readonly choices: readonly AdminChoiceResponse[];
};

export type ChoiceRequest = {
  readonly content: string;
  readonly correct: boolean;
};

export type QuestionRequest = {
  readonly categorySlug: string;
  readonly type: QuestionType;
  readonly content: string;
  readonly explanation: string | null;
  readonly modelAnswer: string | null;
  readonly sortOrder: number;
  readonly choices: readonly ChoiceRequest[];
};

const BASE = "/api/v1/admin";

export type AdminUserRole = "USER" | "ADMIN";

export type AdminUserResponse = {
  readonly id: number;
  readonly email: string;
  readonly role: AdminUserRole;
  readonly nickname: string;
  readonly tag: number;
  // 이 기능 전에 가입한 회원은 null
  readonly createdAt: string | null;
};

export function getAdminUsers(): Promise<readonly AdminUserResponse[]> {
  return getJson(`${BASE}/users`);
}

export function changeUserRole(
  id: number,
  role: AdminUserRole,
): Promise<AdminUserResponse> {
  return sendJson("PUT", `${BASE}/users/${id}/role`, { role });
}

export type AdminTypeStatResponse = {
  readonly type: QuestionType;
  readonly solved: number;
  readonly correct: number;
};

export type AdminCategoryStatResponse = {
  readonly slug: string;
  readonly name: string;
  readonly types: readonly AdminTypeStatResponse[];
};

export type AdminQuestionStatResponse = {
  readonly id: number;
  readonly type: QuestionType;
  readonly content: string;
  readonly solvedUsers: number;
  readonly correctUsers: number;
};

export function getAdminCategoryStats(): Promise<
  readonly AdminCategoryStatResponse[]
> {
  return getJson(`${BASE}/stats/categories`);
}

export function getAdminQuestionStats(
  categorySlug: string,
): Promise<readonly AdminQuestionStatResponse[]> {
  return getJson(
    `${BASE}/stats/categories/${encodeURIComponent(categorySlug)}/questions`,
  );
}

export function getAdminCategories(): Promise<
  readonly AdminCategoryResponse[]
> {
  return getJson(`${BASE}/categories`);
}

export function createCategory(
  request: CategoryCreateRequest,
): Promise<AdminCategoryResponse> {
  return sendJson("POST", `${BASE}/categories`, request);
}

export function updateCategory(
  slug: string,
  request: CategoryUpdateRequest,
): Promise<AdminCategoryResponse> {
  return sendJson(
    "PUT",
    `${BASE}/categories/${encodeURIComponent(slug)}`,
    request,
  );
}

export function deleteCategory(slug: string): Promise<void> {
  return sendWithoutBody(
    "DELETE",
    `${BASE}/categories/${encodeURIComponent(slug)}`,
  );
}

export function getAdminQuestions(
  categorySlug: string,
): Promise<readonly AdminQuestionResponse[]> {
  return getJson(
    `${BASE}/questions?category=${encodeURIComponent(categorySlug)}`,
  );
}

export function getAdminQuestion(id: number): Promise<AdminQuestionResponse> {
  return getJson(`${BASE}/questions/${id}`);
}

export function createQuestion(
  request: QuestionRequest,
): Promise<AdminQuestionResponse> {
  return sendJson("POST", `${BASE}/questions`, request);
}

export function updateQuestion(
  id: number,
  request: QuestionRequest,
): Promise<AdminQuestionResponse> {
  return sendJson("PUT", `${BASE}/questions/${id}`, request);
}

export function deleteQuestion(id: number): Promise<void> {
  return sendWithoutBody("DELETE", `${BASE}/questions/${id}`);
}
