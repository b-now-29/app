import { getJson } from "./http";
import type { QuestionResponse, QuestionType } from "./questions";

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

export function getWrongAnswerQuestions(
  slug: string,
): Promise<readonly QuestionResponse[]> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  return getJson(
    `/api/v1/users/me/wrong-answers/${encodeURIComponent(slug)}/questions`,
  );
}
