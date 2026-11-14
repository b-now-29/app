import type { LoaderFunctionArgs } from "react-router";
import type { QuestionResponse } from "../api/questions";
import { getWrongAnswerQuestions, getWrongAnswers } from "../api/wrongAnswers";
import type { WrongAnswerCategoryResponse } from "../api/wrongAnswers";
import { rethrowWithLoginRedirect } from "./loginRedirect";

export async function wrongAnswersLoader(): Promise<
  readonly WrongAnswerCategoryResponse[]
> {
  try {
    return await getWrongAnswers();
  } catch (error) {
    return rethrowWithLoginRedirect(error);
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
    return rethrowWithLoginRedirect(error);
  }
}
