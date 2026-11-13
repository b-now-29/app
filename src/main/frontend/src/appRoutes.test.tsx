import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { UserEvent } from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategoryStats, getRecentAttempts } from "./api/attempts";
import { getCategories } from "./api/categories";
import {
  getQuestions,
  submitSelfGrade,
  submitTextAnswer,
} from "./api/questions";
import type { QuestionResponse } from "./api/questions";
import { ApiError, getCurrentUser, login, logout, signUp } from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/categories");

// 에러 화면이 NotFoundError를 instanceof로 구분하므로 클래스는 진짜를 쓴다
vi.mock("./api/questions", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/questions")>()),
  getQuestions: vi.fn(),
  submitTextAnswer: vi.fn(),
  submitSelfGrade: vi.fn(),
}));

vi.mock("./api/attempts", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/attempts")>()),
  getCategoryStats: vi.fn(),
  getRecentAttempts: vi.fn(),
}));

// 모듈 전체를 자동으로 대체하면 ApiError도 가짜가 되어 status와 message가 비게 된다
vi.mock("./api/users", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/users")>()),
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  signUp: vi.fn(),
}));

const EMAIL = "user@example.com";
const PASSWORD = "password1234";
const NICKNAME = "철수";
const USER: UserResponse = {
  id: 1,
  email: EMAIL,
  role: "USER",
  nickname: NICKNAME,
  tag: 3,
};
// 메뉴에는 이메일 대신 닉네임#번호를 /me 링크로 보여 준다 (nickname R9, D8)
const MENU_NAME = "철수#3";
const CATEGORY_NAME = "Java";
const QUIZ_PATH = "/categories/java";
const QUESTION_CONTENT = "첫 번째 문제";
const CHOICE_CONTENT = "가방";
const QUESTIONS: readonly QuestionResponse[] = [
  {
    id: 101,
    type: "MULTIPLE_CHOICE",
    content: QUESTION_CONTENT,
    choices: [
      { id: 11, content: CHOICE_CONTENT },
      { id: 12, content: "라면" },
    ],
  },
];

const HISTORY_CATEGORY_NAME = "기록 카테고리";
const HISTORY_STATS = [
  {
    slug: "java",
    name: HISTORY_CATEGORY_NAME,
    types: [
      {
        type: "MULTIPLE_CHOICE" as const,
        solvedQuestions: 1,
        correctQuestions: 1,
      },
    ],
  },
];

// 서버의 쿠키 상태를 흉내 낸다. 로그인 후 loader가 다시 실행되어야만 메뉴에 반영된다
let currentUser: UserResponse | null;

beforeEach(() => {
  currentUser = null;
  vi.mocked(getCategories).mockReset();
  vi.mocked(getCategories).mockResolvedValue([
    { slug: "java", name: CATEGORY_NAME },
  ]);
  vi.mocked(getQuestions).mockReset();
  vi.mocked(getQuestions).mockResolvedValue(QUESTIONS);
  vi.mocked(getCurrentUser).mockReset();
  vi.mocked(getCurrentUser).mockImplementation(() =>
    Promise.resolve(currentUser),
  );
  vi.mocked(login).mockReset();
  vi.mocked(login).mockImplementation(() => {
    currentUser = USER;
    return Promise.resolve(USER);
  });
  vi.mocked(signUp).mockReset();
  vi.mocked(signUp).mockImplementation(() => {
    currentUser = USER;
    return Promise.resolve(USER);
  });
  vi.mocked(logout).mockReset();
  vi.mocked(logout).mockImplementation(() => {
    currentUser = null;
    return Promise.resolve();
  });
  // 기록 조회 API는 인증 필수다. 비회원이면 서버처럼 401로 거절한다 (R8)
  const unauthorized = () => new ApiError(401, "로그인이 필요합니다.");
  vi.mocked(getCategoryStats).mockReset();
  vi.mocked(getCategoryStats).mockImplementation(() =>
    currentUser === null
      ? Promise.reject(unauthorized())
      : Promise.resolve(HISTORY_STATS),
  );
  vi.mocked(getRecentAttempts).mockReset();
  vi.mocked(getRecentAttempts).mockImplementation(() =>
    currentUser === null ? Promise.reject(unauthorized()) : Promise.resolve([]),
  );
  vi.mocked(submitTextAnswer).mockReset();
  vi.mocked(submitSelfGrade).mockReset();
});

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
}

async function findMenu() {
  return within(await screen.findByRole("navigation"));
}

async function fillCredentials(user: UserEvent) {
  await user.type(await screen.findByLabelText("이메일"), EMAIL);
  await user.type(screen.getByLabelText("비밀번호"), PASSWORD);
}

async function fillSignUp(user: UserEvent) {
  await fillCredentials(user);
  await user.type(screen.getByLabelText("닉네임"), NICKNAME);
}

async function expectLoggedInMenu() {
  const menu = await findMenu();
  expect(await menu.findByRole("link", { name: MENU_NAME })).toHaveAttribute(
    "href",
    "/me",
  );
  expect(menu.getByRole("button", { name: "로그아웃" })).toBeInTheDocument();
  expect(menu.queryByRole("link", { name: "로그인" })).not.toBeInTheDocument();
  // 이메일은 메뉴에 보이지 않는다 (nickname D8)
  expect(menu.queryByText(EMAIL)).not.toBeInTheDocument();
}

async function expectGuestMenu() {
  const menu = await findMenu();
  expect(await menu.findByRole("link", { name: "로그인" })).toBeInTheDocument();
  expect(
    menu.queryByRole("button", { name: "로그아웃" }),
  ).not.toBeInTheDocument();
  expect(menu.queryByText(EMAIL)).not.toBeInTheDocument();
  expect(menu.queryByRole("link", { name: MENU_NAME })).not.toBeInTheDocument();
}

async function expectHome() {
  expect(
    await screen.findByRole("link", { name: CATEGORY_NAME }),
  ).toBeInTheDocument();
}

describe("메뉴 (R10)", () => {
  it("비회원에게는 로그인 링크가 보이고 퀴즈 화면을 그대로 쓸 수 있다 (R8)", async () => {
    renderApp("/");

    await expectGuestMenu();
    await expectHome();
  });

  it("로그인 사용자에게는 이메일 대신 닉네임#번호 링크와 로그아웃이 보인다 (nickname R9, D8)", async () => {
    currentUser = USER;

    renderApp("/");

    await expectLoggedInMenu();
    await expectHome();
  });

  it("기존 메뉴(홈, 소개)는 로그인 여부와 상관없이 남아 있다", async () => {
    renderApp("/");

    const menu = await findMenu();
    expect(await menu.findByRole("link", { name: "홈" })).toBeInTheDocument();
    expect(menu.getByRole("link", { name: "소개" })).toBeInTheDocument();
  });

  it("로그인 링크를 누르면 로그인 화면으로 간다", async () => {
    const user = userEvent.setup();
    renderApp("/");

    const menu = await findMenu();
    await user.click(await menu.findByRole("link", { name: "로그인" }));

    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
  });

  it("닉네임#번호 링크를 누르면 내 정보 화면(/me)으로 간다 (nickname R9)", async () => {
    currentUser = USER;
    const user = userEvent.setup();
    renderApp("/");

    const menu = await findMenu();
    await user.click(await menu.findByRole("link", { name: MENU_NAME }));

    expect(
      await screen.findByRole("heading", { name: "내 정보", level: 1 }),
    ).toBeInTheDocument();
  });

  it("사용자 조회가 실패해도 비회원으로 처리하고 퀴즈 화면을 막지 않는다 (D11)", async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error("HTTP 500"));

    renderApp("/");

    await expectGuestMenu();
    await expectHome();
  });
});

describe("로그아웃 (R7)", () => {
  it("로그아웃을 누르면 메뉴가 비회원 상태로 바뀐다", async () => {
    currentUser = USER;
    const user = userEvent.setup();
    renderApp("/");

    const menu = await findMenu();
    await user.click(await menu.findByRole("button", { name: "로그아웃" }));

    expect(logout).toHaveBeenCalledTimes(1);
    await expectGuestMenu();
  });

  // HttpOnly 쿠키는 프론트에서 지울 수 없으므로 서버 상태(currentUser)는 로그인 그대로다
  it.each([
    ["서버 오류", new ApiError(500, "서버 오류가 발생했습니다.")],
    ["네트워크 오류", new TypeError("Failed to fetch")],
  ])(
    "%s로 로그아웃이 실패하면 로그인 메뉴를 유지하고 에러를 보여 준다 (D15)",
    async (_description, failure) => {
      currentUser = USER;
      vi.mocked(logout).mockRejectedValue(failure);
      const user = userEvent.setup();
      renderApp("/");

      const menu = await findMenu();
      await user.click(await menu.findByRole("button", { name: "로그아웃" }));

      expect(await screen.findByRole("alert")).not.toBeEmptyDOMElement();
      expect(logout).toHaveBeenCalledTimes(1);
      await expectLoggedInMenu();
      await expectHome();
    },
  );

  it("로그아웃 실패 에러는 다른 페이지로 이동하면 사라진다 (D20)", async () => {
    currentUser = USER;
    vi.mocked(logout).mockRejectedValue(new ApiError(500, "서버 오류"));
    const user = userEvent.setup();
    renderApp("/");

    const menu = await findMenu();
    await user.click(await menu.findByRole("button", { name: "로그아웃" }));
    await screen.findByRole("alert");

    await user.click(menu.getByRole("link", { name: "소개" }));

    expect(
      await screen.findByRole("heading", { name: "소개" }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
  });

  describe("퀴즈와 카테고리 loader를 다시 실행하지 않는다 (D17)", () => {
    async function selectChoiceAndLogout(user: UserEvent) {
      await user.click(
        await screen.findByRole("radio", { name: CHOICE_CONTENT }),
      );
      await logoutFromMenu(user);
    }

    async function logoutFromMenu(user: UserEvent) {
      const menu = await findMenu();
      await user.click(await menu.findByRole("button", { name: "로그아웃" }));
      // 메뉴가 바뀌면 action 뒤의 loader 재실행까지 끝난 것이다
      await expectGuestMenu();
    }

    function expectQuizInProgress() {
      expect(
        screen.getByRole("heading", { name: QUESTION_CONTENT }),
      ).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: CHOICE_CONTENT })).toBeChecked();
    }

    it("퀴즈 화면에서 로그아웃해도 문제 조회 API를 다시 호출하지 않고 풀던 화면을 유지한다", async () => {
      currentUser = USER;
      const user = userEvent.setup();
      renderApp(QUIZ_PATH);

      await selectChoiceAndLogout(user);

      expect(getQuestions).toHaveBeenCalledTimes(1);
      expectQuizInProgress();
    });

    it("문제 조회 API가 장애여도 로그아웃 후 풀던 화면이 에러 화면으로 바뀌지 않는다", async () => {
      currentUser = USER;
      vi.mocked(getQuestions)
        .mockReset()
        .mockResolvedValueOnce(QUESTIONS)
        .mockRejectedValue(new Error("HTTP 500"));
      const user = userEvent.setup();
      renderApp(QUIZ_PATH);

      await selectChoiceAndLogout(user);

      expectQuizInProgress();
    });

    it("홈에서 로그아웃해도 카테고리 조회 API를 다시 호출하지 않는다", async () => {
      currentUser = USER;
      const user = userEvent.setup();
      renderApp("/");
      await expectHome();

      await logoutFromMenu(user);

      expect(getCategories).toHaveBeenCalledTimes(1);
      await expectHome();
    });
  });
});

describe("로그인 화면", () => {
  it("비밀번호 칸은 입력값을 가린다", async () => {
    renderApp("/login");

    expect(await screen.findByLabelText("비밀번호")).toHaveAttribute(
      "type",
      "password",
    );
  });

  it("성공하면 홈으로 이동하고 메뉴가 로그인 상태로 바뀐다", async () => {
    const user = userEvent.setup();
    renderApp("/login");

    await fillCredentials(user);
    await user.click(screen.getByRole("button", { name: "로그인" }));

    await expectHome();
    expect(login).toHaveBeenCalledWith(EMAIL, PASSWORD);
    await expectLoggedInMenu();
    expect(screen.queryByLabelText("비밀번호")).not.toBeInTheDocument();
  });

  it("로그인 폼에는 닉네임 입력칸이 없다 (nickname 화면 흐름)", async () => {
    renderApp("/login");

    await screen.findByLabelText("비밀번호");
    expect(screen.queryByLabelText("닉네임")).not.toBeInTheDocument();
  });

  it("인증에 실패하면 폼에 서버 메시지를 보여 주고 이동하지 않는다 (R5)", async () => {
    const message = "이메일 또는 비밀번호가 올바르지 않습니다.";
    vi.mocked(login).mockRejectedValue(new ApiError(401, message));
    const user = userEvent.setup();
    renderApp("/login");

    await fillCredentials(user);
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
    await expectGuestMenu();
  });

  it("네트워크 오류가 나도 에러 화면이 아니라 폼에 에러를 보여 준다", async () => {
    vi.mocked(login).mockRejectedValue(new TypeError("Failed to fetch"));
    const user = userEvent.setup();
    renderApp("/login");

    await fillCredentials(user);
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
  });

  it("회원가입 링크를 누르면 가입 화면으로 간다", async () => {
    const user = userEvent.setup();
    renderApp("/login");

    await user.click(await screen.findByRole("link", { name: "회원가입" }));

    expect(
      await screen.findByRole("button", { name: "회원가입" }),
    ).toBeInTheDocument();
  });
});

describe("회원가입 화면", () => {
  it("비밀번호 칸은 입력값을 가린다", async () => {
    renderApp("/signup");

    expect(await screen.findByLabelText("비밀번호")).toHaveAttribute(
      "type",
      "password",
    );
  });

  it("이메일·비밀번호 아래에 닉네임 입력칸이 있다 (nickname 화면 흐름)", async () => {
    renderApp("/signup");

    const password = await screen.findByLabelText("비밀번호");
    const nickname = screen.getByLabelText("닉네임");
    // 문서 순서상 비밀번호 칸 뒤에 온다
    expect(
      password.compareDocumentPosition(nickname) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("성공하면 입력한 닉네임으로 가입하고 로그인 상태로 홈으로 이동한다 (R1, nickname R1)", async () => {
    const user = userEvent.setup();
    renderApp("/signup");

    await fillSignUp(user);
    await user.click(screen.getByRole("button", { name: "회원가입" }));

    await expectHome();
    expect(signUp).toHaveBeenCalledWith(EMAIL, PASSWORD, NICKNAME);
    await expectLoggedInMenu();
  });

  it("중복 이메일이면 폼에 서버 메시지를 보여 주고 이동하지 않는다", async () => {
    const message = "이미 가입한 이메일입니다.";
    vi.mocked(signUp).mockRejectedValue(new ApiError(409, message));
    const user = userEvent.setup();
    renderApp("/signup");

    await fillSignUp(user);
    await user.click(screen.getByRole("button", { name: "회원가입" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
  });

  it("형식 오류면 폼에 에러를 보여 주고 이동하지 않는다", async () => {
    vi.mocked(signUp).mockRejectedValue(
      new ApiError(400, "Invalid request content."),
    );
    const user = userEvent.setup();
    renderApp("/signup");

    await fillSignUp(user);
    await user.click(screen.getByRole("button", { name: "회원가입" }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
  });

  it("닉네임 규칙 위반(400)이면 서버 메시지를 보여 주고 이동하지 않는다 (nickname R1)", async () => {
    const message = "입력값이 올바르지 않습니다. (항목: nickname)";
    vi.mocked(signUp).mockRejectedValue(new ApiError(400, message));
    const user = userEvent.setup();
    renderApp("/signup");

    await fillCredentials(user);
    await user.type(screen.getByLabelText("닉네임"), "철수#1");
    await user.click(screen.getByRole("button", { name: "회원가입" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(signUp).toHaveBeenCalledWith(EMAIL, PASSWORD, "철수#1");
    expect(screen.getByLabelText("닉네임")).toBeInTheDocument();
    await expectGuestMenu();
  });
});

describe("내 기록 (R10)", () => {
  const HISTORY_LINK = "내 기록";

  async function expectHistoryScreen() {
    expect(
      await screen.findByRole("heading", { name: HISTORY_LINK, level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByText(HISTORY_CATEGORY_NAME)).toBeInTheDocument();
  }

  async function expectLoginScreen() {
    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  }

  it("회원 메뉴에 /history로 가는 내 기록 링크가 있다", async () => {
    currentUser = USER;
    renderApp("/");

    await expectLoggedInMenu();
    const menu = await findMenu();
    expect(menu.getByRole("link", { name: HISTORY_LINK })).toHaveAttribute(
      "href",
      "/history",
    );
  });

  it("비회원 메뉴에는 내 기록 링크가 없다", async () => {
    renderApp("/");

    await expectGuestMenu();
    const menu = await findMenu();
    expect(
      menu.queryByRole("link", { name: HISTORY_LINK }),
    ).not.toBeInTheDocument();
  });

  it("회원이 내 기록 링크를 누르면 정답률과 최근 풀이 화면으로 간다", async () => {
    currentUser = USER;
    const user = userEvent.setup();
    renderApp("/");

    const menu = await findMenu();
    await user.click(await menu.findByRole("link", { name: HISTORY_LINK }));

    await expectHistoryScreen();
  });

  it("비회원이 /history에 오면 로그인 화면으로 보낸다 (R8)", async () => {
    renderApp("/history");

    await expectLoginScreen();
    expect(
      screen.queryByRole("heading", { name: HISTORY_LINK, level: 1 }),
    ).not.toBeInTheDocument();
  });

  // 이 route에는 skipAfterLogout을 붙이지 않아 loader가 다시 실행되고 401로 로그인 화면에 간다
  it("기록 화면에서 로그아웃하면 로그인 화면으로 간다", async () => {
    currentUser = USER;
    const user = userEvent.setup();
    renderApp("/history");
    await expectHistoryScreen();

    const menu = await findMenu();
    await user.click(menu.getByRole("button", { name: "로그아웃" }));

    await expectLoginScreen();
    await expectGuestMenu();
  });
});

describe("회원의 자기 채점 저장 (R2, R5)", () => {
  const SHORT_ANSWER: readonly QuestionResponse[] = [
    { id: 301, type: "SHORT_ANSWER", content: "주관식 문제", choices: [] },
  ];

  async function revealAndGrade(user: UserEvent) {
    await user.type(
      await screen.findByRole("textbox", { name: "내 답" }),
      "내 답안",
    );
    await user.click(screen.getByRole("button", { name: "제출" }));
    await user.click(await screen.findByRole("button", { name: "맞았어요" }));
    await screen.findByRole("heading", { name: "풀이 결과" });
  }

  beforeEach(() => {
    vi.mocked(getQuestions).mockResolvedValue(SHORT_ANSWER);
    vi.mocked(submitTextAnswer).mockResolvedValue({
      modelAnswer: "모범 답안",
      explanation: null,
    });
    vi.mocked(submitSelfGrade).mockResolvedValue();
  });

  it("root loader가 회원이라고 알려 주면 자기 채점을 저장한다", async () => {
    currentUser = USER;
    const user = userEvent.setup();
    renderApp(QUIZ_PATH);

    await revealAndGrade(user);

    expect(submitSelfGrade).toHaveBeenCalledTimes(1);
    expect(submitSelfGrade).toHaveBeenCalledWith(301, true);
  });

  it("비회원이면 자기 채점을 저장하지 않는다", async () => {
    const user = userEvent.setup();
    renderApp(QUIZ_PATH);

    await revealAndGrade(user);

    expect(submitSelfGrade).not.toHaveBeenCalled();
  });
});

describe("이미 로그인한 사용자", () => {
  it.each(["/login", "/signup"])("%s에 오면 홈으로 보낸다", async (path) => {
    currentUser = USER;

    renderApp(path);

    await expectHome();
    expect(screen.queryByLabelText("비밀번호")).not.toBeInTheDocument();
  });
});
