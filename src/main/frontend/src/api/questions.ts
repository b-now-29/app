import { getJson, sendJson, sendJsonForNoContent } from "./http";

export type ChoiceResponse = {
  readonly id: number;
  readonly content: string;
};

export type QuestionType = "MULTIPLE_CHOICE" | "SHORT_ANSWER" | "ESSAY";

export type TextAnswerResponse = {
  readonly modelAnswer: string;
  readonly explanation: string | null;
};

export type QuestionResponse = {
  readonly id: number;
  readonly type: QuestionType;
  readonly content: string;
  readonly choices: readonly ChoiceResponse[];
};

export type AnswerResponse = {
  readonly correct: boolean;
  readonly correctChoiceId: number;
  readonly explanation: string | null;
  // 회원의 풀이 기록으로 저장됐는지. false면 비회원이거나 로그인이 만료된 것이다 (D13)
  readonly recorded: boolean;
};

export function getQuestions(
  slug: string,
): Promise<readonly QuestionResponse[]> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  return getJson(`/api/v1/categories/${encodeURIComponent(slug)}/questions`);
}

export function submitAnswer(
  questionId: number,
  choiceId: number,
): Promise<AnswerResponse> {
  return sendJson("POST", `/api/v1/questions/${questionId}/answers`, {
    choiceId,
  });
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
  return sendJson("POST", `/api/v1/questions/${questionId}/text-answers`, {
    answer,
  });
}
