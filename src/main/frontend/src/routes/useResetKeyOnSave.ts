import { useState } from "react";

// action이 { saved: true }를 돌려줄 때마다 올라가는 숫자를 돌려준다.
// 폼의 key로 쓰면 저장에 성공했을 때만 입력칸이 새로 그려져 비워지고,
// 실패했을 때는 key가 그대로라 입력한 값이 남는다.
export function useResetKeyOnSave(actionData: unknown): number {
  const [key, setKey] = useState(0);
  const [seenActionData, setSeenActionData] = useState(actionData);
  if (actionData !== seenActionData) {
    setSeenActionData(actionData);
    if (
      typeof actionData === "object" &&
      actionData !== null &&
      "saved" in actionData
    ) {
      setKey((current) => current + 1);
    }
  }
  return key;
}
