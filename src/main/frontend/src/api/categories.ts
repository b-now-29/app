export type CategoryResponse = {
  readonly slug: string;
  readonly name: string;
};

export async function getCategories(): Promise<readonly CategoryResponse[]> {
  const response = await fetch("/api/v1/categories");
  // fetch는 404·500 응답에도 실패로 끝나지 않으므로 직접 확인한다
  if (!response.ok) {
    throw new Error(`카테고리 목록 조회 실패: HTTP ${response.status}`);
  }
  return (await response.json()) as readonly CategoryResponse[];
}
