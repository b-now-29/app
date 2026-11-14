import { HStack, Text, VStack } from "@seed-design/react";
import { useState } from "react";
import { useRevalidator, useRouteLoaderData } from "react-router";
import type { FormEvent } from "react";
import { createCategory, deleteCategory, updateCategory } from "../api/admin";
import type { AdminCategoryResponse } from "../api/admin";
import { errorMessage } from "./actionError";
import { validateCategoryForm } from "../quiz/categoryForm";
import type {
  CategoryFormErrors,
  CategoryFormValues,
} from "../quiz/categoryForm";
import { parseSortOrder } from "../quiz/questionDraft";
import { ActionButton } from "seed-design/ui/action-button";
import { TextField, TextFieldInput } from "seed-design/ui/text-field";

type FormValues = CategoryFormValues;

const EMPTY_FORM: FormValues = {
  slug: "",
  name: "",
  description: "",
  sortOrder: "0",
};

export default function AdminCategoriesPage() {
  const categories = useRouteLoaderData(
    "admin",
  ) as readonly AdminCategoryResponse[];
  const { revalidate } = useRevalidator();
  const [form, setForm] = useState<FormValues>(EMPTY_FORM);
  // null이면 추가, 값이 있으면 그 slug의 카테고리를 수정 중
  const [editingSlug, setEditingSlug] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // 저장을 누를 때 새로 계산한다. 칸 아래에 보여 준다 (D23)
  const [fieldErrors, setFieldErrors] = useState<CategoryFormErrors>({});
  const [submitting, setSubmitting] = useState(false);

  function change(field: keyof FormValues, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function startEdit(category: AdminCategoryResponse) {
    setEditingSlug(category.slug);
    setError(null);
    setFieldErrors({});
    setForm({
      slug: category.slug,
      name: category.name,
      description: category.description ?? "",
      sortOrder: String(category.sortOrder),
    });
  }

  function reset() {
    setEditingSlug(null);
    setFieldErrors({});
    setForm(EMPTY_FORM);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const errors = validateCategoryForm(
      form,
      editingSlug === null ? "create" : "edit",
    );
    setFieldErrors(errors);
    const sortOrder = parseSortOrder(form.sortOrder);
    if (Object.keys(errors).length > 0 || sortOrder === null) {
      return;
    }
    setSubmitting(true);
    const body = {
      name: form.name,
      description: form.description === "" ? null : form.description,
      sortOrder,
    };
    try {
      if (editingSlug === null) {
        await createCategory({ slug: form.slug, ...body });
      } else {
        await updateCategory(editingSlug, body);
      }
      reset();
      await revalidate();
    } catch (thrown) {
      setError(errorMessage(thrown));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(category: AdminCategoryResponse) {
    if (!window.confirm(`"${category.name}" 카테고리를 삭제할까요?`)) {
      return;
    }
    setError(null);
    try {
      await deleteCategory(category.slug);
      if (editingSlug === category.slug) {
        reset();
      }
      await revalidate();
    } catch (thrown) {
      setError(errorMessage(thrown));
    }
  }

  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        카테고리 관리
      </Text>
      {error && (
        <Text role="alert" color="fg.critical">
          {error}
        </Text>
      )}
      <ul aria-label="카테고리 목록">
        {categories.map((category) => (
          <li key={category.slug}>
            <HStack gap="x2" align="center" justify="space-between">
              <VStack>
                <Text textStyle="t4Bold">{category.name}</Text>
                <Text textStyle="t3Regular">
                  {category.slug} · 문제 {category.questionCount}개
                </Text>
              </VStack>
              <HStack gap="x1">
                <ActionButton
                  type="button"
                  variant="neutralWeak"
                  size="small"
                  onClick={() => startEdit(category)}
                >
                  수정
                </ActionButton>
                <ActionButton
                  type="button"
                  variant="criticalSolid"
                  size="small"
                  onClick={() => void handleDelete(category)}
                >
                  삭제
                </ActionButton>
              </HStack>
            </HStack>
          </li>
        ))}
      </ul>
      <form onSubmit={(event) => void handleSubmit(event)}>
        <VStack gap="x4">
          <TextField
            label="slug"
            value={form.slug}
            onValueChange={({ value }) => change("slug", value)}
            disabled={editingSlug !== null}
            invalid={fieldErrors.slug !== undefined}
            errorMessage={fieldErrors.slug}
          >
            <TextFieldInput />
          </TextField>
          <TextField
            label="이름"
            value={form.name}
            onValueChange={({ value }) => change("name", value)}
            invalid={fieldErrors.name !== undefined}
            errorMessage={fieldErrors.name}
          >
            <TextFieldInput />
          </TextField>
          <TextField
            label="설명"
            value={form.description}
            onValueChange={({ value }) => change("description", value)}
          >
            <TextFieldInput />
          </TextField>
          <TextField
            label="표시 순서"
            value={form.sortOrder}
            onValueChange={({ value }) => change("sortOrder", value)}
            invalid={fieldErrors.sortOrder !== undefined}
            errorMessage={fieldErrors.sortOrder}
          >
            <TextFieldInput type="number" />
          </TextField>
          <HStack gap="x2">
            <ActionButton type="submit" disabled={submitting}>
              {editingSlug === null ? "추가" : "저장"}
            </ActionButton>
            {editingSlug !== null && (
              <ActionButton type="button" variant="neutralWeak" onClick={reset}>
                취소
              </ActionButton>
            )}
          </HStack>
        </VStack>
      </form>
    </VStack>
  );
}
