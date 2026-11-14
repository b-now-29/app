import { Text, VStack } from "@seed-design/react";
import { Link, useLoaderData, useSearchParams } from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import SelectField from "../components/SelectField";
import type { adminQuestionListLoader } from "./adminQuestionLoaders";
import { useAdminCategories } from "./routeLoaderData";

export default function AdminQuestionListPage() {
  const categories = useAdminCategories();
  const { category, questions } =
    useLoaderData<typeof adminQuestionListLoader>();
  const [, setSearchParams] = useSearchParams();

  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        문제 관리
      </Text>
      <SelectField
        label="카테고리"
        value={category}
        options={[
          { value: "", label: "선택" },
          ...categories.map((item) => ({ value: item.slug, label: item.name })),
        ]}
        onChange={(value) =>
          setSearchParams(value === "" ? {} : { category: value })
        }
      />
      {category !== "" && (
        <>
          <ActionButton asChild size="small">
            <Link
              to={`/admin/questions/new?category=${encodeURIComponent(category)}`}
            >
              새 문제
            </Link>
          </ActionButton>
          <ul aria-label="문제 목록">
            {questions.map((question) => (
              <li key={question.id}>
                <Link to={`/admin/questions/${question.id}`}>
                  {question.content}
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </VStack>
  );
}
