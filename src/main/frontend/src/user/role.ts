import type { AdminUserRole } from "../api/admin";

// USER가 아니면 관리자 메뉴를 볼 수 있는 역할이다
export function isStaff(role: AdminUserRole): boolean {
  return role !== "USER";
}

// 운영자(OWNER)만 회원 관리와 카테고리 생성·삭제를 한다
export function isOwner(role: AdminUserRole): boolean {
  return role === "OWNER";
}
