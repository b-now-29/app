import { HStack, VStack } from "@seed-design/react";
import { Link, Outlet } from "react-router";
import { ActionButton } from "seed-design/ui/action-button";

export default function RootLayout() {
  return (
    // SEED는 모바일 우선 디자인이라 PC에서 늘어지지 않게 폭을 제한하고 가운데에 둔다
    <VStack
      maxWidth="640px"
      marginX="auto"
      paddingX="spacingX.globalGutter"
      paddingY="x4"
      gap="x4"
    >
      {/* small 버튼의 안쪽 여백(x3_5)만큼 왼쪽으로 당겨 글자를 아래 제목과 맞춘다 */}
      <HStack as="nav" gap="x1" bleedLeft="x3_5">
        <ActionButton asChild variant="ghost" size="small">
          <Link to="/">홈</Link>
        </ActionButton>
        <ActionButton asChild variant="ghost" size="small">
          <Link to="/about">소개</Link>
        </ActionButton>
      </HStack>
      <main>
        <Outlet /> {/* 여기에 주소에 맞는 자식 화면이 들어간다 */}
      </main>
    </VStack>
  );
}
