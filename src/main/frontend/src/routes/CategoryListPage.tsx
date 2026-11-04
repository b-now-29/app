import { useLoaderData } from "react-router";
import type { getCategories } from "../api/categories";

export default function CategoryListPage() {
  const categories = useLoaderData<typeof getCategories>(); // router.tsx의 loader 결과
  return (
    <>
      <h1>카테고리</h1>
      <ul>
        {categories.map((category) => (
          <li key={category.slug}>{category.name}</li>
        ))}
      </ul>
    </>
  );
}
