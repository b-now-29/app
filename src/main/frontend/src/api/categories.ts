import { z } from "zod";
import { getJson } from "./http";

const CategorySchema = z
  .object({
    slug: z.string(),
    name: z.string(),
  })
  .readonly();
export type CategoryResponse = z.infer<typeof CategorySchema>;

const CategoryListSchema = z.array(CategorySchema).readonly();

export function getCategories(): Promise<readonly CategoryResponse[]> {
  return getJson("/api/v1/categories", CategoryListSchema);
}
