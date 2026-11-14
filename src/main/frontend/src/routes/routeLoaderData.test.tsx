import { render, screen } from "@testing-library/react";
import {
  createMemoryRouter,
  Outlet,
  RouterProvider,
  useRouteError,
} from "react-router";
import type { RouteObject } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminCategoryResponse } from "../api/admin";
import type { UserResponse } from "../api/users";
import { useAdminCategories, useCurrentUser } from "./routeLoaderData";

const MEMBER: UserResponse = {
  id: 1,
  email: "user@example.com",
  role: "USER",
  nickname: "철수",
  tag: 7,
};

const CATEGORIES: readonly AdminCategoryResponse[] = [
  {
    slug: "java",
    name: "자바",
    description: null,
    sortOrder: 1,
    questionCount: 3,
  },
  {
    slug: "spring",
    name: "스프링",
    description: "스프링 문제",
    sortOrder: 2,
    questionCount: 5,
  },
];

const GUEST_TEXT = "비회원";

function CurrentUserProbe() {
  const user = useCurrentUser();
  return (
    <p>{user === null ? GUEST_TEXT : `회원 ${user.nickname}#${user.tag}`}</p>
  );
}

function AdminCategoriesProbe() {
  const categories = useAdminCategories();
  return (
    <ul>
      {categories.map((category) => (
        <li key={category.slug}>{category.name}</li>
      ))}
    </ul>
  );
}

function ErrorMessage() {
  const error = useRouteError();
  return (
    <p role="alert">
      {error instanceof Error ? `Error: ${error.message}` : "Error 아님"}
    </p>
  );
}

function renderRoutes(routes: RouteObject[]) {
  const router = createMemoryRouter(routes, { initialEntries: ["/probe"] });
  render(<RouterProvider router={router} />);
}

// 데이터가 없을 때 hook이 던진 Error는 React가 콘솔에 남기므로, 테스트 출력이 지저분해지지 않게 막는다
beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.spyOn(console, "warn").mockImplementation(() => {});
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useCurrentUser", () => {
  function renderUnderRoot(currentUser: UserResponse | null) {
    renderRoutes([
      {
        path: "/",
        id: "root",
        loader: () => currentUser,
        Component: Outlet,
        children: [
          {
            path: "probe",
            Component: CurrentUserProbe,
            ErrorBoundary: ErrorMessage,
          },
        ],
      },
    ]);
  }

  it("root route 아래에서 회원이면 root loader의 회원 정보를 돌려준다", async () => {
    renderUnderRoot(MEMBER);

    expect(await screen.findByText("회원 철수#7")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("root route 아래에서 비회원이면 null을 돌려준다", async () => {
    renderUnderRoot(null);

    expect(await screen.findByText(GUEST_TEXT)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("root route 밖에서는 route id를 담은 Error를 던진다", async () => {
    renderRoutes([
      {
        path: "/",
        id: "other",
        loader: () => MEMBER,
        Component: Outlet,
        children: [
          {
            path: "probe",
            Component: CurrentUserProbe,
            ErrorBoundary: ErrorMessage,
          },
        ],
      },
    ]);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/^Error: .*root/);
    expect(screen.queryByText(GUEST_TEXT)).not.toBeInTheDocument();
  });
});

describe("useAdminCategories", () => {
  it("admin route 아래에서는 admin loader의 카테고리 목록을 돌려준다", async () => {
    renderRoutes([
      {
        path: "/",
        id: "admin",
        loader: () => CATEGORIES,
        Component: Outlet,
        children: [
          {
            path: "probe",
            Component: AdminCategoriesProbe,
            ErrorBoundary: ErrorMessage,
          },
        ],
      },
    ]);

    const items = await screen.findAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual(["자바", "스프링"]);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("admin route 밖에서는 route id를 담은 Error를 던진다", async () => {
    // root route 아래이지만 admin route 아래는 아닌 화면
    renderRoutes([
      {
        path: "/",
        id: "root",
        loader: () => MEMBER,
        Component: Outlet,
        children: [
          {
            path: "probe",
            Component: AdminCategoriesProbe,
            ErrorBoundary: ErrorMessage,
          },
        ],
      },
    ]);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/^Error: .*admin/);
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
  });
});
