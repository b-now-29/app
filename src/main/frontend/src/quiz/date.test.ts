// 모든 화면의 날짜 표시 (docs/features/ranking.md D13). 구현할 파일: src/quiz/date.ts
// React를 import하지 않는 순수 함수. 내 기록 (HistoryPage), 오답 노트 (WrongAnswersPage, quiz/wrongAnswers.ts),
// 관리자 회원 목록 (AdminUsersPage)의 가입일이 모두 이 함수 하나를 쓴다.
// 기존 formatAttemptDate (quiz/attemptDate.ts)와 formatJoinedDate (quiz/adminUsers.ts)는 없앤다.
//
//   formatDate(value: string | null): string
//     ISO-8601 시각을 브라우저 시간대의 연·월·일로 "YYYY.MM.DD" 형식으로 바꾼다 (공백 없음, 월·일은 두 자리).
//     null이거나 날짜로 읽을 수 없는 값 (빈 문자열 포함)이면 "-".
//     실행 환경의 날짜 형식 데이터 (ICU/CLDR, toLocale*·Intl)에 기대지 않는다. Node v26 (CLDR 49)의
//     toLocaleDateString("ko-KR")은 "2026/11/13"을 내므로 그 결과를 쓰면 안 된다.
import { afterEach, describe, expect, it, vi } from "vitest";
import { formatDate } from "./date";

// 실행 환경의 날짜 형식 데이터가 바뀐 상태 (Node v26, CLDR 49의 ko-KR 모양 "2026/11/13")를 흉내 낸다.
// Date의 지역화 메서드와 Intl.DateTimeFormat이 모두 이 모양을 내도록 바꾼다.
function stubChangedLocaleData() {
  // 바꾸기 전에 지금 시간대 (vi.stubEnv로 정한 TZ)를 진짜 Intl에서 읽어 둔다
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const pad = (value: number) => String(value).padStart(2, "0");
  const changedShape = (date: Date) =>
    `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`;

  vi.spyOn(Date.prototype, "toLocaleDateString").mockImplementation(function (
    this: Date,
  ) {
    return changedShape(this);
  });
  vi.spyOn(Date.prototype, "toLocaleString").mockImplementation(function (
    this: Date,
  ) {
    return changedShape(this);
  });

  class ChangedDateTimeFormat {
    format(date?: Date | number) {
      return changedShape(new Date(date ?? Date.now()));
    }

    formatToParts(date?: Date | number) {
      const value = new Date(date ?? Date.now());
      return [
        { type: "year", value: String(value.getFullYear()) },
        { type: "literal", value: "/" },
        { type: "month", value: pad(value.getMonth() + 1) },
        { type: "literal", value: "/" },
        { type: "day", value: pad(value.getDate()) },
      ];
    }

    resolvedOptions() {
      return {
        locale: "ko-KR",
        calendar: "gregory",
        numberingSystem: "latn",
        timeZone,
      };
    }
  }

  vi.stubGlobal("Intl", { ...Intl, DateTimeFormat: ChangedDateTimeFormat });
}

describe("formatDate (D13)", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe("브라우저 시간대의 연·월·일을 'YYYY.MM.DD'로 쓴다 (공백 없음, 월·일 두 자리)", () => {
    it.each([
      ["2026-11-06T01:23:45Z", "2026.11.06"],
      ["2026-03-05T12:00:00Z", "2026.03.05"],
      ["2026-01-09T12:00:00.123456Z", "2026.01.09"],
      ["2026-10-30T12:00:00Z", "2026.10.30"],
      ["2026-12-31T05:00:00Z", "2026.12.31"],
      // UTC로는 12월 31일이지만 서울(UTC+9)로는 다음 해 1월 1일이다
      ["2026-12-31T23:59:59Z", "2027.01.01"],
    ])("서울 시간대에서 %s는 %s다", (value, expected) => {
      vi.stubEnv("TZ", "Asia/Seoul");

      expect(formatDate(value)).toBe(expected);
    });
  });

  // Node는 process.env.TZ가 바뀌면 바로 시간대를 다시 읽는다 (vi.stubEnv가 process.env를 바꾼다)
  it("UTC가 아니라 브라우저 시간대의 날짜를 쓴다", () => {
    vi.stubEnv("TZ", "Asia/Seoul");
    // UTC로는 11월 12일이지만 서울(UTC+9)로는 11월 13일이다
    expect(formatDate("2026-11-12T23:30:00Z")).toBe("2026.11.13");

    vi.stubEnv("TZ", "America/Los_Angeles");
    // UTC로는 11월 13일이지만 로스앤젤레스(UTC-8)로는 11월 12일이다
    expect(formatDate("2026-11-13T01:00:00Z")).toBe("2026.11.12");
    // UTC로는 다음 해 1월 1일이지만 로스앤젤레스로는 12월 31일이다
    expect(formatDate("2027-01-01T03:00:00Z")).toBe("2026.12.31");
  });

  // 버그 재현 (docs/features/ranking.md 제약 사항): Node v26 (CLDR 49)에서 "2026/11/13"이 나왔다.
  // 프로젝트 기준 Node v24에서도 재현되도록 지역화 결과를 바꿔 놓고 확인한다.
  describe("실행 환경의 날짜 형식 데이터가 달라도 결과가 같다", () => {
    it.each([
      ["2026-11-12T23:30:00Z", "2026.11.13"],
      ["2026-01-09T12:00:00Z", "2026.01.09"],
      ["2026-12-31T23:59:59Z", "2027.01.01"],
    ])("%s는 %s다", (value, expected) => {
      vi.stubEnv("TZ", "Asia/Seoul");
      stubChangedLocaleData();

      expect(formatDate(value)).toBe(expected);
    });
  });

  describe("값이 없거나 날짜로 읽을 수 없으면 -다", () => {
    it("null이면 -다 (가입일 기능 전에 가입한 회원)", () => {
      expect(formatDate(null)).toBe("-");
    });

    it.each([
      ["빈 문자열", ""],
      ["날짜가 아닌 글자", "not-a-date"],
      ["없는 월", "2026-13-01T00:00:00Z"],
    ])("%s (%j)이면 -다", (_, value) => {
      vi.stubEnv("TZ", "Asia/Seoul");

      expect(formatDate(value)).toBe("-");
    });
  });
});
