// 카테고리 폼 화면 검사 (D23). 구현할 파일: src/quiz/categoryForm.ts
//
//   export type CategoryFormValues = {
//     readonly slug: string; readonly name: string; readonly description: string; readonly sortOrder: string;
//   };
//   export type CategoryFormErrors = {
//     readonly slug?: string;      // "slug는 영어 소문자, 숫자, -로 50자 이내로 입력해 주세요." (mode가 "create"일 때만)
//     readonly name?: string;      // "이름을 입력해 주세요." (공백만 있어도)
//     readonly sortOrder?: string; // "표시 순서는 정수로 입력해 주세요." (parseSortOrder 규칙)
//   };
//   export type CategoryFormMode = "create" | "edit";
//   export function validateCategoryForm(form: CategoryFormValues, mode: CategoryFormMode): CategoryFormErrors
//     에러가 없으면 빈 객체 {}. slug는 ^[a-z0-9-]{1,50}$로 검사하고, 수정할 때는 보지 않는다. 설명은 보지 않는다.
//
//   export const EMPTY_CATEGORY_FORM: CategoryFormValues
//   export function formFromCategory(category: AdminCategoryResponse): CategoryFormValues
//   export function prepareCategorySubmit(form: CategoryFormValues, mode: "create"):
//     | { ok: false; errors: CategoryFormErrors }
//     | { ok: true; request: CategoryCreateRequest }
//   export function prepareCategorySubmit(form: CategoryFormValues, mode: "edit"):
//     | { ok: false; errors: CategoryFormErrors }
//     | { ok: true; request: CategoryUpdateRequest }   // { name, description, sortOrder }, slug 없음
import { describe, expect, it } from "vitest";
import type { AdminCategoryResponse } from "../api/admin";
import {
  EMPTY_CATEGORY_FORM,
  formFromCategory,
  prepareCategorySubmit,
  validateCategoryForm,
} from "./categoryForm";

const SLUG_ERROR = "slug는 영어 소문자, 숫자, -로 50자 이내로 입력해 주세요.";
const NAME_ERROR = "이름을 입력해 주세요.";
const SORT_ORDER_ERROR = "표시 순서는 정수로 입력해 주세요.";

function form(
  overrides: Partial<{
    slug: string;
    name: string;
    description: string;
    sortOrder: string;
  }> = {},
) {
  return {
    slug: "kotlin",
    name: "Kotlin",
    description: "",
    sortOrder: "0",
    ...overrides,
  };
}

describe("validateCategoryForm (D23)", () => {
  it("추가 폼 값이 규칙을 모두 지키면 에러가 없다. 빈 설명도 괜찮다", () => {
    expect(validateCategoryForm(form(), "create")).toEqual({});
  });

  it.each([
    "a",
    "a-1",
    "kotlin-coroutines",
    "abcdefghijklmnopqrstuvwxyz0123456789-abcdefghijklm",
  ])("추가할 때 slug '%s'는 받는다", (slug) => {
    expect(validateCategoryForm(form({ slug }), "create")).toEqual({});
  });

  it.each([
    "",
    "Kotlin",
    "kotlin_basic",
    "kotlin basic",
    " kotlin",
    "코틀린",
    "kotlin/basic",
    "abcdefghijklmnopqrstuvwxyz0123456789-abcdefghijklmn",
  ])("추가할 때 slug '%s'는 형식 에러다", (slug) => {
    expect(validateCategoryForm(form({ slug }), "create")).toEqual({
      slug: SLUG_ERROR,
    });
  });

  it("수정할 때는 slug를 보지 않는다", () => {
    expect(validateCategoryForm(form({ slug: "Old_Slug" }), "edit")).toEqual(
      {},
    );
  });

  it.each(["", "   ", "\t"])(
    "이름이 '%s'이면 이름 에러다 (추가·수정 모두)",
    (name) => {
      expect(validateCategoryForm(form({ name }), "create")).toEqual({
        name: NAME_ERROR,
      });
      expect(validateCategoryForm(form({ name }), "edit")).toEqual({
        name: NAME_ERROR,
      });
    },
  );

  it.each(["", "1.5", "x"])(
    "표시 순서가 '%s'이면 표시 순서 에러다",
    (sortOrder) => {
      expect(validateCategoryForm(form({ sortOrder }), "edit")).toEqual({
        sortOrder: SORT_ORDER_ERROR,
      });
    },
  );

  it("여러 칸이 틀리면 칸마다 에러를 함께 낸다", () => {
    expect(
      validateCategoryForm(
        form({ slug: "Bad Slug", name: " ", sortOrder: "" }),
        "create",
      ),
    ).toEqual({
      slug: SLUG_ERROR,
      name: NAME_ERROR,
      sortOrder: SORT_ORDER_ERROR,
    });
  });
});

describe("EMPTY_CATEGORY_FORM", () => {
  it("모든 칸이 비고 표시 순서는 0이다", () => {
    expect(EMPTY_CATEGORY_FORM).toEqual({
      slug: "",
      name: "",
      description: "",
      sortOrder: "0",
    });
  });
});

describe("formFromCategory", () => {
  function category(
    overrides: Partial<AdminCategoryResponse> = {},
  ): AdminCategoryResponse {
    return {
      slug: "java",
      name: "Java",
      description: "자바 기초",
      sortOrder: 10,
      questionCount: 3,
      ...overrides,
    };
  }

  it("응답 값을 폼 값으로 옮기고 표시 순서는 문자열로 바꾼다", () => {
    expect(formFromCategory(category())).toEqual({
      slug: "java",
      name: "Java",
      description: "자바 기초",
      sortOrder: "10",
    });
  });

  it("설명이 null이면 빈 문자열이다", () => {
    expect(formFromCategory(category({ description: null }))).toMatchObject({
      description: "",
    });
  });

  it("음수 표시 순서도 문자열로 바꾼다", () => {
    expect(formFromCategory(category({ sortOrder: -2 }))).toMatchObject({
      sortOrder: "-2",
    });
  });
});

describe("prepareCategorySubmit", () => {
  it.each(["create", "edit"] as const)(
    "%s에서 검사 에러가 있으면 ok가 false이고 errors는 validateCategoryForm 결과 그대로다",
    (mode) => {
      const values = form({ slug: "Bad Slug", name: " ", sortOrder: "x" });

      expect(prepareCategorySubmit(values, mode)).toEqual({
        ok: false,
        errors: validateCategoryForm(values, mode),
      });
    },
  );

  it("추가할 때 slug만 틀려도 ok가 false다", () => {
    expect(prepareCategorySubmit(form({ slug: "Bad" }), "create")).toEqual({
      ok: false,
      errors: { slug: SLUG_ERROR },
    });
  });

  it("값이 맞으면 표시 순서를 정수로 바꾼 요청을 만든다", () => {
    expect(
      prepareCategorySubmit(
        form({ description: "코틀린 기초", sortOrder: " 5 " }),
        "create",
      ),
    ).toEqual({
      ok: true,
      request: {
        slug: "kotlin",
        name: "Kotlin",
        description: "코틀린 기초",
        sortOrder: 5,
      },
    });
  });

  it("설명이 비어 있으면 null로 보낸다", () => {
    expect(prepareCategorySubmit(form({ description: "" }), "create")).toEqual({
      ok: true,
      request: {
        slug: "kotlin",
        name: "Kotlin",
        description: null,
        sortOrder: 0,
      },
    });
  });

  it("수정할 때는 slug 형식이 틀려도 요청을 만든다", () => {
    const result = prepareCategorySubmit(
      form({ slug: "Old_Slug", sortOrder: "-1" }),
      "edit",
    );

    expect(result.ok).toBe(true);
  });

  it("수정 요청에는 slug가 없고 이름·설명·표시 순서만 담는다", () => {
    const result = prepareCategorySubmit(
      form({ slug: "Old_Slug", description: "코틀린 기초", sortOrder: "-1" }),
      "edit",
    );

    if (!result.ok) {
      throw new Error("수정 요청을 만들지 못했다");
    }
    expect(result.request).toEqual({
      name: "Kotlin",
      description: "코틀린 기초",
      sortOrder: -1,
    });
    expect("slug" in result.request).toBe(false);
  });
});
