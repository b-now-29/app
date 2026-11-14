import { HStack, Text, VStack } from "@seed-design/react";
import { useId, useState } from "react";
import type { FormEvent } from "react";
import {
  useLoaderData,
  useNavigate,
  useParams,
  useRouteLoaderData,
  useSearchParams,
} from "react-router";
import { createQuestion, deleteQuestion, updateQuestion } from "../api/admin";
import type {
  AdminCategoryResponse,
  AdminQuestionResponse,
} from "../api/admin";
import type { QuestionType } from "../api/questions";
import { errorMessage } from "./actionError";
import { ActionButton } from "seed-design/ui/action-button";
import {
  TextField,
  TextFieldInput,
  TextFieldTextarea,
} from "seed-design/ui/text-field";
import SelectField from "../components/SelectField";
import {
  canRemoveChoice,
  draftFromQuestion,
  emptyDraft,
  prepareQuestionSubmit,
  withChoiceContent,
  withCorrectChoice,
  withNewChoice,
  withType,
  withoutChoice,
} from "../quiz/questionDraft";
import type { QuestionDraft, QuestionDraftErrors } from "../quiz/questionDraft";
import { QUESTION_TYPES, QUESTION_TYPE_NAMES } from "../quiz/questionType";

function listPath(categorySlug: string): string {
  return `/admin/questions?category=${encodeURIComponent(categorySlug)}`;
}

// 새 문제 (/admin/questions/new)
export function AdminQuestionNewPage() {
  const categories = useRouteLoaderData(
    "admin",
  ) as readonly AdminCategoryResponse[];
  const [searchParams] = useSearchParams();
  const category = searchParams.get("category") ?? categories[0]?.slug ?? "";
  return (
    <QuestionForm
      key="new"
      questionId={null}
      initial={emptyDraft(category)}
      categories={categories}
    />
  );
}

// 문제 수정 (/admin/questions/:id)
export function AdminQuestionEditPage() {
  const categories = useRouteLoaderData(
    "admin",
  ) as readonly AdminCategoryResponse[];
  const question = useLoaderData() as AdminQuestionResponse;
  const { id } = useParams();
  return (
    <QuestionForm
      key={id}
      questionId={question.id}
      initial={draftFromQuestion(question)}
      categories={categories}
    />
  );
}

type FormProps = {
  readonly questionId: number | null; // null이면 새 문제
  readonly initial: QuestionDraft;
  readonly categories: readonly AdminCategoryResponse[];
};

function QuestionForm({ questionId, initial, categories }: FormProps) {
  const navigate = useNavigate();
  const [draft, setDraft] = useState<QuestionDraft>(initial);
  const [error, setError] = useState<string | null>(null);
  // 저장을 누를 때 새로 계산한다. 문제 있는 칸 아래에 보여 준다 (D23)
  const [fieldErrors, setFieldErrors] = useState<QuestionDraftErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const correctErrorId = useId();

  function change(changes: Partial<QuestionDraft>) {
    setDraft((current) => ({ ...current, ...changes }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const prepared = prepareQuestionSubmit(draft);
    setFieldErrors(prepared.ok ? {} : prepared.errors);
    if (!prepared.ok) {
      return;
    }
    setSubmitting(true);
    try {
      if (questionId === null) {
        await createQuestion(prepared.request);
      } else {
        await updateQuestion(questionId, prepared.request);
      }
      await navigate(listPath(draft.categorySlug));
    } catch (thrown) {
      setError(errorMessage(thrown));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (questionId === null || !window.confirm("이 문제를 삭제할까요?")) {
      return;
    }
    setError(null);
    try {
      await deleteQuestion(questionId);
      // 폼에서 카테고리 select를 바꿨더라도 불러온 문제의 원래 카테고리 목록으로 간다 (D20)
      await navigate(listPath(initial.categorySlug));
    } catch (thrown) {
      setError(errorMessage(thrown));
    }
  }

  return (
    <form onSubmit={(event) => void handleSubmit(event)}>
      <VStack gap="x4">
        <Text as="h1" textStyle="screenTitle">
          {questionId === null ? "새 문제" : "문제 수정"}
        </Text>
        {error && (
          <Text role="alert" color="fg.critical">
            {error}
          </Text>
        )}
        <SelectField
          label="카테고리"
          value={draft.categorySlug}
          options={categories.map((item) => ({
            value: item.slug,
            label: item.name,
          }))}
          onChange={(categorySlug) => change({ categorySlug })}
        />
        <SelectField
          label="유형"
          value={draft.type}
          options={QUESTION_TYPES.map((type) => ({
            value: type,
            label: QUESTION_TYPE_NAMES[type],
          }))}
          onChange={(type) =>
            setDraft((current) => withType(current, type as QuestionType))
          }
        />
        <TextField
          label="문제"
          value={draft.content}
          onValueChange={({ value }) => change({ content: value })}
          invalid={fieldErrors.content !== undefined}
          errorMessage={fieldErrors.content}
        >
          <TextFieldTextarea />
        </TextField>
        <TextField
          label="해설"
          value={draft.explanation}
          onValueChange={({ value }) => change({ explanation: value })}
        >
          <TextFieldTextarea />
        </TextField>
        <TextField
          label="표시 순서"
          value={draft.sortOrder}
          onValueChange={({ value }) => change({ sortOrder: value })}
          invalid={fieldErrors.sortOrder !== undefined}
          errorMessage={fieldErrors.sortOrder}
        >
          <TextFieldInput type="number" />
        </TextField>
        {draft.type === "MULTIPLE_CHOICE" ? (
          <VStack gap="x3">
            {draft.choices.map((choice, index) => (
              <HStack key={choice.key} gap="x2" align="flex-end">
                <input
                  type="radio"
                  name="correctChoice"
                  aria-label={`보기 ${index + 1} 정답`}
                  checked={choice.correct}
                  aria-invalid={
                    fieldErrors.correctChoice !== undefined ? true : undefined
                  }
                  aria-describedby={
                    fieldErrors.correctChoice !== undefined
                      ? correctErrorId
                      : undefined
                  }
                  onChange={() =>
                    setDraft((current) => withCorrectChoice(current, index))
                  }
                />
                <TextField
                  label={`보기 ${index + 1}`}
                  value={choice.content}
                  onValueChange={({ value }) =>
                    setDraft((current) =>
                      withChoiceContent(current, index, value),
                    )
                  }
                  invalid={fieldErrors.choices?.[index] !== undefined}
                  errorMessage={fieldErrors.choices?.[index]}
                >
                  <TextFieldInput />
                </TextField>
                <ActionButton
                  type="button"
                  variant="neutralWeak"
                  size="small"
                  aria-label={`보기 ${index + 1} 삭제`}
                  disabled={!canRemoveChoice(draft)}
                  onClick={() =>
                    setDraft((current) => withoutChoice(current, index))
                  }
                >
                  삭제
                </ActionButton>
              </HStack>
            ))}
            {fieldErrors.correctChoice && (
              <Text
                id={correctErrorId}
                color="fg.critical"
                textStyle="t3Regular"
              >
                {fieldErrors.correctChoice}
              </Text>
            )}
            <ActionButton
              type="button"
              variant="neutralWeak"
              size="small"
              onClick={() => setDraft((current) => withNewChoice(current))}
            >
              보기 추가
            </ActionButton>
          </VStack>
        ) : (
          <TextField
            label="모범 답안"
            value={draft.modelAnswer}
            onValueChange={({ value }) => change({ modelAnswer: value })}
            invalid={fieldErrors.modelAnswer !== undefined}
            errorMessage={fieldErrors.modelAnswer}
          >
            <TextFieldTextarea />
          </TextField>
        )}
        <HStack gap="x2">
          <ActionButton type="submit" disabled={submitting}>
            저장
          </ActionButton>
          {questionId !== null && (
            <ActionButton
              type="button"
              variant="criticalSolid"
              onClick={() => void handleDelete()}
            >
              삭제
            </ActionButton>
          )}
        </HStack>
      </VStack>
    </form>
  );
}
