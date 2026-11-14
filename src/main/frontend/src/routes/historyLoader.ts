import { getCategoryStats, getRecentAttempts } from "../api/attempts";
import { rethrowWithLoginRedirect } from "./loginRedirect";

// 컴포넌트 파일에서 loader를 함께 export하면 react-refresh 린트 규칙에 걸려서 파일을 나눴다
export async function historyLoader() {
  try {
    const [categoryStats, attempts] = await Promise.all([
      getCategoryStats(),
      getRecentAttempts(),
    ]);
    return { categoryStats, attempts };
  } catch (error) {
    return rethrowWithLoginRedirect(error);
  }
}
