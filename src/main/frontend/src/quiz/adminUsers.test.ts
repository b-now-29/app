// 회원 관리 화면의 계산 (docs/features/admin-members.md 화면 흐름). 구현할 파일: src/quiz/adminUsers.ts
// React를 import하지 않는 순수 함수.
//
//   formatJoinedDate(createdAt: string | null): string
//     ISO-8601 시각을 브라우저 시간대의 "YYYY-MM-DD"로 바꾼다 (월·일은 두 자리). null이면 "-" (R2).
//
//   roleAction(role: "USER" | "ADMIN"): { readonly label: string; readonly nextRole: "USER" | "ADMIN" }
//     USER면 { label: "관리자로 지정", nextRole: "ADMIN" }, ADMIN이면 { label: "관리자 해제", nextRole: "USER" }.
import { afterEach, describe, expect, it, vi } from "vitest";
import { formatJoinedDate, roleAction } from "./adminUsers";

describe("formatJoinedDate (R2, 화면 흐름)", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("null이면 -를 돌려준다 (기능 전에 가입한 회원)", () => {
    expect(formatJoinedDate(null)).toBe("-");
  });

  it.each([
    // 시간대가 달라도 날짜가 바뀌지 않도록 UTC 정오로 둔다
    ["2026-11-13T12:00:00Z", "2026-11-13"],
    ["2026-03-05T12:00:00Z", "2026-03-05"],
    ["2026-01-09T12:00:00.123456Z", "2026-01-09"],
  ])("%s는 %s다 (월·일은 두 자리)", (createdAt, expected) => {
    expect(formatJoinedDate(createdAt)).toBe(expected);
  });

  // Node는 process.env.TZ가 바뀌면 바로 시간대를 다시 읽는다 (vi.stubEnv가 process.env를 바꾼다)
  it("UTC가 아니라 브라우저 시간대의 날짜를 쓴다", () => {
    vi.stubEnv("TZ", "Asia/Seoul");
    // UTC로는 11월 12일이지만 서울(UTC+9)로는 11월 13일 오전 8시 30분이다
    expect(formatJoinedDate("2026-11-12T23:30:00Z")).toBe("2026-11-13");

    vi.stubEnv("TZ", "America/Los_Angeles");
    // UTC로는 11월 13일이지만 로스앤젤레스(UTC-8)로는 11월 12일 오후 5시다
    expect(formatJoinedDate("2026-11-13T01:00:00Z")).toBe("2026-11-12");
  });
});

describe("roleAction (R3, 화면 흐름)", () => {
  it("USER 회원은 관리자로 지정하는 버튼이다", () => {
    expect(roleAction("USER")).toEqual({
      label: "관리자로 지정",
      nextRole: "ADMIN",
    });
  });

  it("ADMIN 회원은 관리자 해제 버튼이다", () => {
    expect(roleAction("ADMIN")).toEqual({
      label: "관리자 해제",
      nextRole: "USER",
    });
  });
});
