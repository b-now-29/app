import type { QuestionResponse, QuestionType } from "../api/questions";

// 적은 순서가 점수 줄 순서다 (R5)
const QUESTION_TYPE_NAMES: Record<QuestionType, string> = {
  MULTIPLE_CHOICE: "객관식",
  SHORT_ANSWER: "주관식",
  ESSAY: "서술형",
};

// Object.keys의 반환 타입은 string[]이지만, 위 표의 키는 QuestionType뿐이다
const QUESTION_TYPES = Object.keys(QUESTION_TYPE_NAMES) as QuestionType[];

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
