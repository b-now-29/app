function twoDigits(value: number): string {
  return String(value).padStart(2, "0");
}

// ISO 시각을 브라우저 시간대의 "YYYY.MM.DD"로 바꾼다. 값이 없거나 읽을 수 없으면 "-"
export function formatDate(value: string | null): string {
  if (value === null) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  const month = twoDigits(date.getMonth() + 1);
  const day = twoDigits(date.getDate());
  return `${date.getFullYear()}.${month}.${day}`;
}
