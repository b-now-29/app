import type {
  WrongAnswerCategoryResponse,
  WrongAnswerQuestionResponse,
} from "../api/wrongAnswers";
import { formatDate } from "./date";
import { QUESTION_TYPE_NAMES } from "./questionType";

// 카테고리 제목: "이름 N문제"
export function wrongAnswerCategoryTitle(
  category: WrongAnswerCategoryResponse,
): string {
  return `${category.name} ${category.questions.length}문제`;
}

// 문제 아래 줄: "유형 이름 · 날짜"
export function wrongAnswerQuestionMeta(
  question: WrongAnswerQuestionResponse,
): string {
  return `${QUESTION_TYPE_NAMES[question.type]} · ${formatDate(question.attemptedAt)}`;
}
