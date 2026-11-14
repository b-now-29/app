import type { QuestionResponse, QuestionType } from "../api/questions";
import { QUESTION_TYPES, QUESTION_TYPE_NAMES } from "./questionType";

// 점수 화면에 유형별 줄을 보여 주기 위해 문제 목록에서 유형별 문제 수를 센다
export function countQuestionsByType(
  questions: readonly QuestionResponse[],
): Record<QuestionType, number> {
  return questions.reduce<Record<QuestionType, number>>(
    (counts, { type }) => ({ ...counts, [type]: counts[type] + 1 }),
    { MULTIPLE_CHOICE: 0, SHORT_ANSWER: 0, ESSAY: 0 },
  );
}

// 유형별로 합산하지 않고 한 줄씩 만들며, 문제가 없는 유형의 줄은 만들지 않는다 (R5, D11)
export function scoreLines(
  counts: Readonly<Record<QuestionType, number>>,
  correctCounts: Readonly<Record<QuestionType, number>>,
): readonly string[] {
  return QUESTION_TYPES.filter((type) => counts[type] > 0).map((type) => {
    const wrongCount = counts[type] - correctCounts[type];
    return `${QUESTION_TYPE_NAMES[type]} ${counts[type]}문제 중 정답 ${correctCounts[type]}문제, 오답 ${wrongCount}문제`;
  });
}
