// 오답 노트 화면의 문구 계산 (docs/features/wrong-answers.md 화면 흐름, 제약 사항 코드 리뷰). 구현할 파일: src/quiz/wrongAnswers.ts
// React를 import하지 않는 순수 함수.
//
//   wrongAnswerCategoryTitle(category: WrongAnswerCategoryResponse): string
//     카테고리 제목. "이름 N문제" (N은 그 카테고리의 오답 문제 수).
//
//   wrongAnswerQuestionMeta(question: WrongAnswerQuestionResponse): string
//     문제 아래 줄. "유형 이름 · 날짜". 유형 이름은 QUESTION_TYPE_NAMES, 날짜는 formatAttemptDate.
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
  WrongAnswerCategoryResponse,
  WrongAnswerQuestionResponse,
} from "../api/wrongAnswers";
import {
  wrongAnswerCategoryTitle,
  wrongAnswerQuestionMeta,
} from "./wrongAnswers";

const ATTEMPTED_AT = "2026-11-06T01:23:45Z";

function question(
  overrides: Partial<WrongAnswerQuestionResponse> = {},
): WrongAnswerQuestionResponse {
  return {
    questionId: 1,
    content: "문제 본문",
    type: "ESSAY",
    attemptedAt: ATTEMPTED_AT,
    ...overrides,
  };
}

function category(
  questions: readonly WrongAnswerQuestionResponse[],
): WrongAnswerCategoryResponse {
  return { slug: "java", name: "자바", questions };
}

describe("wrongAnswerCategoryTitle (R6, 화면 흐름)", () => {
  it("이름 뒤에 오답 문제 수를 붙인다", () => {
    expect(
      wrongAnswerCategoryTitle(
        category([question({ questionId: 1 }), question({ questionId: 2 })]),
      ),
    ).toBe("자바 2문제");
  });

  it("오답이 하나면 1문제다", () => {
    expect(wrongAnswerCategoryTitle(category([question()]))).toBe("자바 1문제");
  });
});

describe("wrongAnswerQuestionMeta (R6, 화면 흐름)", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it.each([
    ["MULTIPLE_CHOICE", "객관식"],
    ["SHORT_ANSWER", "주관식"],
    ["ESSAY", "서술형"],
  ] as const)("%s 문제는 %s · 날짜다", (type, typeName) => {
    expect(wrongAnswerQuestionMeta(question({ type }))).toBe(
      `${typeName} · ${new Date(ATTEMPTED_AT).toLocaleDateString("ko-KR")}`,
    );
  });

  it("날짜는 브라우저 시간대의 ko-KR 형식이다", () => {
    vi.stubEnv("TZ", "Asia/Seoul");
    // UTC로는 11월 12일이지만 서울(UTC+9)로는 11월 13일이다
    expect(
      wrongAnswerQuestionMeta(
        question({ type: "ESSAY", attemptedAt: "2026-11-12T23:30:00Z" }),
      ),
    ).toBe("서술형 · 2026. 11. 13.");
  });
});
