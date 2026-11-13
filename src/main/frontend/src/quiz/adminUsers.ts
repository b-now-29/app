import type { AdminUserRole } from "../api/admin";

function twoDigits(value: number): string {
  return String(value).padStart(2, "0");
}

// ISO 시각을 브라우저 시간대의 YYYY-MM-DD로 바꾼다. 가입일이 없으면 "-"
export function formatJoinedDate(createdAt: string | null): string {
  if (createdAt === null) {
    return "-";
  }
  const date = new Date(createdAt);
  const month = twoDigits(date.getMonth() + 1);
  const day = twoDigits(date.getDate());
  return `${date.getFullYear()}-${month}-${day}`;
}

export function roleAction(role: AdminUserRole): {
  readonly label: string;
  readonly nextRole: AdminUserRole;
} {
  return role === "USER"
    ? { label: "관리자로 지정", nextRole: "ADMIN" }
    : { label: "관리자 해제", nextRole: "USER" };
}
