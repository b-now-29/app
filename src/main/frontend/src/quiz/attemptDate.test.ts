// 풀이 시각의 날짜 표시 (docs/features/wrong-answers.md 제약 사항, 코드 리뷰). 구현할 파일: src/quiz/attemptDate.ts
// React를 import하지 않는 순수 함수. 내 기록 (HistoryPage)과 오답 노트 (WrongAnswersPage)가 같이 쓴다.
//
//   formatAttemptDate(attemptedAt: string): string
//     ISO-8601 시각을 기존 화면과 같은 new Date(attemptedAt).toLocaleDateString("ko-KR") 형식으로 바꾼다.
import { afterEach, describe, expect, it, vi } from "vitest";
import { formatAttemptDate } from "./attemptDate";

describe("formatAttemptDate", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it.each([
    "2026-11-06T01:23:45Z",
    "2026-01-09T12:00:00.123456Z",
    "2026-12-31T23:59:59Z",
  ])("%s는 기존 toLocaleDateString(ko-KR)과 같은 결과다", (attemptedAt) => {
    expect(formatAttemptDate(attemptedAt)).toBe(
      new Date(attemptedAt).toLocaleDateString("ko-KR"),
    );
  });

  // Node는 process.env.TZ가 바뀌면 바로 시간대를 다시 읽는다 (vi.stubEnv가 process.env를 바꾼다)
  it("브라우저 시간대의 날짜를 ko-KR 형식으로 쓴다", () => {
    vi.stubEnv("TZ", "Asia/Seoul");
    // UTC로는 11월 12일이지만 서울(UTC+9)로는 11월 13일이다
    expect(formatAttemptDate("2026-11-12T23:30:00Z")).toBe("2026. 11. 13.");

    vi.stubEnv("TZ", "America/Los_Angeles");
    // UTC로는 11월 13일이지만 로스앤젤레스(UTC-8)로는 11월 12일이다
    expect(formatAttemptDate("2026-11-13T01:00:00Z")).toBe("2026. 11. 12.");
  });
});
