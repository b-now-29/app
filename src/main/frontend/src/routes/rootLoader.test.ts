import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/http";
import { getCurrentUser } from "../api/users";
import { rootLoader } from "./rootLoader";

vi.mock("../api/users", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/users")>()),
  getCurrentUser: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(getCurrentUser).mockReset();
});

describe("rootLoader", () => {
  it("로그인 상태면 사용자를 돌려준다", async () => {
    const user = {
      id: 1,
      email: "user@example.com",
      role: "USER",
      nickname: "철수",
      tag: 1,
    } as const;
    vi.mocked(getCurrentUser).mockResolvedValue(user);

    await expect(rootLoader()).resolves.toEqual(user);
  });

  it("비회원이면 null을 돌려준다", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(null);

    await expect(rootLoader()).resolves.toBeNull();
  });

  it.each([
    ["서버 오류", new ApiError(500, "서버 오류")],
    ["네트워크 오류", new TypeError("Failed to fetch")],
  ])(
    "%s가 나도 던지지 않고 비회원으로 처리한다 (D11)",
    async (_name, error) => {
      vi.mocked(getCurrentUser).mockRejectedValue(error);

      await expect(rootLoader()).resolves.toBeNull();
    },
  );
});
