// 내 정보 화면의 action 결과 모양 (docs/features/password-change.md D15).
// 결과는 intent(nickname | password)와 kind(saved | error | mismatch)로 구분한다.
// 화면 동작은 appRoutes.me.test.tsx, appRoutes.mePassword.test.tsx가 사용자 관점으로 확인한다.
import type { ActionFunctionArgs } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/http";
import { changeNickname, changePassword } from "../api/users";
import { meAction } from "./meRoute";

vi.mock("../api/users");

const WRONG_CURRENT = "현재 비밀번호가 맞지 않습니다.";

function actionArgs(fields: Record<string, string>): ActionFunctionArgs {
  return {
    request: new Request("http://localhost/me", {
      method: "POST",
      body: new URLSearchParams(fields),
    }),
    params: {},
    context: undefined,
  } as unknown as ActionFunctionArgs;
}

function passwordForm(confirm = "new-password-123") {
  return actionArgs({
    intent: "password",
    currentPassword: "old-password",
    newPassword: "new-password-123",
    confirmPassword: confirm,
  });
}

beforeEach(() => {
  vi.mocked(changeNickname).mockReset();
  vi.mocked(changeNickname).mockResolvedValue({
    id: 1,
    email: "user@example.com",
    role: "USER",
    nickname: "영희",
    tag: 1,
  });
  vi.mocked(changePassword).mockReset();
  vi.mocked(changePassword).mockResolvedValue(undefined);
});

describe("meAction 결과 (password-change D15)", () => {
  it("닉네임 저장에 성공하면 intent nickname, kind saved", async () => {
    const result = await meAction(
      actionArgs({ intent: "nickname", nickname: "영희" }),
    );

    expect(result).toMatchObject({ intent: "nickname", kind: "saved" });
  });

  it("닉네임 저장에 실패하면 intent nickname, kind error와 서버 문구", async () => {
    vi.mocked(changeNickname).mockRejectedValue(
      new ApiError(400, "입력값이 올바르지 않습니다. (항목: nickname)"),
    );

    const result = await meAction(
      actionArgs({ intent: "nickname", nickname: "영희#1" }),
    );

    expect(result).toMatchObject({
      intent: "nickname",
      kind: "error",
      error: "입력값이 올바르지 않습니다. (항목: nickname)",
    });
  });

  it("비밀번호 변경에 성공하면 intent password, kind saved", async () => {
    const result = await meAction(passwordForm());

    expect(result).toMatchObject({ intent: "password", kind: "saved" });
    expect(changePassword).toHaveBeenCalledWith(
      "old-password",
      "new-password-123",
    );
  });

  it("비밀번호 변경에 실패하면 intent password, kind error와 서버 문구", async () => {
    vi.mocked(changePassword).mockRejectedValue(
      new ApiError(400, WRONG_CURRENT),
    );

    const result = await meAction(passwordForm());

    expect(result).toMatchObject({
      intent: "password",
      kind: "error",
      error: WRONG_CURRENT,
    });
  });

  it("새 비밀번호와 확인이 다르면 서버에 보내지 않고 intent password, kind mismatch", async () => {
    const result = await meAction(passwordForm("new-password-124"));

    expect(result).toMatchObject({ intent: "password", kind: "mismatch" });
    expect(changePassword).not.toHaveBeenCalled();
  });
});
