import { redirect } from "react-router";
import type { ActionFunctionArgs } from "react-router";
import { login, logout, signUp } from "../api/users";
import { toActionError } from "./actionError";
import type { ActionError } from "./actionError";
import { formString } from "./formValue";

function readCredentials(formData: FormData) {
  return {
    email: formString(formData, "email"),
    password: formString(formData, "password"),
  };
}

// action: 폼이 제출(POST)되면 React Router가 호출하는 함수.
// 성공하면 redirect로 홈으로 보내고, 이때 root loader가 다시 실행되어 메뉴가 갱신된다.
export async function loginAction({ request }: ActionFunctionArgs) {
  const { email, password } = readCredentials(await request.formData());
  try {
    await login(email, password);
  } catch (thrown) {
    return toActionError(thrown, "로그인에 실패했습니다. 다시 시도해 주세요.");
  }
  return redirect("/");
}

export async function signUpAction({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const { email, password } = readCredentials(formData);
  try {
    await signUp(email, password, formString(formData, "nickname"));
  } catch (thrown) {
    return toActionError(
      thrown,
      "회원가입에 실패했습니다. 다시 시도해 주세요.",
    );
  }
  return redirect("/");
}

// 로그아웃이 실패하면 로그인 상태를 유지하고 에러만 돌려준다 (D15).
// 성공하면 action 뒤에 loader가 자동으로 다시 실행되어 메뉴가 비회원 상태로 바뀐다.
export async function logoutAction(): Promise<ActionError | null> {
  try {
    await logout();
  } catch {
    return { error: "로그아웃에 실패했습니다. 다시 시도해 주세요." };
  }
  return null;
}
