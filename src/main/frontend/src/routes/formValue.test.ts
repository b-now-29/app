// 폼 값 꺼내기 공용 함수 (docs/features/nickname.md 제약 사항 Standards 리뷰, 프론트).
//
// 구현할 것:
// - src/routes/formValue.ts:
//   export function formString(formData: FormData, name: string): string
//   그 이름의 값이 문자열이면 그대로 (공백도 지우지 않는다. 정규화는 서버 책임), 없거나 파일이면 "".
//   authActions.ts (email, password, nickname)와 meRoute.ts (nickname)가 이 함수를 쓴다
import { describe, expect, it } from "vitest";
import { formString } from "./formValue";

describe("formString", () => {
  it("문자열 값은 그대로 돌려준다. 앞뒤 공백도 지우지 않는다", () => {
    const formData = new FormData();
    formData.set("nickname", "  철수 ");

    expect(formString(formData, "nickname")).toBe("  철수 ");
  });

  it("빈 문자열은 빈 문자열이다", () => {
    const formData = new FormData();
    formData.set("email", "");

    expect(formString(formData, "email")).toBe("");
  });

  it("그 이름의 값이 없으면 빈 문자열이다", () => {
    expect(formString(new FormData(), "password")).toBe("");
  });

  it("파일이면 빈 문자열이다", () => {
    const formData = new FormData();
    formData.set("nickname", new File(["x"], "nickname.txt"));

    expect(formString(formData, "nickname")).toBe("");
  });
});
