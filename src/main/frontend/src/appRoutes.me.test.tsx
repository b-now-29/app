// 내 정보 화면 /me (docs/features/nickname.md R5, R9, R10, D8, 화면 흐름).
// appRoutes 전체로 그려서 route 구성, root loader 갱신까지 함께 확인한다.
//
// 구현할 것:
// - src/api/users.ts: changeNickname(nickname: string): Promise<UserResponse> (users.test.ts 참고)
// - src/appRoutes.tsx: 루트 자식에 { path: "me", ... } — loader, 화면, 닉네임 저장 처리 (action 또는 fetcher)
//   skipAfterLogout을 붙이지 않아 로그아웃 뒤 loader가 다시 실행되어 /login으로 간다 (/history와 같다)
// - 비회원(root loader의 사용자가 null)이면 /login으로 보낸다
//   (코드 리뷰) 사용자는 root loader 데이터 (useRouteLoaderData("root") 등)에서 읽고 getCurrentUser를 따로 부르지 않는다.
//   비회원이면 /login으로 보내는 동작은 그대로다
// - (화면 확인, D14) 닉네임 입력칸은 처음부터 비어 있고 (현재 닉네임은 "닉네임#번호" 텍스트로 보인다),
//   저장에 성공하면 다시 비운다. 실패하면 입력한 값을 그대로 둔다
//
// 화면 계약 (frontend-dev가 맞출 이름·라벨):
// - h1 "내 정보". <main> 안에 이메일 (읽기 전용: 텍스트 또는 readOnly/disabled 입력칸)과 "닉네임#번호" 텍스트
// - 입력칸 label "닉네임", 버튼 "저장"
// - 저장 성공: 메뉴의 닉네임#번호 링크와 화면의 닉네임#번호가 새 값으로 바뀐다 (root loader 재실행)
// - 저장 실패: role="alert"에 ApiError의 message (그 밖의 오류는 기본 문구), 입력한 값은 그대로, 메뉴는 그대로
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { UserEvent } from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategories } from "./api/categories";
import { ApiError, changeNickname, getCurrentUser, logout } from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/categories");

// 모듈 전체를 자동으로 대체하면 ApiError도 가짜가 되어 status와 message가 비게 된다
vi.mock("./api/users", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/users")>()),
  getCurrentUser: vi.fn(),
  changeNickname: vi.fn(),
  logout: vi.fn(),
}));

const EMAIL = "user@example.com";
const ME: UserResponse = {
  id: 1,
  email: EMAIL,
  role: "USER",
  nickname: "철수",
  tag: 3,
};
const HOME_CATEGORY = "홈 카테고리";

// 서버 상태를 흉내 낸다. 저장 뒤 root loader가 다시 실행되어야만 메뉴에 반영된다
let currentUser: UserResponse | null;

beforeEach(() => {
  currentUser = ME;
  vi.mocked(getCategories).mockReset();
  vi.mocked(getCategories).mockResolvedValue([
    { slug: "java", name: HOME_CATEGORY },
  ]);
  vi.mocked(getCurrentUser).mockReset();
  vi.mocked(getCurrentUser).mockImplementation(() =>
    Promise.resolve(currentUser),
  );
  vi.mocked(changeNickname).mockReset();
  vi.mocked(changeNickname).mockImplementation((nickname) => {
    if (currentUser === null) {
      return Promise.reject(new ApiError(401, "로그인이 필요합니다."));
    }
    // 서버처럼 앞뒤 공백을 지우고, 다른 닉네임이면 번호 1을 준다
    const trimmed = nickname.trim();
    const updated: UserResponse =
      trimmed === currentUser.nickname
        ? currentUser
        : { ...currentUser, nickname: trimmed, tag: 1 };
    currentUser = updated;
    return Promise.resolve(updated);
  });
  vi.mocked(logout).mockReset();
  vi.mocked(logout).mockImplementation(() => {
    currentUser = null;
    return Promise.resolve();
  });
});

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

async function findMenu() {
  return within(await screen.findByRole("navigation"));
}

async function findMeScreen() {
  expect(
    await screen.findByRole("heading", { name: "내 정보", level: 1 }),
  ).toBeInTheDocument();
  return within(screen.getByRole("main"));
}

function expectReadOnlyEmail(main: ReturnType<typeof within>) {
  const asText = main.queryByText(EMAIL);
  if (asText !== null) {
    return;
  }
  const input = main.getByDisplayValue(EMAIL);
  expect(input.hasAttribute("readonly") || input.hasAttribute("disabled")).toBe(
    true,
  );
}

async function saveNickname(user: UserEvent, nickname: string) {
  const input = screen.getByLabelText("닉네임");
  await user.clear(input);
  await user.type(input, nickname);
  await user.click(screen.getByRole("button", { name: "저장" }));
}

describe("내 정보 화면 (nickname R10, D8)", () => {
  it("이메일(읽기 전용), 닉네임#번호, 닉네임 입력칸과 저장 버튼을 보여 준다", async () => {
    renderApp("/me");

    const main = await findMeScreen();
    expectReadOnlyEmail(main);
    expect(main.getByText("철수#3")).toBeInTheDocument();
    expect(main.getByLabelText("닉네임")).toBeInTheDocument();
    expect(main.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });

  // (화면 확인, D14) 현재 닉네임을 미리 채우지 않는다
  it("처음 들어가면 닉네임 입력칸이 비어 있다", async () => {
    renderApp("/me");

    const main = await findMeScreen();
    expect(main.getByText("철수#3")).toBeInTheDocument();
    expect(main.getByLabelText("닉네임")).toHaveValue("");
  });

  // (코드 리뷰) /me는 root loader가 받은 사용자를 쓰고 같은 조회를 또 하지 않는다
  it("root loader의 사용자 데이터를 써서 현재 사용자를 한 번만 조회한다", async () => {
    renderApp("/me");

    const main = await findMeScreen();
    expect(main.getByText("철수#3")).toBeInTheDocument();
    expect(getCurrentUser).toHaveBeenCalledTimes(1);
  });

  it("비회원이 /me에 오면 로그인 화면으로 보낸다 (R10)", async () => {
    currentUser = null;
    const router = renderApp("/me");

    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(
      screen.queryByRole("heading", { name: "내 정보", level: 1 }),
    ).not.toBeInTheDocument();
  });

  it("내 정보 화면에서 로그아웃하면 로그인 화면으로 간다", async () => {
    const user = userEvent.setup();
    const router = renderApp("/me");
    await findMeScreen();

    const menu = await findMenu();
    await user.click(menu.getByRole("button", { name: "로그아웃" }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/login");
    });
    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
  });
});

describe("닉네임 저장 (nickname R5, R10)", () => {
  it("저장하면 새 닉네임으로 요청하고 메뉴와 화면의 닉네임#번호가 바뀐다", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await saveNickname(user, "영희");

    await waitFor(() => {
      expect(changeNickname).toHaveBeenCalledWith("영희");
    });
    const menu = await findMenu();
    expect(await menu.findByRole("link", { name: "영희#1" })).toHaveAttribute(
      "href",
      "/me",
    );
    expect(
      menu.queryByRole("link", { name: "철수#3" }),
    ).not.toBeInTheDocument();
    const main = within(screen.getByRole("main"));
    expect(await main.findByText("영희#1")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("저장 뒤 메뉴를 바꾸려고 현재 사용자를 다시 조회하는 것은 한 번뿐이다 (root loader 재실행)", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await saveNickname(user, "영희");

    const menu = await findMenu();
    expect(
      await menu.findByRole("link", { name: "영희#1" }),
    ).toBeInTheDocument();
    expect(getCurrentUser).toHaveBeenCalledTimes(2);
  });

  // (화면 확인, D14) 저장에 성공하면 입력칸을 다시 비운다. 닉네임#번호는 새 값으로 바뀐다
  it.each([
    ["다른 닉네임", "  영희 ", "영희#1"],
    ["같은 닉네임", "  철수 ", "철수#3"],
  ])(
    "%s을 앞뒤 공백과 함께 저장하면 닉네임#번호가 바뀌고 입력칸이 비워진다",
    async (_case, typed, display) => {
      const user = userEvent.setup();
      renderApp("/me");
      await findMeScreen();

      await saveNickname(user, typed);

      await waitFor(() => {
        expect(changeNickname).toHaveBeenCalledTimes(1);
      });
      const menu = await findMenu();
      expect(
        await menu.findByRole("link", { name: display }),
      ).toBeInTheDocument();
      const main = within(screen.getByRole("main"));
      expect(await main.findByText(display)).toBeInTheDocument();
      await waitFor(() => {
        expect(screen.getByLabelText("닉네임")).toHaveValue("");
      });
    },
  );

  it("같은 닉네임으로 저장하면 번호가 그대로다 (R5)", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await saveNickname(user, "철수");

    await waitFor(() => {
      expect(changeNickname).toHaveBeenCalledWith("철수");
    });
    const menu = await findMenu();
    expect(
      await menu.findByRole("link", { name: "철수#3" }),
    ).toBeInTheDocument();
  });

  it.each([
    [400, "입력값이 올바르지 않습니다. (항목: nickname)"],
    [401, "로그인이 필요합니다."],
  ])(
    "%i로 실패하면 서버 메시지를 보여 주고 입력한 값과 메뉴는 그대로다",
    async (status, detail) => {
      vi.mocked(changeNickname).mockRejectedValue(new ApiError(status, detail));
      const user = userEvent.setup();
      renderApp("/me");
      await findMeScreen();

      await saveNickname(user, "영희#1");

      expect(await screen.findByRole("alert")).toHaveTextContent(detail);
      expect(screen.getByLabelText("닉네임")).toHaveValue("영희#1");
      const menu = await findMenu();
      expect(menu.getByRole("link", { name: "철수#3" })).toBeInTheDocument();
    },
  );

  it("네트워크 오류여도 에러 화면이 아니라 폼에 오류를 보여 주고 입력을 유지한다", async () => {
    vi.mocked(changeNickname).mockRejectedValue(
      new TypeError("Failed to fetch"),
    );
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await saveNickname(user, "영희");

    expect(await screen.findByRole("alert")).not.toBeEmptyDOMElement();
    expect(screen.getByLabelText("닉네임")).toHaveValue("영희");
    expect(
      screen.getByRole("heading", { name: "내 정보", level: 1 }),
    ).toBeInTheDocument();
  });

  it("실패한 뒤 다시 저장해 성공하면 오류 문구가 사라진다", async () => {
    vi.mocked(changeNickname).mockRejectedValueOnce(
      new ApiError(400, "입력값이 올바르지 않습니다. (항목: nickname)"),
    );
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await saveNickname(user, "영희#1");
    await screen.findByRole("alert");
    await saveNickname(user, "영희");

    const menu = await findMenu();
    expect(
      await menu.findByRole("link", { name: "영희#1" }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
  });
});
