import { z } from "zod";
import { QuestionTypeSchema, type QuestionType } from "./questions";
import { getJson, sendJson, sendWithoutBody } from "./http";

const AdminCategorySchema = z
  .object({
    slug: z.string(),
    name: z.string(),
    description: z.string().nullable(),
    sortOrder: z.number(),
    questionCount: z.number(),
  })
  .readonly();
export type AdminCategoryResponse = z.infer<typeof AdminCategorySchema>;

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

const AdminChoiceSchema = z
  .object({
    id: z.number(),
    content: z.string(),
    correct: z.boolean(),
  })
  .readonly();
export type AdminChoiceResponse = z.infer<typeof AdminChoiceSchema>;

const AdminQuestionSchema = z
  .object({
    id: z.number(),
    categorySlug: z.string(),
    type: QuestionTypeSchema,
    content: z.string(),
    explanation: z.string().nullable(),
    modelAnswer: z.string().nullable(),
    sortOrder: z.number(),
    choices: z.array(AdminChoiceSchema).readonly(),
  })
  .readonly();
export type AdminQuestionResponse = z.infer<typeof AdminQuestionSchema>;

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

export const AdminUserRoleSchema = z.enum(["USER", "ADMIN"]);
export type AdminUserRole = z.infer<typeof AdminUserRoleSchema>;

const AdminUserSchema = z
  .object({
    id: z.number(),
    email: z.string(),
    role: AdminUserRoleSchema,
    nickname: z.string(),
    tag: z.number(),
    // 이 기능 전에 가입한 회원은 null
    createdAt: z.string().nullable(),
  })
  .readonly();
export type AdminUserResponse = z.infer<typeof AdminUserSchema>;

const AdminUserListSchema = z.array(AdminUserSchema).readonly();

export function getAdminUsers(): Promise<readonly AdminUserResponse[]> {
  return getJson(`${BASE}/users`, AdminUserListSchema);
}

export function changeUserRole(
  id: number,
  role: AdminUserRole,
): Promise<AdminUserResponse> {
  return sendJson("PUT", `${BASE}/users/${id}/role`, { role }, AdminUserSchema);
}

const AdminTypeStatSchema = z
  .object({
    type: QuestionTypeSchema,
    solved: z.number(),
    correct: z.number(),
  })
  .readonly();
export type AdminTypeStatResponse = z.infer<typeof AdminTypeStatSchema>;

const AdminCategoryStatSchema = z
  .object({
    slug: z.string(),
    name: z.string(),
    types: z.array(AdminTypeStatSchema).readonly(),
  })
  .readonly();
export type AdminCategoryStatResponse = z.infer<typeof AdminCategoryStatSchema>;

const AdminQuestionStatSchema = z
  .object({
    id: z.number(),
    type: QuestionTypeSchema,
    content: z.string(),
    solvedUsers: z.number(),
    correctUsers: z.number(),
  })
  .readonly();
export type AdminQuestionStatResponse = z.infer<typeof AdminQuestionStatSchema>;

const AdminCategoryStatListSchema = z.array(AdminCategoryStatSchema).readonly();
const AdminQuestionStatListSchema = z.array(AdminQuestionStatSchema).readonly();
const AdminCategoryListSchema = z.array(AdminCategorySchema).readonly();
const AdminQuestionListSchema = z.array(AdminQuestionSchema).readonly();

export function getAdminCategoryStats(): Promise<
  readonly AdminCategoryStatResponse[]
> {
  return getJson(`${BASE}/stats/categories`, AdminCategoryStatListSchema);
}

export function getAdminQuestionStats(
  categorySlug: string,
): Promise<readonly AdminQuestionStatResponse[]> {
  return getJson(
    `${BASE}/stats/categories/${encodeURIComponent(categorySlug)}/questions`,
    AdminQuestionStatListSchema,
  );
}

export function getAdminCategories(): Promise<
  readonly AdminCategoryResponse[]
> {
  return getJson(`${BASE}/categories`, AdminCategoryListSchema);
}

export function createCategory(
  request: CategoryCreateRequest,
): Promise<AdminCategoryResponse> {
  return sendJson("POST", `${BASE}/categories`, request, AdminCategorySchema);
}

export function updateCategory(
  slug: string,
  request: CategoryUpdateRequest,
): Promise<AdminCategoryResponse> {
  return sendJson(
    "PUT",
    `${BASE}/categories/${encodeURIComponent(slug)}`,
    request,
    AdminCategorySchema,
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
    AdminQuestionListSchema,
  );
}

export function getAdminQuestion(id: number): Promise<AdminQuestionResponse> {
  return getJson(`${BASE}/questions/${id}`, AdminQuestionSchema);
}

export function createQuestion(
  request: QuestionRequest,
): Promise<AdminQuestionResponse> {
  return sendJson("POST", `${BASE}/questions`, request, AdminQuestionSchema);
}

export function updateQuestion(
  id: number,
  request: QuestionRequest,
): Promise<AdminQuestionResponse> {
  return sendJson(
    "PUT",
    `${BASE}/questions/${id}`,
    request,
    AdminQuestionSchema,
  );
}

export function deleteQuestion(id: number): Promise<void> {
  return sendWithoutBody("DELETE", `${BASE}/questions/${id}`);
}
