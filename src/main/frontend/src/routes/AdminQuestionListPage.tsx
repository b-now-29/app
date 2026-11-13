import { Text, VStack } from "@seed-design/react";
import {
  Link,
  useLoaderData,
  useRouteLoaderData,
  useSearchParams,
} from "react-router";
import type {
  AdminCategoryResponse,
  AdminQuestionResponse,
} from "../api/admin";
import { ActionButton } from "seed-design/ui/action-button";
import SelectField from "../components/SelectField";

type LoaderData = {
  readonly category: string;
  readonly questions: readonly AdminQuestionResponse[];
};

export default function AdminQuestionListPage() {
  const categories = useRouteLoaderData(
    "admin",
  ) as readonly AdminCategoryResponse[];
  const { category, questions } = useLoaderData() as LoaderData;
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
