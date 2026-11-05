import type { QuestionResponse } from "../api/questions";

export type QuestionCounts = {
  readonly multipleChoice: number;
  readonly shortAnswer: number;
};

// 점수 화면에 유형별 줄을 보여 주기 위해 문제 목록에서 유형별 문제 수를 센다
export function countQuestionsByType(
  questions: readonly QuestionResponse[],
): QuestionCounts {
  return {
    multipleChoice: questions.filter((q) => q.type === "MULTIPLE_CHOICE")
      .length,
    shortAnswer: questions.filter((q) => q.type === "SHORT_ANSWER").length,
  };
}

// 문제가 없는 유형의 줄은 만들지 않는다 (R6)
export function scoreLines(
  counts: QuestionCounts,
  correctCount: number,
  selfGradeCorrectCount: number,
): readonly string[] {
  const lines: string[] = [];
  if (counts.multipleChoice > 0) {
    lines.push(
      `객관식 ${counts.multipleChoice}문제 중 ${correctCount}문제 정답`,
    );
  }
  if (counts.shortAnswer > 0) {
    lines.push(
      `주관식 ${counts.shortAnswer}문제 중 ${selfGradeCorrectCount}문제 맞음`,
    );
  }
  return lines;
}
