import type { AdminUserResponse } from "../api/admin";

// 화면에서 고를 수 있는 역할. OWNER는 고를 수 없다
export type EditableRole = "USER" | "CATEGORY_ADMIN";

export type AdminUserForm = {
  readonly role: EditableRole;
  readonly categorySlugs: readonly string[];
};

export function formFromUser(user: AdminUserResponse): AdminUserForm {
  return {
    role: user.role === "CATEGORY_ADMIN" ? "CATEGORY_ADMIN" : "USER",
    categorySlugs: user.categorySlugs,
  };
}

// 체크한 카테고리는 남긴다 (다시 카테고리 관리자를 고르면 체크가 살아 있다)
export function withRole(
  form: AdminUserForm,
  role: EditableRole,
): AdminUserForm {
  return { ...form, role };
}

export function withCategoryChecked(
  form: AdminUserForm,
  slug: string,
  checked: boolean,
): AdminUserForm {
  const without = form.categorySlugs.filter((current) => current !== slug);
  if (!checked) {
    return { ...form, categorySlugs: without };
  }
  return {
    ...form,
    categorySlugs: form.categorySlugs.includes(slug)
      ? form.categorySlugs
      : [...without, slug],
  };
}

// 담당 없는 카테고리 관리자는 저장할 수 없다
export function canSaveUserForm(form: AdminUserForm): boolean {
  return form.role === "USER" || form.categorySlugs.length > 0;
}

export function prepareRoleChange(form: AdminUserForm): AdminUserForm | null {
  if (!canSaveUserForm(form)) {
    return null;
  }
  return {
    role: form.role,
    categorySlugs: form.role === "USER" ? [] : form.categorySlugs,
  };
}
