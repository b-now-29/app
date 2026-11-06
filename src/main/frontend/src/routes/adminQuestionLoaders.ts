import type { LoaderFunctionArgs } from "react-router";
import { getAdminQuestion, getAdminQuestions } from "../api/admin";
import { rethrowAsRedirect } from "./adminLoader";

// 카테고리를 아직 고르지 않았으면 문제를 부르지 않고 빈 목록을 돌려준다
export async function adminQuestionListLoader({ request }: LoaderFunctionArgs) {
  const category = new URL(request.url).searchParams.get("category") ?? "";
  if (category === "") {
    return { category, questions: [] };
  }
  try {
    return { category, questions: await getAdminQuestions(category) };
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}

export async function adminQuestionLoader({ params }: LoaderFunctionArgs) {
  try {
    return await getAdminQuestion(Number(params.id));
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}
