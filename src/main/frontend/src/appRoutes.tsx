import { redirect } from "react-router";
import type { RouteObject, ShouldRevalidateFunction } from "react-router";
import AdminLayout from "./routes/AdminLayout";
import AdminCategoriesPage from "./routes/AdminCategoriesPage";
import AdminQuestionListPage from "./routes/AdminQuestionListPage";
import {
  AdminQuestionEditPage,
  AdminQuestionNewPage,
} from "./routes/AdminQuestionFormPage";
import AdminUsersPage from "./routes/AdminUsersPage";
import { adminUsersLoader } from "./routes/adminUsersLoader";
import { adminLoader } from "./routes/adminLoader";
import AdminCategoryStatsPage from "./routes/AdminCategoryStatsPage";
import AdminQuestionStatsPage from "./routes/AdminQuestionStatsPage";
import {
  adminCategoryStatsLoader,
  adminQuestionStatsLoader,
} from "./routes/adminStatsLoaders";
import {
  adminQuestionListLoader,
  adminQuestionLoader,
} from "./routes/adminQuestionLoaders";
import { getCategories } from "./api/categories";
import RootLayout from "./routes/RootLayout";
import CategoryListPage from "./routes/CategoryListPage";
import ErrorPage from "./routes/ErrorPage";
import QuizPage from "./routes/QuizPage";
import QuizErrorPage from "./routes/QuizErrorPage";
import { quizLoader } from "./routes/quizLoader";
import HistoryPage from "./routes/HistoryPage";
import { historyLoader } from "./routes/historyLoader";
import AboutPage from "./routes/AboutPage";
import NotFoundPage from "./routes/NotFoundPage";
import LoginPage from "./routes/LoginPage";
import SignUpPage from "./routes/SignUpPage";
import { rootLoader } from "./routes/rootLoader";
import { loginAction, logoutAction, signUpAction } from "./routes/authActions";

// shouldRevalidate: action이 끝난 뒤 loader를 "다시 실행할지" 정하는 함수.
// 로그아웃(루트 "/"의 action)은 퀴즈·카테고리 데이터와 무관하므로 false로 다시 받지 않는다.
// 다시 받다가 실패하면 풀던 진행 상황이 사라지기 때문이다. 그 밖에는 기본 동작을 따른다.
const skipAfterLogout: ShouldRevalidateFunction = ({
  formMethod,
  formAction,
  defaultShouldRevalidate,
}) => {
  const isLogout =
    formMethod?.toUpperCase() === "POST" &&
    formAction !== undefined &&
    new URL(formAction, "http://localhost").pathname === "/";
  return isLogout ? false : defaultShouldRevalidate;
};

export const appRoutes: RouteObject[] = [
  {
    path: "/",
    id: "root", // 자식 화면이 useRouteLoaderData("root")로 현재 사용자를 읽는다
    Component: RootLayout, // 공통 틀
    loader: rootLoader, // 현재 로그인 사용자 (비회원이면 null)
    action: logoutAction, // 메뉴의 로그아웃 버튼이 보내는 POST
    children: [
      {
        index: true, // "/" 그 자체
        Component: CategoryListPage,
        // 화면을 그리기 "전에" 서버에서 데이터를 받아온다 (Data mode의 핵심 기능)
        loader: getCategories,
        shouldRevalidate: skipAfterLogout,
        // loader가 실패하면 CategoryListPage 대신 그린다 (RootLayout의 메뉴는 그대로 남는다)
        ErrorBoundary: ErrorPage,
      },
      {
        path: "categories/:slug",
        Component: QuizPage,
        loader: quizLoader,
        shouldRevalidate: skipAfterLogout,
        ErrorBoundary: QuizErrorPage,
      },
      {
        // skipAfterLogout을 붙이지 않는다. 로그아웃 뒤 loader가 다시 실행되어 401로 /login에 간다
        path: "history",
        Component: HistoryPage,
        loader: historyLoader,
        ErrorBoundary: ErrorPage,
      },
      {
        // 관리자 화면. 로그아웃 뒤에는 loader가 다시 실행되어 401로 /login에 간다
        path: "admin",
        id: "admin", // 자식 화면이 useRouteLoaderData("admin")으로 카테고리 목록을 읽는다
        Component: AdminLayout,
        loader: adminLoader,
        ErrorBoundary: ErrorPage,
        children: [
          { index: true, loader: () => redirect("/admin/categories") },
          { path: "categories", Component: AdminCategoriesPage },
          {
            path: "questions",
            Component: AdminQuestionListPage,
            loader: adminQuestionListLoader,
          },
          {
            path: "users",
            Component: AdminUsersPage,
            loader: adminUsersLoader,
          },
          {
            path: "stats",
            Component: AdminCategoryStatsPage,
            loader: adminCategoryStatsLoader,
          },
          {
            path: "stats/:slug",
            Component: AdminQuestionStatsPage,
            loader: adminQuestionStatsLoader,
          },
          { path: "questions/new", Component: AdminQuestionNewPage },
          {
            path: "questions/:id",
            Component: AdminQuestionEditPage,
            loader: adminQuestionLoader,
          },
        ],
      },
      { path: "login", Component: LoginPage, action: loginAction },
      { path: "signup", Component: SignUpPage, action: signUpAction },
      { path: "about", Component: AboutPage },
      { path: "*", Component: NotFoundPage }, // 나머지 전부
    ],
  },
];
