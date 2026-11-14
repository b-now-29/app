import type { ActionFunctionArgs } from "react-router";
import { changeNickname, changePassword } from "../api/users";
import { passwordsMatch } from "../user/password";
import { errorMessage } from "./actionError";
import { formString } from "./formValue";

// 결과는 intent(어느 폼인지)와 kind(saved | error | mismatch)로 나눈다.
// kind: "saved"는 화면이 입력칸을 비우는 신호이기도 하다 (useResetKeyOnSave).
export type MeActionResult =
  | { readonly intent: "nickname"; readonly kind: "saved" }
  | {
      readonly intent: "nickname";
      readonly kind: "error";
      readonly error: string;
    }
  | { readonly intent: "password"; readonly kind: "saved" }
  | {
      readonly intent: "password";
      readonly kind: "error";
      readonly error: string;
    }
  | { readonly intent: "password"; readonly kind: "mismatch" };

// 폼의 hidden 필드 intent로 닉네임 / 비밀번호 폼을 나눈다.
// 성공하면 kind: "saved" (닉네임은 이 뒤에 loader가 다시 실행되어 메뉴와 화면이 바뀐다).
// 실패하면 에러 문구만 돌려주어 화면에 입력이 그대로 남는다.
export async function meAction({
  request,
}: ActionFunctionArgs): Promise<MeActionResult> {
  const formData = await request.formData();
  if (formString(formData, "intent") === "password") {
    return changePasswordFromForm(formData);
  }
  return changeNicknameFromForm(formData);
}

async function changeNicknameFromForm(
  formData: FormData,
): Promise<MeActionResult> {
  try {
    await changeNickname(formString(formData, "nickname"));
  } catch (thrown) {
    return { intent: "nickname", kind: "error", error: errorMessage(thrown) };
  }
  return { intent: "nickname", kind: "saved" };
}

async function changePasswordFromForm(
  formData: FormData,
): Promise<MeActionResult> {
  const newPassword = formString(formData, "newPassword");
  if (!passwordsMatch(newPassword, formString(formData, "confirmPassword"))) {
    return { intent: "password", kind: "mismatch" };
  }
  try {
    await changePassword(formString(formData, "currentPassword"), newPassword);
  } catch (thrown) {
    return { intent: "password", kind: "error", error: errorMessage(thrown) };
  }
  return { intent: "password", kind: "saved" };
}
