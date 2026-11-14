import type { QuestionType } from "./questions";
import { toApiError } from "./users";

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

const JSON_HEADERS = { "Content-Type": "application/json" };

// 실패(401·403·400·409 등)는 상태 코드와 서버 메시지를 담은 ApiError로 던진다
async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

async function expectNoContent(response: Response): Promise<void> {
  if (!response.ok) {
    throw await toApiError(response);
  }
}

function sendJson(method: "POST" | "PUT", url: string, body: unknown) {
  return fetch(url, {
    method,
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  });
}

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

export async function getAdminUsers(): Promise<readonly AdminUserResponse[]> {
  return readJson(await fetch(`${BASE}/users`));
}

export async function changeUserRole(
  id: number,
  role: AdminUserRole,
): Promise<AdminUserResponse> {
  return readJson(await sendJson("PUT", `${BASE}/users/${id}/role`, { role }));
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

export async function getAdminCategoryStats(): Promise<
  readonly AdminCategoryStatResponse[]
> {
  return readJson(await fetch(`${BASE}/stats/categories`));
}

export async function getAdminQuestionStats(
  categorySlug: string,
): Promise<readonly AdminQuestionStatResponse[]> {
  return readJson(
    await fetch(
      `${BASE}/stats/categories/${encodeURIComponent(categorySlug)}/questions`,
    ),
  );
}

export async function getAdminCategories(): Promise<
  readonly AdminCategoryResponse[]
> {
  return readJson(await fetch(`${BASE}/categories`));
}

export async function createCategory(
  request: CategoryCreateRequest,
): Promise<AdminCategoryResponse> {
  return readJson(await sendJson("POST", `${BASE}/categories`, request));
}

export async function updateCategory(
  slug: string,
  request: CategoryUpdateRequest,
): Promise<AdminCategoryResponse> {
  return readJson(
    await sendJson(
      "PUT",
      `${BASE}/categories/${encodeURIComponent(slug)}`,
      request,
    ),
  );
}

export async function deleteCategory(slug: string): Promise<void> {
  return expectNoContent(
    await fetch(`${BASE}/categories/${encodeURIComponent(slug)}`, {
      method: "DELETE",
    }),
  );
}

export async function getAdminQuestions(
  categorySlug: string,
): Promise<readonly AdminQuestionResponse[]> {
  return readJson(
    await fetch(
      `${BASE}/questions?category=${encodeURIComponent(categorySlug)}`,
    ),
  );
}

export async function getAdminQuestion(
  id: number,
): Promise<AdminQuestionResponse> {
  return readJson(await fetch(`${BASE}/questions/${id}`));
}

export async function createQuestion(
  request: QuestionRequest,
): Promise<AdminQuestionResponse> {
  return readJson(await sendJson("POST", `${BASE}/questions`, request));
}

export async function updateQuestion(
  id: number,
  request: QuestionRequest,
): Promise<AdminQuestionResponse> {
  return readJson(await sendJson("PUT", `${BASE}/questions/${id}`, request));
}

export async function deleteQuestion(id: number): Promise<void> {
  return expectNoContent(
    await fetch(`${BASE}/questions/${id}`, { method: "DELETE" }),
  );
}
