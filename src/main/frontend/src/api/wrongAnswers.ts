import { z } from "zod";
import { getJson } from "./http";
import {
  QuestionListSchema,
  QuestionTypeSchema,
  type QuestionResponse,
} from "./questions";

const WrongAnswerQuestionSchema = z
  .object({
    questionId: z.number(),
    content: z.string(),
    type: QuestionTypeSchema,
    attemptedAt: z.string(),
  })
  .readonly();
export type WrongAnswerQuestionResponse = z.infer<
  typeof WrongAnswerQuestionSchema
>;

const WrongAnswerCategorySchema = z
  .object({
    slug: z.string(),
    name: z.string(),
    questions: z.array(WrongAnswerQuestionSchema).readonly(),
  })
  .readonly();
export type WrongAnswerCategoryResponse = z.infer<
  typeof WrongAnswerCategorySchema
>;

const WrongAnswerListSchema = z.array(WrongAnswerCategorySchema).readonly();

export function getWrongAnswers(): Promise<
  readonly WrongAnswerCategoryResponse[]
> {
  return getJson("/api/v1/users/me/wrong-answers", WrongAnswerListSchema);
}

export function getWrongAnswerQuestions(
  slug: string,
): Promise<readonly QuestionResponse[]> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  return getJson(
    `/api/v1/users/me/wrong-answers/${encodeURIComponent(slug)}/questions`,
    QuestionListSchema,
  );
}
