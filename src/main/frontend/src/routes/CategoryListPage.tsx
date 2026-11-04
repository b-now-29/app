import { Box, Text, VStack } from "@seed-design/react";
import { useLinkClickHandler, useLoaderData } from "react-router";
import { List, ListLinkItem } from "seed-design/ui/list";
import type { CategoryResponse, getCategories } from "../api/categories";

export default function CategoryListPage() {
  const categories = useLoaderData<typeof getCategories>(); // router.tsx의 loader 결과
  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        카테고리
      </Text>
      {/* ListLinkItem에 자체 좌우 여백이 있어서, 그만큼 밖으로 당겨 글자를 제목과 맞춘다 */}
      <Box bleedX="spacingX.globalGutter">
        <List>
          {categories.map((category) => (
            <CategoryLinkItem key={category.slug} category={category} />
          ))}
        </List>
      </Box>
    </VStack>
  );
}

function CategoryLinkItem({
  category,
}: {
  readonly category: CategoryResponse;
}) {
  const to = `/categories/${encodeURIComponent(category.slug)}`;
  // SEED 항목은 일반 <a>를 그리므로, 그대로 두면 누를 때 페이지 전체를 새로 불러온다.
  // 이 핸들러가 클릭을 가로채 React Router 이동으로 바꾼다 (새 탭 열기 같은 동작은 그대로 둔다).
  const handleClick = useLinkClickHandler(to);
  return <ListLinkItem href={to} onClick={handleClick} title={category.name} />;
}
