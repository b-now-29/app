import { Text, VStack } from "@seed-design/react";
import { Link, useLoaderData } from "react-router";
import type { AdminCategoryStatResponse } from "../api/admin";
import { categoryTypeStatLine } from "../quiz/adminStats";

export default function AdminCategoryStatsPage() {
  const categories = useLoaderData() as readonly AdminCategoryStatResponse[];

  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        통계
      </Text>
      <ul aria-label="카테고리 통계">
        {categories.map((category) => (
          <li key={category.slug}>
            <VStack gap="x1">
              <Link to={`/admin/stats/${encodeURIComponent(category.slug)}`}>
                <Text textStyle="t4Bold">{category.name}</Text>
              </Link>
              {category.types.length === 0 ? (
                <Text textStyle="t3Regular">아직 풀이 기록이 없습니다.</Text>
              ) : (
                category.types.map((stat) => (
                  <Text key={stat.type} textStyle="t3Regular">
                    {categoryTypeStatLine(stat)}
                  </Text>
                ))
              )}
            </VStack>
          </li>
        ))}
      </ul>
    </VStack>
  );
}
