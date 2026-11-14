// 새 비밀번호와 확인 칸 일치 여부 (docs/features/password-change.md R8).
import { describe, expect, it } from "vitest";
import { passwordsMatch } from "./password";

describe("passwordsMatch", () => {
  it("두 값이 같으면 true", () => {
    expect(passwordsMatch("new-password-123", "new-password-123")).toBe(true);
  });

  it("두 값이 다르면 false", () => {
    expect(passwordsMatch("new-password-123", "new-password-124")).toBe(false);
  });

  it("대소문자만 달라도 false (비밀번호는 정확히 같아야 한다)", () => {
    expect(passwordsMatch("New-Password-123", "new-password-123")).toBe(false);
  });

  it("앞뒤 공백만 달라도 false", () => {
    expect(passwordsMatch("new-password-123", " new-password-123")).toBe(false);
    expect(passwordsMatch("new-password-123", "new-password-123 ")).toBe(false);
  });

  it("빈 문자열끼리는 true", () => {
    expect(passwordsMatch("", "")).toBe(true);
  });
});
