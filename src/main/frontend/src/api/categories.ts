import { getJson } from "./http";

export type CategoryResponse = {
  readonly slug: string;
  readonly name: string;
};

export function getCategories(): Promise<readonly CategoryResponse[]> {
  return getJson("/api/v1/categories");
}
