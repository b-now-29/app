import type {
  AdminQuestionStatResponse,
  AdminTypeStatResponse,
} from "../api/admin";
import { accuracyPercent } from "./accuracy";
import { QUESTION_TYPE_NAMES } from "./quizScore";

export function categoryTypeStatLine(stat: AdminTypeStatResponse): string {
  const percent = accuracyPercent(stat.solved, stat.correct);
  return `${QUESTION_TYPE_NAMES[stat.type]} 풀이 ${stat.solved}건 중 정답 ${stat.correct}건 (${percent}%)`;
}

export function questionStatLine(
  stat: Pick<AdminQuestionStatResponse, "solvedUsers" | "correctUsers">,
): string {
  if (stat.solvedUsers === 0) {
    return "풀이 기록 없음";
  }
  const percent = accuracyPercent(stat.solvedUsers, stat.correctUsers);
  return `${stat.solvedUsers}명 중 ${stat.correctUsers}명 정답 (${percent}%)`;
}
