import type { AdminUserRole } from "../api/admin";

export function roleAction(role: AdminUserRole): {
  readonly label: string;
  readonly nextRole: AdminUserRole;
} {
  return role === "USER"
    ? { label: "관리자로 지정", nextRole: "ADMIN" }
    : { label: "관리자 해제", nextRole: "USER" };
}
