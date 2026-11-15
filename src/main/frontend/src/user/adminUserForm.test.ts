// 회원 상세 화면 (/admin/users/:id)의 폼 상태와 저장 준비 (role-owner-category-admin R9, 화면 흐름, D9, D11).
// 구현할 파일: src/user/adminUserForm.ts (React를 import하지 않는 순수 함수, D15)
//
//   type EditableRole = "USER" | "CATEGORY_ADMIN"        // 화면에서 고를 수 있는 역할 (OWNER는 고를 수 없다)
//   type AdminUserForm = { readonly role: EditableRole; readonly categorySlugs: readonly string[] }
//   formFromUser(user: AdminUserResponse): AdminUserForm
//     회원의 지금 역할과 담당 카테고리로 시작한다.
//   withRole(form, role: EditableRole): AdminUserForm
//     역할만 바꾸고 체크한 카테고리는 그대로 둔다 (다시 카테고리 관리자를 고르면 체크가 살아 있다).
//   withCategoryChecked(form, slug: string, checked: boolean): AdminUserForm
//     체크하면 끝에 더하고 (이미 있으면 그대로), 해제하면 뺀다.
//   canSaveUserForm(form): boolean
//     카테고리 관리자인데 체크가 0개면 false (D9). 일반 회원은 true.
//   prepareRoleChange(form): { readonly role: EditableRole; readonly categorySlugs: readonly string[] } | null
//     저장할 수 없으면 null. 일반 회원은 체크와 상관없이 빈 categorySlugs로 보낸다 (R9: USER는 담당이 없다).
import { describe, expect, it } from "vitest";
import type { AdminUserResponse } from "../api/admin";
import {
  canSaveUserForm,
  formFromUser,
  prepareRoleChange,
  withCategoryChecked,
  withRole,
} from "./adminUserForm";

const MANAGER: AdminUserResponse = {
  id: 3,
  email: "manager@example.com",
  role: "CATEGORY_ADMIN",
  createdAt: null,
  nickname: "매니저",
  tag: 1,
  categorySlugs: ["java", "spring"],
};

const MEMBER: AdminUserResponse = {
  id: 2,
  email: "user@example.com",
  role: "USER",
  createdAt: null,
  nickname: "회원",
  tag: 1,
  categorySlugs: [],
};

describe("formFromUser", () => {
  it("카테고리 관리자는 역할과 담당 카테고리로 시작한다", () => {
    expect(formFromUser(MANAGER)).toEqual({
      role: "CATEGORY_ADMIN",
      categorySlugs: ["java", "spring"],
    });
  });

  it("일반 회원은 체크 없이 시작한다", () => {
    expect(formFromUser(MEMBER)).toEqual({ role: "USER", categorySlugs: [] });
  });
});

describe("withRole", () => {
  it("역할만 바꾸고 체크한 카테고리는 남긴다", () => {
    const form = withRole(formFromUser(MANAGER), "USER");

    expect(form).toEqual({ role: "USER", categorySlugs: ["java", "spring"] });
    expect(withRole(form, "CATEGORY_ADMIN")).toEqual({
      role: "CATEGORY_ADMIN",
      categorySlugs: ["java", "spring"],
    });
  });

  it("원래 폼을 바꾸지 않는다", () => {
    const form = formFromUser(MANAGER);

    withRole(form, "USER");

    expect(form.role).toBe("CATEGORY_ADMIN");
  });
});

describe("withCategoryChecked", () => {
  it("체크하면 끝에 더하고, 해제하면 뺀다", () => {
    const start = withRole(formFromUser(MEMBER), "CATEGORY_ADMIN");

    const checked = withCategoryChecked(
      withCategoryChecked(start, "spring", true),
      "java",
      true,
    );
    expect(checked.categorySlugs).toEqual(["spring", "java"]);

    expect(withCategoryChecked(checked, "spring", false).categorySlugs).toEqual(
      ["java"],
    );
  });

  it("이미 체크한 카테고리를 다시 체크해도 한 번만 남는다", () => {
    const form = withCategoryChecked(formFromUser(MANAGER), "java", true);

    expect(form.categorySlugs).toEqual(["java", "spring"]);
  });

  it("체크하지 않은 카테고리를 해제해도 그대로다", () => {
    const form = withCategoryChecked(formFromUser(MANAGER), "kotlin", false);

    expect(form.categorySlugs).toEqual(["java", "spring"]);
  });

  it("원래 폼을 바꾸지 않는다", () => {
    const form = formFromUser(MANAGER);

    withCategoryChecked(form, "java", false);

    expect(form.categorySlugs).toEqual(["java", "spring"]);
  });
});

describe("canSaveUserForm (D9)", () => {
  it("카테고리 관리자인데 체크가 0개면 저장할 수 없다", () => {
    expect(canSaveUserForm({ role: "CATEGORY_ADMIN", categorySlugs: [] })).toBe(
      false,
    );
  });

  it("카테고리 관리자가 1개 이상 체크하면 저장할 수 있다", () => {
    expect(
      canSaveUserForm({ role: "CATEGORY_ADMIN", categorySlugs: ["java"] }),
    ).toBe(true);
  });

  it.each([[[]], [["java"]]])(
    "일반 회원은 체크와 상관없이 저장할 수 있다 (%j)",
    (categorySlugs) => {
      expect(canSaveUserForm({ role: "USER", categorySlugs })).toBe(true);
    },
  );
});

describe("prepareRoleChange (R9)", () => {
  it("카테고리 관리자는 체크한 카테고리를 그 순서대로 보낸다", () => {
    expect(
      prepareRoleChange({
        role: "CATEGORY_ADMIN",
        categorySlugs: ["spring", "java"],
      }),
    ).toEqual({ role: "CATEGORY_ADMIN", categorySlugs: ["spring", "java"] });
  });

  it("일반 회원은 체크가 남아 있어도 빈 목록으로 보낸다", () => {
    expect(
      prepareRoleChange({ role: "USER", categorySlugs: ["java"] }),
    ).toEqual({ role: "USER", categorySlugs: [] });
  });

  it("저장할 수 없는 폼 (담당 없는 카테고리 관리자)이면 null이다 (D9)", () => {
    expect(
      prepareRoleChange({ role: "CATEGORY_ADMIN", categorySlugs: [] }),
    ).toBeNull();
  });
});
