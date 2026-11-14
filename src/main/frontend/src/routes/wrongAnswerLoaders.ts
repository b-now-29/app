import { redirect } from "react-router";
import type { LoaderFunctionArgs } from "react-router";
import type { QuestionResponse } from "../api/questions";
import { ApiError } from "../api/http";
import { getWrongAnswerQuestions, getWrongAnswers } from "../api/wrongAnswers";
import type { WrongAnswerCategoryResponse } from "../api/wrongAnswers";

// 비회원이면 로그인 화면으로 보낸다. 그 밖의 실패는 에러 화면이 그리도록 다시 던진다.
function rethrowOrLogin(error: unknown): never {
  if (error instanceof ApiError && error.status === 401) {
    throw redirect("/login");
  }
  throw error;
}

export async function wrongAnswersLoader(): Promise<
  readonly WrongAnswerCategoryResponse[]
> {
  try {
    return await getWrongAnswers();
  } catch (error) {
    return rethrowOrLogin(error);
  }
}

export async function wrongAnswerQuizLoader({
  params,
}: LoaderFunctionArgs): Promise<readonly QuestionResponse[]> {
  const { slug } = params;
  if (slug === undefined) {
    throw new Error(
      "wrongAnswerQuizLoader는 :slug가 있는 경로에서만 쓸 수 있습니다",
    );
  }
  try {
    return await getWrongAnswerQuestions(slug);
  } catch (error) {
    return rethrowOrLogin(error);
  }
}
