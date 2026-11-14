// 표시 순서 변환 (D19). 대상: src/quiz/sortOrder.ts
//
//   export function parseSortOrder(value: string): number | null
//     앞뒤 공백을 무시하고 정수 문자열이면 그 수를, 비었거나 정수가 아니면 null을 돌려준다.
import { describe, expect, it } from "vitest";
import { parseSortOrder } from "./sortOrder";

describe("parseSortOrder (D19)", () => {
  it.each([
    ["0", 0],
    ["30", 30],
    ["-5", -5],
    [" 7 ", 7],
  ])("'%s'는 정수 %i이다", (value, expected) => {
    expect(parseSortOrder(value)).toBe(expected);
  });

  it.each(["", "   ", "1.5", "-0.1", "abc", "1a", "NaN", "Infinity"])(
    "'%s'는 정수가 아니므로 null이다",
    (value) => {
      expect(parseSortOrder(value)).toBeNull();
    },
  );
});
