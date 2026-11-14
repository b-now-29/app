import { HStack, Text, VStack } from "@seed-design/react";
import { useEffect } from "react";
import {
  Link,
  Outlet,
  useFetcher,
  useLocation,
  useRouteLoaderData,
} from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import type { UserResponse } from "../api/users";
import NicknameText from "../components/NicknameText";
import type { ActionError } from "./actionError";

export default function RootLayout() {
  const user = useRouteLoaderData("root") as UserResponse | null;
  // fetcher: 화면 이동 없이 action(로그아웃)만 호출한다. 성공하면 loader가 다시 실행되어 메뉴가 바뀐다.
  const logoutFetcher = useFetcher<ActionError | null>();
  const { pathname } = useLocation();
  const { reset } = logoutFetcher;
  // 다른 페이지로 이동(주소가 바뀜)하면 fetcher.reset()으로 지난 결과를 지워 에러도 함께 사라진다.
  useEffect(() => {
    reset();
  }, [pathname, reset]);
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
        {user ? (
          <>
            <ActionButton asChild variant="ghost" size="small">
              <Link to="/history">내 기록</Link>
            </ActionButton>
            <ActionButton asChild variant="ghost" size="small">
              <Link to="/wrong-answers">오답 노트</Link>
            </ActionButton>
            {user.role === "ADMIN" && (
              <ActionButton asChild variant="ghost" size="small">
                <Link to="/admin">관리</Link>
              </ActionButton>
            )}
            <ActionButton asChild variant="ghost" size="small">
              <Link to="/me">
                <NicknameText member={user} />
              </Link>
            </ActionButton>
            <logoutFetcher.Form method="post">
              <ActionButton type="submit" variant="ghost" size="small">
                로그아웃
              </ActionButton>
            </logoutFetcher.Form>
          </>
        ) : (
          <ActionButton asChild variant="ghost" size="small">
            <Link to="/login">로그인</Link>
          </ActionButton>
        )}
      </HStack>
      {logoutFetcher.data?.error && (
        <Text role="alert" color="fg.critical">
          {logoutFetcher.data.error}
        </Text>
      )}
      <main>
        <Outlet /> {/* 여기에 주소에 맞는 자식 화면이 들어간다 */}
      </main>
    </VStack>
  );
}
