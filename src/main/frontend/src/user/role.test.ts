// 역할 판단 (role-owner-category-admin R1, R8, D14).
//   isStaff(role): USER가 아니면 true (관리자 메뉴를 보는 역할)
//   isOwner(role): OWNER만 true (회원 관리 메뉴, 카테고리 생성·삭제)
import { describe, expect, it } from "vitest";
import type { AdminUserRole } from "../api/admin";
import { isOwner, isStaff } from "./role";

describe("isStaff", () => {
  it.each<[AdminUserRole, boolean]>([
    ["USER", false],
    ["CATEGORY_ADMIN", true],
    ["OWNER", true],
  ])("%s → %s (R8: 관리자 메뉴는 CATEGORY_ADMIN·OWNER)", (role, expected) => {
    expect(isStaff(role)).toBe(expected);
  });
});

describe("isOwner", () => {
  it.each<[AdminUserRole, boolean]>([
    ["USER", false],
    ["CATEGORY_ADMIN", false],
    ["OWNER", true],
  ])("%s → %s (R8: 회원 관리 메뉴는 OWNER만)", (role, expected) => {
    expect(isOwner(role)).toBe(expected);
  });
});
