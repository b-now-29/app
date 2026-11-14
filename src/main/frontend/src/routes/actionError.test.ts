// action 실패를 폼 오류 문구로 바꾸는 공용 함수 (docs/features/nickname.md 제약 사항 Standards 리뷰, 프론트).
//
// 구현할 것:
// - src/routes/actionError.ts: authActions.ts의 toActionError와 ActionError 타입을 옮긴다
//   export type ActionError = { readonly error: string };
//   export function toActionError(thrown: unknown, fallback: string): ActionError
//   authActions.ts와 meRoute.ts는 여기서 import한다
import { describe, expect, it } from "vitest";
import { ApiError } from "../api/http";
import { errorMessage, toActionError } from "./actionError";

const FALLBACK = "요청에 실패했습니다. 다시 시도해 주세요.";

describe("toActionError", () => {
  it("ApiError면 서버 메시지를 오류 문구로 쓴다", () => {
    expect(
      toActionError(new ApiError(400, "닉네임이 올바르지 않습니다."), FALLBACK),
    ).toEqual({ error: "닉네임이 올바르지 않습니다." });
  });

  it("네트워크 오류처럼 ApiError가 아니면 기본 문구를 쓴다", () => {
    expect(toActionError(new TypeError("Failed to fetch"), FALLBACK)).toEqual({
      error: FALLBACK,
    });
  });

  it("Error가 아닌 값이 던져져도 기본 문구를 쓴다", () => {
    expect(toActionError("oops", FALLBACK)).toEqual({ error: FALLBACK });
  });
});

describe("errorMessage", () => {
  const DEFAULT_MESSAGE = "요청에 실패했습니다. 다시 시도해 주세요.";

  it("ApiError면 서버 메시지를 쓴다", () => {
    expect(errorMessage(new ApiError(409, "사용 중인 카테고리입니다."))).toBe(
      "사용 중인 카테고리입니다.",
    );
  });

  it("네트워크 오류처럼 ApiError가 아니면 기본 문구를 쓴다", () => {
    expect(errorMessage(new TypeError("Failed to fetch"))).toBe(
      DEFAULT_MESSAGE,
    );
  });

  it("Error가 아닌 값이 던져져도 기본 문구를 쓴다", () => {
    expect(errorMessage("oops")).toBe(DEFAULT_MESSAGE);
  });
});
