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
//   export function validateCategoryForm(form: CategoryFormValues, mode: "create" | "edit"): CategoryFormErrors
//     에러가 없으면 빈 객체 {}. slug는 ^[a-z0-9-]{1,50}$로 검사하고, 수정할 때는 보지 않는다. 설명은 보지 않는다.
import { describe, expect, it } from "vitest";
import { validateCategoryForm } from "./categoryForm";

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
