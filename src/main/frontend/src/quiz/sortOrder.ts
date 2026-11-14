export const SORT_ORDER_ERROR = "표시 순서는 정수로 입력해 주세요.";

// 앞뒤 공백은 무시하고, 정수가 아니면 null (D19)
export function parseSortOrder(value: string): number | null {
  const trimmed = value.trim();
  return /^-?\d+$/.test(trimmed) ? Number(trimmed) : null;
}
