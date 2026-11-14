import type { QuestionType } from "../api/questions";

// 적은 순서가 점수 줄 순서다 (R5). 유형 선택 칸의 순서이기도 하다.
export const QUESTION_TYPE_NAMES: Record<QuestionType, string> = {
  MULTIPLE_CHOICE: "객관식",
  SHORT_ANSWER: "주관식",
  ESSAY: "서술형",
};

// Object.keys의 반환 타입은 string[]이지만, 위 표의 키는 QuestionType뿐이다
export const QUESTION_TYPES = Object.keys(
  QUESTION_TYPE_NAMES,
) as readonly QuestionType[];
