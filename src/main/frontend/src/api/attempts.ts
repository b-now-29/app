import { z } from "zod";
import { QuestionTypeSchema } from "./questions";
import { getJson } from "./http";

const TypeStatSchema = z
  .object({
    type: QuestionTypeSchema,
    solvedQuestions: z.number(),
    correctQuestions: z.number(),
  })
  .readonly();
export type TypeStatResponse = z.infer<typeof TypeStatSchema>;

const CategoryStatSchema = z
  .object({
    slug: z.string(),
    name: z.string(),
    types: z.array(TypeStatSchema).readonly(),
  })
  .readonly();
export type CategoryStatResponse = z.infer<typeof CategoryStatSchema>;

const AttemptSchema = z
  .object({
    id: z.number(),
    questionId: z.number(),
    questionContent: z.string(),
    questionType: QuestionTypeSchema,
    categoryName: z.string(),
    correct: z.boolean(),
    attemptedAt: z.string(),
  })
  .readonly();
export type AttemptResponse = z.infer<typeof AttemptSchema>;

const CategoryStatListSchema = z.array(CategoryStatSchema).readonly();
const AttemptListSchema = z.array(AttemptSchema).readonly();

export function getCategoryStats(): Promise<readonly CategoryStatResponse[]> {
  return getJson("/api/v1/users/me/category-stats", CategoryStatListSchema);
}

export function getRecentAttempts(): Promise<readonly AttemptResponse[]> {
  return getJson("/api/v1/users/me/attempts", AttemptListSchema);
}
