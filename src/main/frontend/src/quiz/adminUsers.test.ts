// 회원 관리 화면의 계산 (docs/features/admin-members.md 화면 흐름). 구현할 파일: src/quiz/adminUsers.ts
// React를 import하지 않는 순수 함수.
// 가입일 표시는 공통 함수 formatDate (quiz/date.ts, date.test.ts 참고, docs/features/ranking.md D13)로 옮겼다.
// formatJoinedDate는 없앤다.
//
//   roleAction(role: "USER" | "ADMIN"): { readonly label: string; readonly nextRole: "USER" | "ADMIN" }
//     USER면 { label: "관리자로 지정", nextRole: "ADMIN" }, ADMIN이면 { label: "관리자 해제", nextRole: "USER" }.
import { describe, expect, it } from "vitest";
import { roleAction } from "./adminUsers";

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
