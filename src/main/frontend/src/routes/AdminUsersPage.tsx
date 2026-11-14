import { HStack, Text, VStack } from "@seed-design/react";
import { useState } from "react";
import {
  useLoaderData,
  useRevalidator,
  useRouteLoaderData,
} from "react-router";
import { changeUserRole } from "../api/admin";
import type { AdminUserResponse } from "../api/admin";
import { errorMessage } from "../api/users";
import type { UserResponse } from "../api/users";
import { formatDate } from "../quiz/date";
import { roleAction } from "../quiz/adminUsers";
import NicknameText from "../components/NicknameText";
import { ActionButton } from "seed-design/ui/action-button";

export default function AdminUsersPage() {
  const users = useLoaderData() as readonly AdminUserResponse[];
  const me = useRouteLoaderData("root") as UserResponse | null;
  const { revalidate } = useRevalidator();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleChange(member: AdminUserResponse) {
    const { label, nextRole } = roleAction(member.role);
    if (pending) {
      return;
    }
    if (!window.confirm(`${member.email} 회원을 "${label}"할까요?`)) {
      return;
    }
    setError(null);
    setPending(true);
    try {
      try {
        await changeUserRole(member.id, nextRole);
      } catch (thrown) {
        setError(errorMessage(thrown));
      }
      // 실패해도 서버의 현재 값으로 목록을 맞춘다
      await revalidate();
    } catch (thrown) {
      setError(errorMessage(thrown));
    } finally {
      setPending(false);
    }
  }

  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        회원 관리
      </Text>
      {error && (
        <Text role="alert" color="fg.critical">
          {error}
        </Text>
      )}
      <ul aria-label="회원 목록">
        {users.map((member) => (
          <li key={member.id}>
            <HStack gap="x2" align="center" justify="space-between">
              <VStack>
                <HStack gap="x1" align="center">
                  <Text textStyle="t4Bold">{member.email}</Text>
                  {member.id === me?.id && (
                    <Text textStyle="t3Regular">나</Text>
                  )}
                </HStack>
                <Text textStyle="t3Regular">
                  <NicknameText member={member} />
                </Text>
                <HStack gap="x1">
                  <Text textStyle="t3Regular">{member.role}</Text>
                  <Text textStyle="t3Regular">
                    {formatDate(member.createdAt)}
                  </Text>
                </HStack>
              </VStack>
              <ActionButton
                type="button"
                variant="neutralWeak"
                size="small"
                disabled={pending || member.id === me?.id}
                onClick={() => void handleChange(member)}
              >
                {roleAction(member.role).label}
              </ActionButton>
            </HStack>
          </li>
        ))}
      </ul>
    </VStack>
  );
}
