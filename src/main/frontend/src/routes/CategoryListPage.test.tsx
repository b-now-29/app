import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider, useParams } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { getCategories } from "../api/categories";
import CategoryListPage from "./CategoryListPage";
import ErrorPage from "./ErrorPage";

vi.mock("../api/categories");

// 이동한 경로의 slug가 맞는지만 보면 되므로 문제 풀이 화면 대신 그린다
function QuizRouteStub() {
  const { slug } = useParams();
  return <h1>{slug} 문제 화면</h1>;
}

function renderPage() {
  const router = createMemoryRouter([
    {
      path: "/",
      Component: CategoryListPage,
      loader: getCategories,
      ErrorBoundary: ErrorPage,
    },
    { path: "/categories/:slug", Component: QuizRouteStub },
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

  it("각 카테고리 링크는 그 카테고리의 풀이 주소를 가리킨다 (R1)", async () => {
    vi.mocked(getCategories).mockResolvedValue([
      { slug: "java", name: "Java" },
      { slug: "database", name: "데이터베이스" },
    ]);

    renderPage();

    const javaLink = await screen.findByRole("link", { name: "Java" });
    const databaseLink = screen.getByRole("link", { name: "데이터베이스" });
    expect(javaLink).toHaveAttribute("href", "/categories/java");
    expect(databaseLink).toHaveAttribute("href", "/categories/database");
  });

  it("카테고리를 누르면 페이지를 새로 불러오지 않고 라우터로 이동한다 (R1)", async () => {
    vi.mocked(getCategories).mockResolvedValue([
      { slug: "java", name: "Java" },
      { slug: "database", name: "데이터베이스" },
    ]);
    const user = userEvent.setup();

    renderPage();
    await user.click(await screen.findByRole("link", { name: "데이터베이스" }));

    expect(
      await screen.findByRole("heading", { name: "database 문제 화면" }),
    ).toBeInTheDocument();
  });
});
