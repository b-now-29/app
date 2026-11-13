import { getJson } from "./attempts";
import { NotFoundError } from "./questions";
import type { QuestionResponse, QuestionType } from "./questions";
import { toApiError } from "./users";

export type WrongAnswerQuestionResponse = {
  readonly questionId: number;
  readonly content: string;
  readonly type: QuestionType;
  readonly attemptedAt: string;
};

export type WrongAnswerCategoryResponse = {
  readonly slug: string;
  readonly name: string;
  readonly questions: readonly WrongAnswerQuestionResponse[];
};

export function getWrongAnswers(): Promise<
  readonly WrongAnswerCategoryResponse[]
> {
  return getJson("/api/v1/users/me/wrong-answers");
}

export async function getWrongAnswerQuestions(
  slug: string,
): Promise<readonly QuestionResponse[]> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  const response = await fetch(
    `/api/v1/users/me/wrong-answers/${encodeURIComponent(slug)}/questions`,
  );
  if (response.status === 404) {
    throw new NotFoundError(`없는 카테고리: ${slug}`);
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as readonly QuestionResponse[];
}
