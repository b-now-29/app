// 관리자 API 함수 (API 계약). 구현할 파일: src/api/admin.ts
//
// 타입 (백엔드 DTO와 같은 이름·필드):
//   AdminCategoryResponse { slug, name, description: string | null, sortOrder, questionCount }
//   CategoryCreateRequest { slug, name, description: string | null, sortOrder }
//   CategoryUpdateRequest { name, description: string | null, sortOrder }
//   AdminChoiceResponse { id, content, correct }
//   AdminQuestionResponse { id, categorySlug, type: QuestionType, content, explanation: string | null,
//                           modelAnswer: string | null, sortOrder, choices: readonly AdminChoiceResponse[] }
//   ChoiceRequest { content, correct }
//   QuestionRequest { categorySlug, type, content, explanation: string | null, modelAnswer: string | null,
//                     sortOrder, choices: readonly ChoiceRequest[] }
//
// 함수 (실패 응답은 모두 toApiError로 만든 ApiError를 던진다. 화면이 401·403·400·409를 구분한다):
//   getAdminCategories(): Promise<readonly AdminCategoryResponse[]>
//   createCategory(request: CategoryCreateRequest): Promise<AdminCategoryResponse>
//   updateCategory(slug: string, request: CategoryUpdateRequest): Promise<AdminCategoryResponse>
//   deleteCategory(slug: string): Promise<void>
//   getAdminQuestions(categorySlug: string): Promise<readonly AdminQuestionResponse[]>
//   getAdminQuestion(id: number): Promise<AdminQuestionResponse>
//   createQuestion(request: QuestionRequest): Promise<AdminQuestionResponse>
//   updateQuestion(id: number, request: QuestionRequest): Promise<AdminQuestionResponse>
//   deleteQuestion(id: number): Promise<void>
//
// 회원 관리 (docs/features/admin-members.md API 계약, D6):
//   AdminUserResponse { id: number, email: string, role: "USER" | "ADMIN", createdAt: string | null }
//   getAdminUsers(): Promise<readonly AdminUserResponse[]>                      GET /api/v1/admin/users
//   changeUserRole(id: number, role: "USER" | "ADMIN"): Promise<AdminUserResponse>
//                                                    PUT /api/v1/admin/users/{id}/role  본문 { role }
//
// 통계 (docs/features/admin-stats.md API 계약, D9):
//   AdminTypeStatResponse { type: QuestionType, solved: number, correct: number }
//   AdminCategoryStatResponse { slug: string, name: string, types: readonly AdminTypeStatResponse[] }
//   AdminQuestionStatResponse { id: number, type: QuestionType, content: string, solvedUsers: number, correctUsers: number }
//   getAdminCategoryStats(): Promise<readonly AdminCategoryStatResponse[]>     GET /api/v1/admin/stats/categories
//   getAdminQuestionStats(categorySlug: string): Promise<readonly AdminQuestionStatResponse[]>
//                                  GET /api/v1/admin/stats/categories/{slug}/questions  (slug는 encodeURIComponent)
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  changeUserRole,
  createCategory,
  createQuestion,
  deleteCategory,
  deleteQuestion,
  getAdminCategories,
  getAdminCategoryStats,
  getAdminQuestion,
  getAdminQuestionStats,
  getAdminQuestions,
  getAdminUsers,
  updateCategory,
  updateQuestion,
} from "./admin";
import type {
  AdminCategoryResponse,
  AdminCategoryStatResponse,
  AdminQuestionResponse,
  AdminQuestionStatResponse,
  AdminUserResponse,
  QuestionRequest,
} from "./admin";
import { ApiError } from "./users";

const fetchMock = vi.spyOn(globalThis, "fetch");

const JSON_HEADERS = { "Content-Type": "application/json" };

const CATEGORY: AdminCategoryResponse = {
  slug: "java",
  name: "Java",
  description: "자바 기초",
  sortOrder: 10,
  questionCount: 5,
};

const QUESTION: AdminQuestionResponse = {
  id: 101,
  categorySlug: "java",
  type: "MULTIPLE_CHOICE",
  content: "문자열 내용을 비교할 때 쓰는 것은?",
  explanation: null,
  modelAnswer: null,
  sortOrder: 10,
  choices: [
    { id: 1001, content: "==", correct: false },
    { id: 1002, content: "equals()", correct: true },
  ],
};

const QUESTION_REQUEST: QuestionRequest = {
  categorySlug: "java",
  type: "MULTIPLE_CHOICE",
  content: "문자열 내용을 비교할 때 쓰는 것은?",
  explanation: null,
  modelAnswer: null,
  sortOrder: 10,
  choices: [
    { content: "==", correct: false },
    { content: "equals()", correct: true },
  ],
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

function problemResponse(status: number, detail: string): Response {
  return jsonResponse({ title: "실패", status, detail }, status);
}

async function caught(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

beforeEach(() => {
  fetchMock.mockReset();
});

describe("카테고리", () => {
  it("getAdminCategories는 관리자 카테고리 목록을 조회한다", async () => {
    fetchMock.mockResolvedValue(jsonResponse([CATEGORY]));

    await expect(getAdminCategories()).resolves.toEqual([CATEGORY]);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/categories");
  });

  it("createCategory는 요청 본문을 POST하고 만든 카테고리를 돌려준다 (R4)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(CATEGORY, 201));
    const request = {
      slug: "java",
      name: "Java",
      description: "자바 기초",
      sortOrder: 10,
    };

    await expect(createCategory(request)).resolves.toEqual(CATEGORY);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/categories", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    });
  });

  it("updateCategory는 slug를 인코딩한 경로로 PUT한다 (R4)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(CATEGORY));
    const request = { name: "Java", description: null, sortOrder: 10 };

    await expect(updateCategory("a/b", request)).resolves.toEqual(CATEGORY);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/categories/a%2Fb", {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    });
  });

  it("deleteCategory는 DELETE하고 204면 끝난다 (R6)", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(deleteCategory("java")).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/categories/java", {
      method: "DELETE",
    });
  });

  it.each([
    ["getAdminCategories", () => getAdminCategories()],
    [
      "createCategory",
      () =>
        createCategory({
          slug: "java",
          name: "Java",
          description: null,
          sortOrder: 0,
        }),
    ],
    [
      "updateCategory",
      () =>
        updateCategory("java", { name: "", description: null, sortOrder: 0 }),
    ],
    ["deleteCategory", () => deleteCategory("java")],
  ])(
    "%s가 실패하면 상태와 서버 메시지를 담은 ApiError를 던진다",
    async (_name, call) => {
      fetchMock.mockResolvedValue(
        problemResponse(409, "이미 있는 slug입니다."),
      );

      const error = await caught(call());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        status: 409,
        message: "이미 있는 slug입니다.",
      });
    },
  );

  it.each([401, 403])(
    "%i 응답도 상태를 담은 ApiError다 (loader가 로그인·홈으로 보낸다)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status, "거부"));

      const error = await caught(getAdminCategories());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status });
    },
  );
});

describe("문제", () => {
  it("getAdminQuestions는 카테고리를 인코딩한 쿼리로 조회한다", async () => {
    fetchMock.mockResolvedValue(jsonResponse([QUESTION]));

    await expect(getAdminQuestions("a&b")).resolves.toEqual([QUESTION]);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/questions?category=a%26b",
    );
  });

  it("getAdminQuestion은 id로 문제 하나를 정답 정보까지 조회한다 (R8)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(QUESTION));

    await expect(getAdminQuestion(101)).resolves.toEqual(QUESTION);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/questions/101");
  });

  it("createQuestion은 요청 본문을 POST하고 만든 문제를 돌려준다 (R5)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(QUESTION, 201));

    await expect(createQuestion(QUESTION_REQUEST)).resolves.toEqual(QUESTION);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/questions", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(QUESTION_REQUEST),
    });
  });

  it("updateQuestion은 id 경로로 PUT한다 (D6)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(QUESTION));

    await expect(updateQuestion(101, QUESTION_REQUEST)).resolves.toEqual(
      QUESTION,
    );
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/questions/101", {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(QUESTION_REQUEST),
    });
  });

  it("deleteQuestion은 DELETE하고 204면 끝난다 (R6)", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(deleteQuestion(101)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/questions/101", {
      method: "DELETE",
    });
  });

  it.each([
    ["getAdminQuestions", () => getAdminQuestions("java")],
    ["getAdminQuestion", () => getAdminQuestion(101)],
    ["createQuestion", () => createQuestion(QUESTION_REQUEST)],
    ["updateQuestion", () => updateQuestion(101, QUESTION_REQUEST)],
    ["deleteQuestion", () => deleteQuestion(101)],
  ])(
    "%s가 실패하면 상태와 서버 메시지를 담은 ApiError를 던진다",
    async (_name, call) => {
      fetchMock.mockResolvedValue(
        problemResponse(400, "객관식은 보기가 2개 이상이어야 합니다."),
      );

      const error = await caught(call());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        status: 400,
        message: "객관식은 보기가 2개 이상이어야 합니다.",
      });
    },
  );
});

describe("통계 (admin-stats)", () => {
  const CATEGORY_STATS: readonly AdminCategoryStatResponse[] = [
    {
      slug: "java",
      name: "Java",
      types: [
        { type: "MULTIPLE_CHOICE", solved: 12, correct: 9 },
        { type: "ESSAY", solved: 3, correct: 1 },
      ],
    },
    { slug: "spring", name: "Spring", types: [] },
  ];
  const QUESTION_STATS: readonly AdminQuestionStatResponse[] = [
    {
      id: 1,
      type: "MULTIPLE_CHOICE",
      content: "문자열 비교는?",
      solvedUsers: 5,
      correctUsers: 4,
    },
    {
      id: 2,
      type: "ESSAY",
      content: "equals()를 설명하세요.",
      solvedUsers: 0,
      correctUsers: 0,
    },
  ];

  it("getAdminCategoryStats는 카테고리 통계를 조회한다 (R2)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(CATEGORY_STATS));

    await expect(getAdminCategoryStats()).resolves.toEqual(CATEGORY_STATS);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/stats/categories");
  });

  it("getAdminQuestionStats는 slug를 인코딩한 경로로 문제 통계를 조회한다 (R3)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(QUESTION_STATS));

    await expect(getAdminQuestionStats("a/b")).resolves.toEqual(QUESTION_STATS);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/stats/categories/a%2Fb/questions",
    );
  });

  it.each([
    ["getAdminCategoryStats", 401, () => getAdminCategoryStats()],
    ["getAdminCategoryStats", 403, () => getAdminCategoryStats()],
    ["getAdminQuestionStats", 404, () => getAdminQuestionStats("nope")],
  ])(
    "%s가 %i로 실패하면 상태와 서버 메시지를 담은 ApiError를 던진다 (R5)",
    async (_name, status, call) => {
      fetchMock.mockResolvedValue(problemResponse(status, "실패 이유"));

      const error = await caught(call());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status, message: "실패 이유" });
    },
  );
});

describe("회원 (admin-members)", () => {
  const MEMBERS: readonly AdminUserResponse[] = [
    { id: 1, email: "test@example.com", role: "USER", createdAt: null },
    {
      id: 3,
      email: "new@example.com",
      role: "ADMIN",
      createdAt: "2026-11-13T05:12:30Z",
    },
  ];

  it("getAdminUsers는 회원 목록을 조회한다 (R1)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(MEMBERS));

    await expect(getAdminUsers()).resolves.toEqual(MEMBERS);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/users");
  });

  it("changeUserRole은 id 경로로 role을 PUT하고 바뀐 회원을 돌려준다 (R3)", async () => {
    const changed: AdminUserResponse = { ...MEMBERS[0], role: "ADMIN" };
    fetchMock.mockResolvedValue(jsonResponse(changed));

    await expect(changeUserRole(1, "ADMIN")).resolves.toEqual(changed);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/users/1/role", {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify({ role: "ADMIN" }),
    });
  });

  it.each([
    ["getAdminUsers", () => getAdminUsers()],
    ["changeUserRole", () => changeUserRole(1, "USER")],
  ])(
    "%s가 실패하면 상태와 ProblemDetail detail을 담은 ApiError를 던진다 (R4)",
    async (_name, call) => {
      fetchMock.mockResolvedValue(
        problemResponse(409, "자기 자신의 관리자 권한은 해제할 수 없습니다."),
      );

      const error = await caught(call());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        status: 409,
        message: "자기 자신의 관리자 권한은 해제할 수 없습니다.",
      });
    },
  );
});
