// 회원 표시 이름 "닉네임#번호" (docs/features/nickname.md R2, R9, R11, D13).
import { describe, expect, it } from "vitest";
import type { UserResponse } from "../api/users";
import { displayName } from "./displayName";

describe("displayName", () => {
  it("닉네임과 번호를 #로 이어 붙인다", () => {
    expect(displayName({ nickname: "철수", tag: 3 })).toBe("철수#3");
  });

  it("닉네임의 대소문자와 가운데 공백을 그대로 둔다 (R7)", () => {
    expect(displayName({ nickname: "Kim Lee", tag: 12 })).toBe("Kim Lee#12");
  });

  it("다른 필드가 있는 회원 객체도 닉네임과 번호만 쓴다", () => {
    const member: UserResponse = {
      id: 1,
      email: "user@example.com",
      role: "USER",
      nickname: "영희",
      tag: 1,
    };

    expect(displayName(member)).toBe("영희#1");
  });
});
