// 로그인이 필요한 화면의 loader가 401을 /login으로 보내는 공용 함수 (docs/features/loader-login-redirect.md R1, R2).
import { describe, expect, it } from "vitest";
import { ApiError, NotFoundError } from "../api/http";
import { rethrowWithLoginRedirect } from "./loginRedirect";

function thrownBy(action: () => unknown): unknown {
  try {
    action();
  } catch (thrown: unknown) {
    return thrown;
  }
  throw new Error("아무것도 던지지 않았다");
}

describe("rethrowWithLoginRedirect", () => {
  it("401 ApiError면 /login으로 보내는 redirect를 던진다", () => {
    const thrown = thrownBy(() =>
      rethrowWithLoginRedirect(new ApiError(401, "로그인이 필요합니다.")),
    );

    expect(thrown).toBeInstanceOf(Response);
    const response = thrown as Response;
    expect(response.status).toBeGreaterThanOrEqual(300);
    expect(response.status).toBeLessThan(400);
    expect(response.headers.get("Location")).toBe("/login");
  });

  it.each([
    ["403 ApiError", new ApiError(403, "권한이 없습니다.")],
    ["500 ApiError", new ApiError(500, "서버 오류")],
    ["NotFoundError", new NotFoundError("없는 대상입니다.")],
    ["일반 Error", new TypeError("Failed to fetch")],
    ["Error가 아닌 값", "oops"],
    ["undefined", undefined],
  ])("%s는 받은 그대로 다시 던진다", (_name, error) => {
    expect(thrownBy(() => rethrowWithLoginRedirect(error))).toBe(error);
  });
});
