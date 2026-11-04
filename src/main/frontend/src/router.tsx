import { createBrowserRouter } from "react-router";
import { getCategories } from "./api/categories";
import RootLayout from "./routes/RootLayout";
import CategoryListPage from "./routes/CategoryListPage";
import ErrorPage from "./routes/ErrorPage";
import QuizPage from "./routes/QuizPage";
import QuizErrorPage from "./routes/QuizErrorPage";
import { quizLoader } from "./routes/quizLoader";
import AboutPage from "./routes/AboutPage";
import NotFoundPage from "./routes/NotFoundPage";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: RootLayout, // 공통 틀
    children: [
      {
        index: true, // "/" 그 자체
        Component: CategoryListPage,
        // 화면을 그리기 "전에" 서버에서 데이터를 받아온다 (Data mode의 핵심 기능)
        loader: getCategories,
        // loader가 실패하면 CategoryListPage 대신 그린다 (RootLayout의 메뉴는 그대로 남는다)
        ErrorBoundary: ErrorPage,
      },
      {
        path: "categories/:slug",
        Component: QuizPage,
        loader: quizLoader,
        ErrorBoundary: QuizErrorPage,
      },
      { path: "about", Component: AboutPage },
      { path: "*", Component: NotFoundPage }, // 나머지 전부
    ],
  },
]);
