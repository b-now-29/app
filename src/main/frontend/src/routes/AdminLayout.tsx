import { HStack, VStack } from "@seed-design/react";
import { Link, Outlet } from "react-router";
import { ActionButton } from "seed-design/ui/action-button";

export default function AdminLayout() {
  return (
    <VStack gap="x4">
      <HStack as="nav" aria-label="관리 메뉴" gap="x1" bleedLeft="x3_5">
        <ActionButton asChild variant="neutralWeak" size="small">
          <Link to="/admin/categories">카테고리</Link>
        </ActionButton>
        <ActionButton asChild variant="neutralWeak" size="small">
          <Link to="/admin/questions">문제</Link>
        </ActionButton>
        <ActionButton asChild variant="neutralWeak" size="small">
          <Link to="/admin/users">회원</Link>
        </ActionButton>
      </HStack>
      <Outlet />
    </VStack>
  );
}
