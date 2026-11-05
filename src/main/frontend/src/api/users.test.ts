import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, getCurrentUser, login, logout, signUp } from "./users";
import type { UserResponse } from "./users";

const fetchMock = vi.spyOn(globalThis, "fetch");

const USER: UserResponse = { id: 1, email: "user@example.com" };
const EMAIL = "user@example.com";
const PASSWORD = "password1234";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

function problemResponse(status: number, detail: string): Response {
  return jsonResponse({ title: "실패", status, detail }, status);
}

async function caught(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

beforeEach(() => {
  fetchMock.mockReset();
});

describe("signUp", () => {
  it("이메일과 비밀번호를 POST하고 응답 본문을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(USER, 201));

    await expect(signUp(EMAIL, PASSWORD)).resolves.toEqual(USER);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    });
  });

  it.each([
    [409, "이미 가입한 이메일입니다."],
    [400, "입력 형식이 올바르지 않습니다."],
  ])(
    "%i 응답은 상태와 서버 메시지를 담은 ApiError다",
    async (status, detail) => {
      fetchMock.mockResolvedValue(problemResponse(status, detail));

      const error = await caught(signUp(EMAIL, PASSWORD));

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status, message: detail });
    },
  );
});

describe("login", () => {
  it("이메일과 비밀번호를 POST하고 응답 본문을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(USER));

    await expect(login(EMAIL, PASSWORD)).resolves.toEqual(USER);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    });
  });

  it("401 응답은 상태와 서버 메시지를 담은 ApiError다 (R5)", async () => {
    const detail = "이메일 또는 비밀번호가 올바르지 않습니다.";
    fetchMock.mockResolvedValue(problemResponse(401, detail));

    const error = await caught(login(EMAIL, PASSWORD));

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 401, message: detail });
  });

  it("본문이 JSON이 아니어도 상태를 담은 ApiError이고 메시지가 비어 있지 않다", async () => {
    fetchMock.mockResolvedValue(
      new Response("<html>Bad Gateway</html>", { status: 502 }),
    );

    const error = await caught(login(EMAIL, PASSWORD));

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 502 });
    expect((error as ApiError).message).not.toBe("");
  });
});

describe("logout", () => {
  it("POST하고 204 응답이면 끝난다", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(logout()).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/logout", {
      method: "POST",
    });
  });

  it("실패 응답은 ApiError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(500, "서버 오류"));

    await expect(logout()).rejects.toBeInstanceOf(ApiError);
  });
});

describe("getCurrentUser", () => {
  it("로그인 상태면 사용자를 돌려준다 (R6)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(USER));

    await expect(getCurrentUser()).resolves.toEqual(USER);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me");
  });

  it("401이면 null을 돌려준다 (R6)", async () => {
    fetchMock.mockResolvedValue(problemResponse(401, "로그인이 필요합니다."));

    await expect(getCurrentUser()).resolves.toBeNull();
  });

  it("401이 아닌 실패는 에러로 던진다", async () => {
    fetchMock.mockResolvedValue(problemResponse(500, "서버 오류"));

    await expect(getCurrentUser()).rejects.toBeInstanceOf(ApiError);
  });
});
