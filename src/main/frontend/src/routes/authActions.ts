import { redirect } from "react-router";
import type { ActionFunctionArgs } from "react-router";
import { ApiError, login, logout, signUp } from "../api/users";

export type ActionError = { readonly error: string };

function readCredentials(formData: FormData) {
  const email = formData.get("email");
  const password = formData.get("password");
  return {
    email: typeof email === "string" ? email : "",
    password: typeof password === "string" ? password : "",
  };
}

function toActionError(thrown: unknown, fallback: string): ActionError {
  return { error: thrown instanceof ApiError ? thrown.message : fallback };
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
  const { email, password } = readCredentials(await request.formData());
  try {
    await signUp(email, password);
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
