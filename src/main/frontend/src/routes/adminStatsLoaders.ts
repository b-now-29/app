import type { LoaderFunctionArgs } from "react-router";
import { getAdminCategoryStats, getAdminQuestionStats } from "../api/admin";
import { rethrowAsRedirect } from "./adminLoader";

export async function adminCategoryStatsLoader() {
  try {
    return await getAdminCategoryStats();
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}

export async function adminQuestionStatsLoader({ params }: LoaderFunctionArgs) {
  try {
    return await getAdminQuestionStats(params.slug ?? "");
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}
