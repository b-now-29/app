import { createBrowserRouter } from "react-router";
import RootLayout from "./routes/RootLayout";
import HomePage from "./routes/HomePage";
import AboutPage from "./routes/AboutPage";
import NotFoundPage from "./routes/NotFoundPage";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: RootLayout, // 공통 틀
    children: [
      {
        index: true, // "/" 그 자체
        Component: HomePage,
        // 화면을 그리기 "전에" 서버에서 데이터를 받아온다 (Data mode의 핵심 기능)
        loader: async () => {
          const res = await fetch("/api/v1/ping");
          return (await res.json()) as { status: string };
        },
      },
      { path: "about", Component: AboutPage },
      { path: "*", Component: NotFoundPage }, // 나머지 전부
    ],
  },
]);
