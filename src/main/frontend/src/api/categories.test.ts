import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategories } from "./categories";
import type { CategoryResponse } from "./categories";
import { ApiError } from "./http";

const fetchMock = vi.spyOn(globalThis, "fetch");

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

async function caught(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

beforeEach(() => {
  fetchMock.mockReset();
});

describe("getCategories", () => {
  it("카테고리 목록을 조회해 응답 본문을 그대로 돌려준다", async () => {
    const categories: readonly CategoryResponse[] = [
      { slug: "java", name: "Java" },
      { slug: "spring", name: "Spring" },
    ];
    fetchMock.mockResolvedValue(jsonResponse(categories));

    await expect(getCategories()).resolves.toEqual(categories);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/categories");
  });

  it("실패 응답은 상태와 서버 메시지를 담은 ApiError다", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ title: "실패", status: 500, detail: "서버 오류" }, 500),
    );

    const error = await caught(getCategories());

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 500, message: "서버 오류" });
  });
});
