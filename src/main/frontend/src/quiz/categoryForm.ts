import { SORT_ORDER_ERROR, parseSortOrder } from "./questionDraft";

export type CategoryFormValues = {
  readonly slug: string;
  readonly name: string;
  readonly description: string;
  readonly sortOrder: string;
};

export type CategoryFormErrors = {
  readonly slug?: string;
  readonly name?: string;
  readonly sortOrder?: string;
};

const SLUG_PATTERN = /^[a-z0-9-]{1,50}$/;

// slug는 추가할 때만 검사한다 (수정에서는 바꿀 수 없다)
export function validateCategoryForm(
  form: CategoryFormValues,
  mode: "create" | "edit",
): CategoryFormErrors {
  return {
    ...(mode === "create" && !SLUG_PATTERN.test(form.slug)
      ? { slug: "slug는 영어 소문자, 숫자, -로 50자 이내로 입력해 주세요." }
      : {}),
    ...(form.name.trim() === "" ? { name: "이름을 입력해 주세요." } : {}),
    ...(parseSortOrder(form.sortOrder) === null
      ? { sortOrder: SORT_ORDER_ERROR }
      : {}),
  };
}
