import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { getCategories } from "../api/categories";
import CategoryListPage from "./CategoryListPage";
import ErrorPage from "./ErrorPage";

vi.mock("../api/categories");

function renderPage() {
  const router = createMemoryRouter([
    {
      path: "/",
      Component: CategoryListPage,
      loader: getCategories,
      ErrorBoundary: ErrorPage,
    },
  ]);
  render(<RouterProvider router={router} />);
}

describe("카테고리 목록 화면", () => {
  it("서버가 준 순서대로 카테고리 이름을 보여 준다", async () => {
    vi.mocked(getCategories).mockResolvedValue([
      { slug: "java", name: "Java" },
      { slug: "database", name: "데이터베이스" },
    ]);

    renderPage();

    const items = await screen.findAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual([
      "Java",
      "데이터베이스",
    ]);
  });

  it("목록을 불러오지 못하면 에러 화면을 보여 준다", async () => {
    vi.mocked(getCategories).mockRejectedValue(new Error("HTTP 500"));

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "문제가 발생했습니다",
    );
  });
});
