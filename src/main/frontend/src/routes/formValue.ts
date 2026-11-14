// 폼에서 문자열 값을 꺼낸다. 값이 없거나 파일이면 "". 공백은 지우지 않는다 (정규화는 서버 책임).
export function formString(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}
