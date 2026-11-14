import type {
  AdminCategoryResponse,
  CategoryCreateRequest,
  CategoryUpdateRequest,
} from "../api/admin";
import { SORT_ORDER_ERROR, parseSortOrder } from "./sortOrder";

export type CategoryFormMode = "create" | "edit";

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

export const EMPTY_CATEGORY_FORM: CategoryFormValues = {
  slug: "",
  name: "",
  description: "",
  sortOrder: "0",
};

export function formFromCategory(
  category: AdminCategoryResponse,
): CategoryFormValues {
  return {
    slug: category.slug,
    name: category.name,
    description: category.description ?? "",
    sortOrder: String(category.sortOrder),
  };
}

const SLUG_PATTERN = /^[a-z0-9-]{1,50}$/;

// slug는 추가할 때만 검사한다 (수정에서는 바꿀 수 없다)
export function validateCategoryForm(
  form: CategoryFormValues,
  mode: CategoryFormMode,
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

export type CategorySubmitResult<R> =
  | { readonly ok: false; readonly errors: CategoryFormErrors }
  | { readonly ok: true; readonly request: R };

// 검사를 통과하면 저장 요청을 만든다. 추가는 slug를 포함하고, 수정은 slug 없이 만든다.
export function prepareCategorySubmit(
  form: CategoryFormValues,
  mode: "create",
): CategorySubmitResult<CategoryCreateRequest>;
export function prepareCategorySubmit(
  form: CategoryFormValues,
  mode: "edit",
): CategorySubmitResult<CategoryUpdateRequest>;
export function prepareCategorySubmit(
  form: CategoryFormValues,
  mode: CategoryFormMode,
): CategorySubmitResult<CategoryCreateRequest | CategoryUpdateRequest>;
export function prepareCategorySubmit(
  form: CategoryFormValues,
  mode: CategoryFormMode,
): CategorySubmitResult<CategoryCreateRequest | CategoryUpdateRequest> {
  const errors = validateCategoryForm(form, mode);
  const sortOrder = parseSortOrder(form.sortOrder);
  if (Object.keys(errors).length > 0) {
    return { ok: false, errors };
  }
  if (sortOrder === null) {
    return { ok: false, errors: { sortOrder: SORT_ORDER_ERROR } };
  }
  const body: CategoryUpdateRequest = {
    name: form.name,
    description: form.description === "" ? null : form.description,
    sortOrder,
  };
  return {
    ok: true,
    request: mode === "create" ? { slug: form.slug, ...body } : body,
  };
}
