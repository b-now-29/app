// 풀이 시각 (ISO-8601)을 브라우저 시간대의 "2026. 11. 6." 형식 날짜로 바꾼다
export function formatAttemptDate(attemptedAt: string): string {
  return new Date(attemptedAt).toLocaleDateString("ko-KR");
}
