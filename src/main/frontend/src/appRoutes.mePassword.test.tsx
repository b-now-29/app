// 내 정보 화면 /me 의 비밀번호 변경 폼 (docs/features/password-change.md R8, R9, 화면 흐름).
// appRoutes 전체로 그려서 route action(meAction)의 intent 구분까지 함께 확인한다.
//
// 화면 계약:
// - 닉네임 폼 아래에 제목 "비밀번호 변경"과 입력칸 label "현재 비밀번호", "새 비밀번호", "새 비밀번호 확인", 버튼 "변경"
// - 비밀번호 칸은 type="password", autoComplete는 current-password / new-password
// - 새 비밀번호와 확인이 다르면 서버에 보내지 않고 role="alert"에 "새 비밀번호가 서로 다릅니다." (D10)
// - 성공: 세 칸 비움 + "비밀번호를 바꿨습니다. 다른 기기에서는 다시 로그인해야 합니다."
// - 실패: role="alert"에 서버 문구 (그 밖의 오류는 기본 문구), 입력 유지. 불일치 문구와 동시에 나오지 않는다 (D10)
// - 로그인 만료(401): 닉네임 폼과 같이 /login으로 간다 (D16)
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { UserEvent } from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategories } from "./api/categories";
import { ApiError } from "./api/http";
import {
  changeNickname,
  changePassword,
  getCurrentUser,
  logout,
} from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/categories");
vi.mock("./api/users");

const ME: UserResponse = {
  id: 1,
  email: "user@example.com",
  role: "USER",
  nickname: "철수",
  tag: 3,
};
const CURRENT = "old-password";
const NEW = "new-password-123";
const MISMATCH = "새 비밀번호가 서로 다릅니다.";
const CHANGED =
  "비밀번호를 바꿨습니다. 다른 기기에서는 다시 로그인해야 합니다.";
const WRONG_CURRENT = "현재 비밀번호가 맞지 않습니다.";

beforeEach(() => {
  vi.mocked(getCategories).mockReset();
  vi.mocked(getCategories).mockResolvedValue([
    { slug: "java", name: "홈 카테고리" },
  ]);
  vi.mocked(getCurrentUser).mockReset();
  vi.mocked(getCurrentUser).mockResolvedValue(ME);
  vi.mocked(changeNickname).mockReset();
  vi.mocked(changeNickname).mockResolvedValue(ME);
  vi.mocked(changePassword).mockReset();
  vi.mocked(changePassword).mockResolvedValue(undefined);
  vi.mocked(logout).mockReset();
  vi.mocked(logout).mockResolvedValue(undefined);
});

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

async function findMeScreen() {
  expect(
    await screen.findByRole("heading", { name: "내 정보", level: 1 }),
  ).toBeInTheDocument();
  return within(screen.getByRole("main"));
}

function passwordInputs() {
  return {
    current: screen.getByLabelText("현재 비밀번호"),
    next: screen.getByLabelText("새 비밀번호"),
    confirm: screen.getByLabelText("새 비밀번호 확인"),
  };
}

async function submitPassword(
  user: UserEvent,
  current: string,
  next: string,
  confirm: string,
) {
  const inputs = passwordInputs();
  await user.type(inputs.current, current);
  await user.type(inputs.next, next);
  await user.type(inputs.confirm, confirm);
  await user.click(screen.getByRole("button", { name: "변경" }));
}

describe("비밀번호 변경 폼 (password-change R8, 화면 흐름)", () => {
  it("내 정보 화면에 제목, 세 입력칸, 변경 버튼이 있고 닉네임 폼도 그대로 있다", async () => {
    renderApp("/me");

    const main = await findMeScreen();
    expect(
      main.getByRole("heading", { name: "비밀번호 변경" }),
    ).toBeInTheDocument();
    expect(main.getByLabelText("현재 비밀번호")).toHaveValue("");
    expect(main.getByLabelText("새 비밀번호")).toHaveValue("");
    expect(main.getByLabelText("새 비밀번호 확인")).toHaveValue("");
    expect(main.getByRole("button", { name: "변경" })).toBeInTheDocument();
    expect(main.getByLabelText("닉네임")).toBeInTheDocument();
    expect(main.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });

  it("비밀번호 칸은 가려지고, 브라우저 자동 완성이 현재·새 비밀번호를 구분한다", async () => {
    renderApp("/me");
    await findMeScreen();

    const { current, next, confirm } = passwordInputs();
    expect(current).toHaveAttribute("type", "password");
    expect(current).toHaveAttribute("autocomplete", "current-password");
    expect(next).toHaveAttribute("type", "password");
    expect(next).toHaveAttribute("autocomplete", "new-password");
    expect(confirm).toHaveAttribute("type", "password");
    expect(confirm).toHaveAttribute("autocomplete", "new-password");
  });

  it("새 비밀번호와 확인이 다르면 서버에 보내지 않고 문구를 보여 주며 입력은 그대로다 (R8)", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await submitPassword(user, CURRENT, NEW, "new-password-124");

    // 스크린리더가 읽도록 alert 한 줄로 보여 준다 (D10)
    const alerts = await screen.findAllByRole("alert");
    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toHaveTextContent(MISMATCH);
    expect(changePassword).not.toHaveBeenCalled();
    expect(changeNickname).not.toHaveBeenCalled();
    const { current, next, confirm } = passwordInputs();
    expect(current).toHaveValue(CURRENT);
    expect(next).toHaveValue(NEW);
    expect(confirm).toHaveValue("new-password-124");
  });

  it("확인을 고쳐 다시 변경하면 서버에 보내고 불일치 문구가 사라진다 (R8)", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();
    await submitPassword(user, CURRENT, NEW, "new-password-124");
    await screen.findByText(MISMATCH);

    const { confirm } = passwordInputs();
    await user.clear(confirm);
    await user.type(confirm, NEW);
    await user.click(screen.getByRole("button", { name: "변경" }));

    await waitFor(() => {
      expect(changePassword).toHaveBeenCalledWith(CURRENT, NEW);
    });
    await waitFor(() => {
      expect(screen.queryByText(MISMATCH)).not.toBeInTheDocument();
    });
  });
});

describe("비밀번호 변경 결과 (password-change R9)", () => {
  it("성공하면 현재·새 비밀번호로 요청하고, 세 칸을 비우고 안내 문구를 보여 준다", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await submitPassword(user, CURRENT, NEW, NEW);

    expect(await screen.findByText(CHANGED)).toBeInTheDocument();
    expect(changePassword).toHaveBeenCalledTimes(1);
    expect(changePassword).toHaveBeenCalledWith(CURRENT, NEW);
    await waitFor(() => {
      const { current, next, confirm } = passwordInputs();
      expect(current).toHaveValue("");
      expect(next).toHaveValue("");
      expect(confirm).toHaveValue("");
    });
    expect(screen.queryByText(MISMATCH)).not.toBeInTheDocument();
  });

  it("비밀번호 폼으로 바꾸면 닉네임은 바꾸지 않는다 (intent 구분)", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await submitPassword(user, CURRENT, NEW, NEW);

    await screen.findByText(CHANGED);
    expect(changeNickname).not.toHaveBeenCalled();
  });

  it("닉네임 폼으로 저장하면 비밀번호는 바꾸지 않고 비밀번호 안내 문구도 나오지 않는다 (intent 구분)", async () => {
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await user.type(screen.getByLabelText("닉네임"), "영희");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(changeNickname).toHaveBeenCalledWith("영희");
    });
    expect(changePassword).not.toHaveBeenCalled();
    expect(screen.queryByText(CHANGED)).not.toBeInTheDocument();
  });

  it.each([
    [400, WRONG_CURRENT],
    [400, "입력값이 올바르지 않습니다. (항목: newPassword)"],
    [429, "시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요."],
  ])(
    "%i로 실패하면 서버 문구를 보여 주고 세 칸의 입력은 그대로다",
    async (status, detail) => {
      vi.mocked(changePassword).mockRejectedValue(new ApiError(status, detail));
      const user = userEvent.setup();
      renderApp("/me");
      await findMeScreen();

      await submitPassword(user, CURRENT, NEW, NEW);

      const alerts = await screen.findAllByRole("alert");
      expect(alerts).toHaveLength(1);
      expect(alerts[0]).toHaveTextContent(detail);
      const { current, next, confirm } = passwordInputs();
      expect(current).toHaveValue(CURRENT);
      expect(next).toHaveValue(NEW);
      expect(confirm).toHaveValue(NEW);
      expect(screen.queryByText(CHANGED)).not.toBeInTheDocument();
    },
  );

  it("현재 비밀번호가 틀려(400) 실패해도 로그인 화면으로 가지 않고 내 정보 화면에 남는다 (D1)", async () => {
    vi.mocked(changePassword).mockRejectedValue(
      new ApiError(400, WRONG_CURRENT),
    );
    const user = userEvent.setup();
    const router = renderApp("/me");
    await findMeScreen();

    await submitPassword(user, "wrong-password", NEW, NEW);

    expect(await screen.findByRole("alert")).toHaveTextContent(WRONG_CURRENT);
    expect(router.state.location.pathname).toBe("/me");
    expect(
      screen.getByRole("heading", { name: "내 정보", level: 1 }),
    ).toBeInTheDocument();
  });

  it("네트워크 오류여도 에러 화면이 아니라 폼에 오류를 보여 주고 입력을 유지한다", async () => {
    vi.mocked(changePassword).mockRejectedValue(
      new TypeError("Failed to fetch"),
    );
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await submitPassword(user, CURRENT, NEW, NEW);

    expect(await screen.findByRole("alert")).not.toBeEmptyDOMElement();
    expect(passwordInputs().current).toHaveValue(CURRENT);
    expect(
      screen.getByRole("heading", { name: "내 정보", level: 1 }),
    ).toBeInTheDocument();
  });

  it("서버 실패 뒤 확인을 다르게 고쳐 다시 변경하면 불일치 문구만 보이고 서버 문구는 사라진다 (D10)", async () => {
    vi.mocked(changePassword).mockRejectedValueOnce(
      new ApiError(400, WRONG_CURRENT),
    );
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await submitPassword(user, "wrong-password", NEW, NEW);
    expect(await screen.findByRole("alert")).toHaveTextContent(WRONG_CURRENT);
    const { confirm } = passwordInputs();
    await user.clear(confirm);
    await user.type(confirm, "new-password-124");
    await user.click(screen.getByRole("button", { name: "변경" }));

    await waitFor(() => {
      expect(screen.queryByText(WRONG_CURRENT)).not.toBeInTheDocument();
    });
    const alerts = screen.getAllByRole("alert");
    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toHaveTextContent(MISMATCH);
    expect(changePassword).toHaveBeenCalledTimes(1);
  });

  it("로그인이 만료된 상태(401)에서 보내면 닉네임 폼과 같이 로그인 화면으로 간다 (D16)", async () => {
    // 서버 상태: 토큰이 만료되어 비밀번호 변경도, 그 뒤 현재 사용자 조회도 401이다
    let loggedIn = true;
    vi.mocked(getCurrentUser).mockImplementation(() =>
      Promise.resolve(loggedIn ? ME : null),
    );
    vi.mocked(changePassword).mockImplementation(() => {
      loggedIn = false;
      return Promise.reject(new ApiError(401, "로그인이 필요합니다."));
    });
    const user = userEvent.setup();
    const router = renderApp("/me");
    await findMeScreen();

    await submitPassword(user, CURRENT, NEW, NEW);

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/login");
    });
    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "내 정보", level: 1 }),
    ).not.toBeInTheDocument();
  });

  it("실패한 뒤 다시 변경해 성공하면 오류 문구가 사라지고 안내 문구가 나온다", async () => {
    vi.mocked(changePassword).mockRejectedValueOnce(
      new ApiError(400, WRONG_CURRENT),
    );
    const user = userEvent.setup();
    renderApp("/me");
    await findMeScreen();

    await submitPassword(user, "wrong-password", NEW, NEW);
    await screen.findByRole("alert");
    const { current } = passwordInputs();
    await user.clear(current);
    await user.type(current, CURRENT);
    await user.click(screen.getByRole("button", { name: "변경" }));

    expect(await screen.findByText(CHANGED)).toBeInTheDocument();
    expect(changePassword).toHaveBeenLastCalledWith(CURRENT, NEW);
    await waitFor(() => {
      expect(screen.queryByText(WRONG_CURRENT)).not.toBeInTheDocument();
    });
  });
});
