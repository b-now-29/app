import { useRouteError } from "react-router";
import { NotFoundError } from "../api/questions";
import ErrorPage from "./ErrorPage";
import NotFoundPage from "./NotFoundPage";

// 없는 카테고리는 404 화면, 그 밖의 실패는 일반 에러 화면을 보여 준다
export default function QuizErrorPage() {
  const error = useRouteError();
  return error instanceof NotFoundError ? <NotFoundPage /> : <ErrorPage />;
}
