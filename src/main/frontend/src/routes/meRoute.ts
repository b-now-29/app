import type { ActionFunctionArgs } from "react-router";
import { changeNickname } from "../api/users";
import { toActionError } from "./actionError";
import type { ActionError } from "./actionError";
import { formString } from "./formValue";

// 저장에 성공했다는 표시. 화면이 입력칸을 서버가 저장한 값으로 다시 그리는 신호로 쓴다
export interface NicknameSaved {
  readonly saved: true;
}

// 저장에 성공하면 { saved: true }. 이 뒤에 loader(root 포함)가 다시 실행되어 메뉴와 화면이 바뀐다.
// 실패하면 에러 문구만 돌려주어 화면에 입력이 그대로 남는다.
export async function meAction({
  request,
}: ActionFunctionArgs): Promise<ActionError | NicknameSaved> {
  const formData = await request.formData();
  try {
    await changeNickname(formString(formData, "nickname"));
  } catch (thrown) {
    return toActionError(thrown, "요청에 실패했습니다. 다시 시도해 주세요.");
  }
  return { saved: true };
}
