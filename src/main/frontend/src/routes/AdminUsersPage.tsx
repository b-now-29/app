import { HStack, Text, VStack } from "@seed-design/react";
import { Link, useLoaderData } from "react-router";
import type { adminUsersLoader } from "./adminUsersLoader";
import { useCurrentUser } from "./routeLoaderData";
import { isOwner } from "../user/role";
import { formatDate } from "../quiz/date";
import NicknameText from "../components/NicknameText";
import { ActionButton } from "seed-design/ui/action-button";

export default function AdminUsersPage() {
  const users = useLoaderData<typeof adminUsersLoader>();
  const me = useCurrentUser();

  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        회원 관리
      </Text>
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
                {member.categorySlugs.length > 0 && (
                  <Text textStyle="t3Regular">
                    {member.categorySlugs.join(", ")}
                  </Text>
                )}
              </VStack>
              {/* 운영자(OWNER)의 역할은 바꿀 수 없으므로 링크가 없다 */}
              {!isOwner(member.role) && (
                <ActionButton asChild variant="neutralWeak" size="small">
                  <Link to={`/admin/users/${member.id}`}>수정 →</Link>
                </ActionButton>
              )}
            </HStack>
          </li>
        ))}
      </ul>
    </VStack>
  );
}
