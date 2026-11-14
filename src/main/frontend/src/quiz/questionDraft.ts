import type { AdminQuestionResponse, QuestionRequest } from "../api/admin";
import type { QuestionType } from "../api/questions";
import { SORT_ORDER_ERROR, parseSortOrder } from "./sortOrder";

// 문제 폼에서 편집 중인 값. 숫자 칸도 입력 중에는 문자열이다.
export type ChoiceDraft = {
  readonly key: number; // 화면용 임시 id (서버로 보내지 않는다)
  readonly content: string;
  readonly correct: boolean;
};

export type QuestionDraft = {
  readonly categorySlug: string;
  readonly type: QuestionType;
  readonly content: string;
  readonly explanation: string;
  readonly modelAnswer: string;
  readonly sortOrder: string;
  readonly choices: readonly ChoiceDraft[];
};

export const MIN_CHOICES = 2; // 보기는 2개 미만으로 지울 수 없다

let nextChoiceKey = 0;

export function blankChoice(): ChoiceDraft {
  nextChoiceKey += 1;
  return { key: nextChoiceKey, content: "", correct: false };
}

// 새 문제: 객관식에 빈 보기 2개로 시작한다 (D10)
export function emptyDraft(categorySlug: string): QuestionDraft {
  return {
    categorySlug,
    type: "MULTIPLE_CHOICE",
    content: "",
    explanation: "",
    modelAnswer: "",
    sortOrder: "0",
    choices: [blankChoice(), blankChoice()],
  };
}

export function draftFromQuestion(
  question: AdminQuestionResponse,
): QuestionDraft {
  return {
    categorySlug: question.categorySlug,
    type: question.type,
    content: question.content,
    explanation: question.explanation ?? "",
    modelAnswer: question.modelAnswer ?? "",
    sortOrder: String(question.sortOrder),
    choices: question.choices.map(({ content, correct }) => ({
      key: blankChoice().key,
      content,
      correct,
    })),
  };
}

// 객관식으로 바꿨는데 보기가 비어 있으면 빈 보기 2개를 채운다
export function withType(
  draft: QuestionDraft,
  type: QuestionType,
): QuestionDraft {
  const choices =
    type === "MULTIPLE_CHOICE" && draft.choices.length === 0
      ? [blankChoice(), blankChoice()]
      : draft.choices;
  return { ...draft, type, choices };
}

// 정답은 하나만 고를 수 있다
export function withCorrectChoice(
  draft: QuestionDraft,
  index: number,
): QuestionDraft {
  return {
    ...draft,
    choices: draft.choices.map((choice, i) => ({
      ...choice,
      correct: i === index,
    })),
  };
}

export function withChoiceContent(
  draft: QuestionDraft,
  index: number,
  content: string,
): QuestionDraft {
  return {
    ...draft,
    choices: draft.choices.map((choice, i) =>
      i === index ? { ...choice, content } : choice,
    ),
  };
}

export function withNewChoice(draft: QuestionDraft): QuestionDraft {
  return { ...draft, choices: [...draft.choices, blankChoice()] };
}

// 보기가 MIN_CHOICES개 이하면 지우지 않고 그대로 돌려준다
export function withoutChoice(
  draft: QuestionDraft,
  index: number,
): QuestionDraft {
  if (!canRemoveChoice(draft)) {
    return draft;
  }
  return { ...draft, choices: draft.choices.filter((_, i) => i !== index) };
}

export function canRemoveChoice(draft: QuestionDraft): boolean {
  return draft.choices.length > MIN_CHOICES;
}

// 칸마다 에러 문구. 에러가 없는 칸은 키를 넣지 않는다 (D23)
export type QuestionDraftErrors = {
  readonly content?: string;
  readonly modelAnswer?: string;
  readonly choices?: Readonly<Record<number, string>>; // 보기 index(0부터) → 문구
  readonly correctChoice?: string;
  readonly sortOrder?: string;
};

export function validateQuestionDraft(
  draft: QuestionDraft,
): QuestionDraftErrors {
  const errors: {
    -readonly [K in keyof QuestionDraftErrors]: QuestionDraftErrors[K];
  } = {};
  if (draft.content.trim() === "") {
    errors.content = "문제 내용을 입력해 주세요.";
  }
  if (parseSortOrder(draft.sortOrder) === null) {
    errors.sortOrder = SORT_ORDER_ERROR;
  }
  const type = draft.type;
  switch (type) {
    case "MULTIPLE_CHOICE": {
      const choiceErrors: Record<number, string> = {};
      draft.choices.forEach((choice, index) => {
        if (choice.content.trim() === "") {
          choiceErrors[index] = `보기 ${index + 1} 내용을 입력해 주세요.`;
        }
      });
      if (Object.keys(choiceErrors).length > 0) {
        errors.choices = choiceErrors;
      }
      if (!draft.choices.some((choice) => choice.correct)) {
        errors.correctChoice = "정답 보기를 하나 선택해 주세요.";
      }
      break;
    }
    case "SHORT_ANSWER":
    case "ESSAY":
      if (draft.modelAnswer.trim() === "") {
        errors.modelAnswer = "모범 답안을 입력해 주세요.";
      }
      break;
    default: {
      const unreachable: never = type;
      throw new Error(`알 수 없는 문제 유형: ${String(unreachable)}`);
    }
  }
  return errors;
}

export type QuestionSubmitResult =
  | { readonly ok: false; readonly errors: QuestionDraftErrors }
  | { readonly ok: true; readonly request: QuestionRequest };

// 검사를 통과하면 저장 요청을 만든다. 표시 순서가 변환되지 않으면 통과로 보지 않는다.
export function prepareQuestionSubmit(
  draft: QuestionDraft,
): QuestionSubmitResult {
  const errors = validateQuestionDraft(draft);
  const sortOrder = parseSortOrder(draft.sortOrder);
  if (Object.keys(errors).length > 0) {
    return { ok: false, errors };
  }
  if (sortOrder === null) {
    return { ok: false, errors: { sortOrder: SORT_ORDER_ERROR } };
  }
  return { ok: true, request: toQuestionRequest(draft, sortOrder) };
}

// sortOrder는 parseSortOrder로 검증한 값을 받는다. 임시 id(key)는 요청에 넣지 않는다.
function toQuestionRequest(
  draft: QuestionDraft,
  sortOrder: number,
): QuestionRequest {
  const base = {
    categorySlug: draft.categorySlug,
    content: draft.content,
    explanation: draft.explanation === "" ? null : draft.explanation,
    sortOrder,
  };
  const type = draft.type;
  switch (type) {
    case "MULTIPLE_CHOICE":
      return {
        ...base,
        type,
        modelAnswer: null,
        choices: draft.choices.map(({ content, correct }) => ({
          content,
          correct,
        })),
      };
    case "SHORT_ANSWER":
    case "ESSAY":
      return { ...base, type, modelAnswer: draft.modelAnswer, choices: [] };
    default: {
      const unreachable: never = type;
      throw new Error(`알 수 없는 문제 유형: ${String(unreachable)}`);
    }
  }
}
