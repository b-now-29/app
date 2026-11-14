import { beforeEach, describe, expect, it, vi } from "vitest";
// 닉네임 (docs/features/nickname.md R1, R5, R8, API 계약)에서 구현할 것:
//   UserResponse에 nickname: string, tag: number 추가
//   signUp(email, password, nickname): 요청 본문 { email, password, nickname }
//   changeNickname(nickname: string): Promise<UserResponse> — PUT /api/v1/users/me/nickname { nickname }
import { ApiError, ResponseFormatError } from "./http";
import {
  changeNickname,
  changePassword,
  getCurrentUser,
  login,
  logout,
  signUp,
} from "./users";
import type { UserResponse } from "./users";

const fetchMock = vi.spyOn(globalThis, "fetch");

const USER: UserResponse = {
  id: 1,
  email: "user@example.com",
  role: "USER",
  nickname: "철수",
  tag: 3,
};
const EMAIL = "user@example.com";
const PASSWORD = "password1234";
const NICKNAME = "철수";

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
  it("이메일, 비밀번호, 닉네임을 POST하고 닉네임과 번호를 포함한 응답 본문을 돌려준다 (nickname R1, R8)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(USER, 201));

    await expect(signUp(EMAIL, PASSWORD, NICKNAME)).resolves.toEqual(USER);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: EMAIL,
        password: PASSWORD,
        nickname: NICKNAME,
      }),
    });
  });

  it.each([
    [409, "이미 가입한 이메일입니다."],
    [400, "입력 형식이 올바르지 않습니다."],
  ])(
    "%i 응답은 상태와 서버 메시지를 담은 ApiError다",
    async (status, detail) => {
      fetchMock.mockResolvedValue(problemResponse(status, detail));

      const error = await caught(signUp(EMAIL, PASSWORD, NICKNAME));

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

describe("changeNickname (nickname R5)", () => {
  it("새 닉네임을 PUT하고 바뀐 내 정보를 돌려준다", async () => {
    const changed: UserResponse = { ...USER, nickname: "영희", tag: 2 };
    fetchMock.mockResolvedValue(jsonResponse(changed));

    await expect(changeNickname("영희")).resolves.toEqual(changed);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/nickname", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ nickname: "영희" }),
    });
  });

  it.each([
    [400, "입력값이 올바르지 않습니다. (항목: nickname)"],
    [401, "로그인이 필요합니다."],
  ])(
    "%i 응답은 상태와 서버 메시지를 담은 ApiError다",
    async (status, detail) => {
      fetchMock.mockResolvedValue(problemResponse(status, detail));

      const error = await caught(changeNickname("영희#1"));

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status, message: detail });
    },
  );
});

describe("changePassword (password-change R1, API 계약)", () => {
  it("현재 비밀번호와 새 비밀번호를 PUT하고 204 응답이면 값 없이 끝난다", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(
      changePassword("old-password", "new-password-123"),
    ).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/password", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        currentPassword: "old-password",
        newPassword: "new-password-123",
      }),
    });
  });

  it.each([
    [400, "현재 비밀번호가 맞지 않습니다."],
    [400, "입력값이 올바르지 않습니다. (항목: newPassword)"],
    [401, "로그인이 필요합니다."],
    [429, "시도가 너무 많습니다."],
  ])(
    "%i 응답은 상태와 서버 메시지를 담은 ApiError다",
    async (status, detail) => {
      fetchMock.mockResolvedValue(problemResponse(status, detail));

      const error = await caught(changePassword("old-password", "short"));

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status, message: detail });
    },
  );
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

  it("응답 모양이 다르면 null이 아니라 ResponseFormatError를 던진다 (api-response-validation R2)", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ...USER, role: "GUEST" }));

    await expect(getCurrentUser()).rejects.toBeInstanceOf(ResponseFormatError);
  });

  it("401이 아닌 실패는 에러로 던진다", async () => {
    fetchMock.mockResolvedValue(problemResponse(500, "서버 오류"));

    await expect(getCurrentUser()).rejects.toBeInstanceOf(ApiError);
  });
});
