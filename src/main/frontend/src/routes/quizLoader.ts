import type { LoaderFunctionArgs } from "react-router";
import { getQuestions } from "../api/questions";

// 컴포넌트 파일에서 loader를 함께 export하면 react-refresh 린트 규칙에 걸려서 파일을 나눴다
export function quizLoader({ params }: LoaderFunctionArgs) {
  const { slug } = params;
  if (slug === undefined) {
    throw new Error("quizLoader는 :slug가 있는 경로에서만 쓸 수 있습니다");
  }
  return getQuestions(slug);
}
