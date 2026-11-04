import { Text, VStack } from "@seed-design/react";
import { Link } from "react-router";
import { ActionButton } from "seed-design/ui/action-button";

export default function NotFoundPage() {
  return (
    <VStack gap="x4" alignItems="flex-start">
      <Text as="h1" textStyle="screenTitle">
        페이지를 찾을 수 없습니다
      </Text>
      <ActionButton asChild variant="neutralSolid">
        <Link to="/">홈으로</Link>
      </ActionButton>
    </VStack>
  );
}
