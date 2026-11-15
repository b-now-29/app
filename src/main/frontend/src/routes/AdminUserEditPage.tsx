import { HStack, Text, VStack } from "@seed-design/react";
import { useState } from "react";
import type { FormEvent } from "react";
import { useLoaderData, useNavigate } from "react-router";
import { changeUserRole } from "../api/admin";
import NicknameText from "../components/NicknameText";
import {
  canSaveUserForm,
  formFromUser,
  prepareRoleChange,
  withCategoryChecked,
  withRole,
} from "../user/adminUserForm";
import type { AdminUserForm } from "../user/adminUserForm";
import { errorMessage } from "./actionError";
import type { adminUserLoader } from "./adminUsersLoader";
import { ActionButton } from "seed-design/ui/action-button";

export default function AdminUserEditPage() {
  const { user, categories } = useLoaderData<typeof adminUserLoader>();
  const navigate = useNavigate();
  const [form, setForm] = useState<AdminUserForm>(() => formFromUser(user));
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const request = prepareRoleChange(form);
    if (request === null || submitting) {
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await changeUserRole(user.id, request.role, request.categorySlugs);
      await navigate("/admin/users");
    } catch (thrown) {
      setError(errorMessage(thrown));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        회원 수정
      </Text>
      <VStack>
        <Text textStyle="t4Bold">{user.email}</Text>
        <Text textStyle="t3Regular">
          <NicknameText member={user} />
        </Text>
      </VStack>
      {error && (
        <Text role="alert" color="fg.critical">
          {error}
        </Text>
      )}
      <form onSubmit={(event) => void handleSubmit(event)}>
        <VStack gap="x4">
          <VStack as="fieldset" gap="x2">
            <legend>역할</legend>
            <label>
              <input
                type="radio"
                name="role"
                checked={form.role === "USER"}
                onChange={() => setForm((current) => withRole(current, "USER"))}
              />{" "}
              일반 회원
            </label>
            <label>
              <input
                type="radio"
                name="role"
                checked={form.role === "CATEGORY_ADMIN"}
                onChange={() =>
                  setForm((current) => withRole(current, "CATEGORY_ADMIN"))
                }
              />{" "}
              카테고리 관리자
            </label>
          </VStack>
          {form.role === "CATEGORY_ADMIN" && (
            <VStack as="fieldset" gap="x2">
              <legend>담당 카테고리</legend>
              {categories.map((category) => (
                <label key={category.slug}>
                  <input
                    type="checkbox"
                    checked={form.categorySlugs.includes(category.slug)}
                    onChange={(event) =>
                      setForm((current) =>
                        withCategoryChecked(
                          current,
                          category.slug,
                          event.target.checked,
                        ),
                      )
                    }
                  />{" "}
                  {category.name}
                </label>
              ))}
            </VStack>
          )}
          <HStack gap="x2">
            <ActionButton
              type="submit"
              disabled={submitting || !canSaveUserForm(form)}
            >
              저장
            </ActionButton>
          </HStack>
        </VStack>
      </form>
    </VStack>
  );
}
