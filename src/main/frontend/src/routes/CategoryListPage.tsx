import { Box, Text, VStack } from "@seed-design/react";
import { useLoaderData } from "react-router";
import { List, ListItem } from "seed-design/ui/list";
import type { getCategories } from "../api/categories";

export default function CategoryListPage() {
  const categories = useLoaderData<typeof getCategories>(); // router.tsx의 loader 결과
  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        카테고리
      </Text>
      {/* ListItem에 자체 좌우 여백이 있어서, 그만큼 밖으로 당겨 글자를 제목과 맞춘다 */}
      <Box bleedX="spacingX.globalGutter">
        <List>
          {categories.map((category) => (
            <ListItem key={category.slug} title={category.name} />
          ))}
        </List>
      </Box>
    </VStack>
  );
}
