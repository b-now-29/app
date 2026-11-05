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
};

// 없는 카테고리(404)와 서버 장애를 화면에서 다르게 보여 주기 위한 구분용 에러
export class NotFoundError extends Error {}

export async function getQuestions(
  slug: string,
): Promise<readonly QuestionResponse[]> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  const response = await fetch(
    `/api/v1/categories/${encodeURIComponent(slug)}/questions`,
  );
  if (response.status === 404) {
    throw new NotFoundError(`없는 카테고리: ${slug}`);
  }
  if (!response.ok) {
    throw new Error(`문제 목록 조회 실패: HTTP ${response.status}`);
  }
  return (await response.json()) as readonly QuestionResponse[];
}

export async function submitAnswer(
  questionId: number,
  choiceId: number,
): Promise<AnswerResponse> {
  const response = await fetch(`/api/v1/questions/${questionId}/answers`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ choiceId }),
  });
  if (!response.ok) {
    throw new Error(`채점 요청 실패: HTTP ${response.status}`);
  }
  return (await response.json()) as AnswerResponse;
}

export async function submitTextAnswer(
  questionId: number,
  answer: string,
): Promise<TextAnswerResponse> {
  const response = await fetch(`/api/v1/questions/${questionId}/text-answers`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ answer }),
  });
  if (!response.ok) {
    throw new Error(`답 제출 실패: HTTP ${response.status}`);
  }
  return (await response.json()) as TextAnswerResponse;
}
