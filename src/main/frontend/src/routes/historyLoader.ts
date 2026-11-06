import { redirect } from "react-router";
import { getCategoryStats, getRecentAttempts } from "../api/attempts";
import { ApiError } from "../api/users";

// 컴포넌트 파일에서 loader를 함께 export하면 react-refresh 린트 규칙에 걸려서 파일을 나눴다
export async function historyLoader() {
  try {
    const [categoryStats, attempts] = await Promise.all([
      getCategoryStats(),
      getRecentAttempts(),
    ]);
    return { categoryStats, attempts };
  } catch (error) {
    // 비회원이면 로그인 화면으로 보낸다. 그 밖의 실패는 에러 화면이 그리도록 다시 던진다.
    if (error instanceof ApiError && error.status === 401) {
      throw redirect("/login");
    }
    throw error;
  }
}
