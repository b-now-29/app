import { z } from "zod";
import { getJson, sendJson, sendJsonForNoContent } from "./http";

const ChoiceSchema = z
  .object({
    id: z.number(),
    content: z.string(),
  })
  .readonly();
export type ChoiceResponse = z.infer<typeof ChoiceSchema>;

export const QuestionTypeSchema = z.enum([
  "MULTIPLE_CHOICE",
  "SHORT_ANSWER",
  "ESSAY",
]);
export type QuestionType = z.infer<typeof QuestionTypeSchema>;

const TextAnswerSchema = z
  .object({
    modelAnswer: z.string(),
    explanation: z.string().nullable(),
  })
  .readonly();
export type TextAnswerResponse = z.infer<typeof TextAnswerSchema>;

export const QuestionSchema = z
  .object({
    id: z.number(),
    type: QuestionTypeSchema,
    content: z.string(),
    choices: z.array(ChoiceSchema).readonly(),
  })
  .readonly();
export type QuestionResponse = z.infer<typeof QuestionSchema>;

const AnswerSchema = z
  .object({
    correct: z.boolean(),
    correctChoiceId: z.number(),
    explanation: z.string().nullable(),
    // 회원의 풀이 기록으로 저장됐는지. false면 비회원이거나 로그인이 만료된 것이다 (D13)
    recorded: z.boolean(),
  })
  .readonly();
export type AnswerResponse = z.infer<typeof AnswerSchema>;

export const QuestionListSchema = z.array(QuestionSchema).readonly();

export function getQuestions(
  slug: string,
): Promise<readonly QuestionResponse[]> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  return getJson(
    `/api/v1/categories/${encodeURIComponent(slug)}/questions`,
    QuestionListSchema,
  );
}

export function submitAnswer(
  questionId: number,
  choiceId: number,
): Promise<AnswerResponse> {
  return sendJson(
    "POST",
    `/api/v1/questions/${questionId}/answers`,
    { choiceId },
    AnswerSchema,
  );
}

// 회원의 자기 채점 결과를 기록으로 저장한다.
export function submitSelfGrade(
  questionId: number,
  correct: boolean,
): Promise<void> {
  return sendJsonForNoContent(
    "POST",
    `/api/v1/questions/${questionId}/self-grades`,
    { correct },
  );
}

export function submitTextAnswer(
  questionId: number,
  answer: string,
): Promise<TextAnswerResponse> {
  return sendJson(
    "POST",
    `/api/v1/questions/${questionId}/text-answers`,
    { answer },
    TextAnswerSchema,
  );
}
