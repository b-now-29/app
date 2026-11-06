// 관리자 화면 (R3, R4, R5, R7, 화면 흐름). appRoutes 전체로 그려서 route 구성까지 함께 확인한다.
//
// 화면 계약 (frontend-dev가 맞출 이름·라벨):
// - 메뉴: role이 ADMIN이면 "관리" 링크 (href="/admin"). USER·비회원에게는 없다.
// - /admin: 공통 틀. <nav aria-label="관리 메뉴"> 안에 "카테고리"(/admin/categories), "문제"(/admin/questions) 링크.
//   loader는 adminLoader (401 → /login, 403 → /). /admin 자체는 /admin/categories로 보낸다.
// - /admin/categories: h1 "카테고리 관리", 목록 <ul aria-label="카테고리 목록">의 항목마다 이름·slug·"문제 N개"와
//   "수정"·"삭제" 버튼. 폼은 하나: 라벨 "slug"·"이름"·"설명"·"표시 순서", 버튼 "추가". "수정"을 누르면 같은 폼이
//   그 카테고리 값으로 채워지고 버튼이 "저장"이 된다 (slug는 바꿀 수 없다). 설명이 비면 null로 보낸다.
//   "표시 순서"는 <input type="number">이고 숫자로 보낸다 (문제 폼도 같다).
//   삭제는 window.confirm으로 확인한 뒤 보낸다.
// - /admin/questions?category=: h1 "문제 관리", 카테고리 선택 <select> 라벨 "카테고리" (option value = slug).
//   카테고리를 고르면 ?category=slug로 바뀌고 목록 <ul aria-label="문제 목록">에 문제 내용 링크(/admin/questions/{id}).
//   "새 문제" 링크 (/admin/questions/new?category=slug).
// - /admin/questions/new, /admin/questions/:id: 문제 폼. 라벨 "카테고리"(select), "유형"(select: 객관식·주관식·서술형,
//   value는 QuestionType), "문제", "해설", "표시 순서", "모범 답안"(객관식이 아닐 때만).
//   객관식이면 보기마다 textbox "보기 N", radio "보기 N 정답", 버튼 "보기 N 삭제", 그리고 "보기 추가" 버튼.
//   새 문제는 객관식에 빈 보기 2개로 시작한다. 버튼 "저장", 수정 화면에는 "삭제"도 있다.
//   해설이 비면 null, 객관식의 modelAnswer는 null, 주관식·서술형의 choices는 []로 보낸다.
//   저장·삭제가 끝나면 /admin/questions?category={그 문제의 카테고리}로 돌아간다.
// - 400·409 응답은 폼 위에 role="alert"로 서버 메시지를 보여 주고 그 화면에 머문다.
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { UserEvent } from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createCategory,
  createQuestion,
  deleteCategory,
  deleteQuestion,
  getAdminCategories,
  getAdminQuestion,
  getAdminQuestions,
  updateCategory,
  updateQuestion,
} from "./api/admin";
import type {
  AdminCategoryResponse,
  AdminQuestionResponse,
  CategoryCreateRequest,
  CategoryUpdateRequest,
  QuestionRequest,
} from "./api/admin";
import { getCategories } from "./api/categories";
import { ApiError, getCurrentUser } from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/admin");
vi.mock("./api/categories");

// 모듈 전체를 자동으로 대체하면 ApiError도 가짜가 되어 status와 message가 비게 된다
vi.mock("./api/users", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/users")>()),
  getCurrentUser: vi.fn(),
}));

const ADMIN: UserResponse = {
  id: 1,
  email: "admin@example.com",
  role: "ADMIN",
};
const USER: UserResponse = { id: 2, email: "user@example.com", role: "USER" };

const HOME_CATEGORY = "홈 카테고리";

const JAVA: AdminCategoryResponse = {
  slug: "java",
  name: "Java",
  description: "자바 기초",
  sortOrder: 10,
  questionCount: 2,
};
const KOTLIN: AdminCategoryResponse = {
  slug: "kotlin",
  name: "Kotlin",
  description: null,
  sortOrder: 20,
  questionCount: 0,
};

const MULTIPLE_CHOICE: AdminQuestionResponse = {
  id: 101,
  categorySlug: "java",
  type: "MULTIPLE_CHOICE",
  content: "문자열 내용을 비교할 때 쓰는 것은?",
  explanation: "equals()는 내용을 비교한다.",
  modelAnswer: null,
  sortOrder: 10,
  choices: [
    { id: 1001, content: "==", correct: false },
    { id: 1002, content: "equals()", correct: true },
  ],
};
const ESSAY: AdminQuestionResponse = {
  id: 102,
  categorySlug: "java",
  type: "ESSAY",
  content: "equals()와 ==를 비교해 서술하세요.",
  explanation: null,
  modelAnswer: "내용 비교와 참조 비교",
  sortOrder: 50,
  choices: [],
};

// 서버 상태를 흉내 낸다. 저장·삭제 뒤 loader가 다시 실행되어야만 화면에 반영된다
let currentUser: UserResponse | null;
let categories: AdminCategoryResponse[];
let questions: AdminQuestionResponse[];

function rejectUnlessAdmin(): Promise<never> | null {
  if (currentUser === null) {
    return Promise.reject(new ApiError(401, "로그인이 필요합니다."));
  }
  if (currentUser.role !== "ADMIN") {
    return Promise.reject(new ApiError(403, "권한이 없습니다."));
  }
  return null;
}

beforeEach(() => {
  currentUser = ADMIN;
  categories = [JAVA, KOTLIN];
  questions = [MULTIPLE_CHOICE, ESSAY];

  vi.mocked(getCurrentUser).mockReset();
  vi.mocked(getCurrentUser).mockImplementation(() =>
    Promise.resolve(currentUser),
  );
  vi.mocked(getCategories).mockReset();
  vi.mocked(getCategories).mockResolvedValue([
    { slug: "java", name: HOME_CATEGORY },
  ]);

  vi.mocked(getAdminCategories).mockReset();
  vi.mocked(getAdminCategories).mockImplementation(
    () => rejectUnlessAdmin() ?? Promise.resolve([...categories]),
  );
  vi.mocked(createCategory).mockReset();
  vi.mocked(createCategory).mockImplementation(
    (request: CategoryCreateRequest) => {
      const created = { ...request, questionCount: 0 };
      categories = [...categories, created];
      return Promise.resolve(created);
    },
  );
  vi.mocked(updateCategory).mockReset();
  vi.mocked(updateCategory).mockImplementation(
    (slug: string, request: CategoryUpdateRequest) => {
      const updated = {
        ...request,
        slug,
        questionCount:
          categories.find((category) => category.slug === slug)
            ?.questionCount ?? 0,
      };
      categories = categories.map((category) =>
        category.slug === slug ? updated : category,
      );
      return Promise.resolve(updated);
    },
  );
  vi.mocked(deleteCategory).mockReset();
  vi.mocked(deleteCategory).mockImplementation((slug: string) => {
    categories = categories.filter((category) => category.slug !== slug);
    return Promise.resolve();
  });

  vi.mocked(getAdminQuestions).mockReset();
  vi.mocked(getAdminQuestions).mockImplementation(
    (slug: string) =>
      rejectUnlessAdmin() ??
      Promise.resolve(
        questions.filter((question) => question.categorySlug === slug),
      ),
  );
  vi.mocked(getAdminQuestion).mockReset();
  vi.mocked(getAdminQuestion).mockImplementation((id: number) => {
    const found = questions.find((question) => question.id === id);
    return (
      rejectUnlessAdmin() ??
      (found
        ? Promise.resolve(found)
        : Promise.reject(new ApiError(404, "문제를 찾을 수 없습니다.")))
    );
  });
  vi.mocked(createQuestion).mockReset();
  vi.mocked(createQuestion).mockImplementation((request: QuestionRequest) => {
    const created = toResponse(900, request);
    questions = [...questions, created];
    return Promise.resolve(created);
  });
  vi.mocked(updateQuestion).mockReset();
  vi.mocked(updateQuestion).mockImplementation(
    (id: number, request: QuestionRequest) => {
      const updated = toResponse(id, request);
      questions = questions.map((question) =>
        question.id === id ? updated : question,
      );
      return Promise.resolve(updated);
    },
  );
  vi.mocked(deleteQuestion).mockReset();
  vi.mocked(deleteQuestion).mockImplementation((id: number) => {
    questions = questions.filter((question) => question.id !== id);
    return Promise.resolve();
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function toResponse(
  id: number,
  request: QuestionRequest,
): AdminQuestionResponse {
  return {
    ...request,
    id,
    choices: request.choices.map((choice, index) => ({
      ...choice,
      id: id * 10 + index,
    })),
  };
}

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

function confirmDialog(answer: boolean) {
  return vi.spyOn(window, "confirm").mockReturnValue(answer);
}

const mainMenu = async () =>
  within(
    await screen.findByRole("navigation", { name: (name) => name === "" }),
  );

// 목록(<ul>)은 처음부터 있으므로 findByRole만으로는 저장 뒤 다시 받은 목록을 기다리지 않는다.
// waitFor로 항목이 나타날 때까지 다시 확인한다.
async function findCategoryItem(name: string) {
  return waitFor(() => {
    const list = screen.getByRole("list", { name: "카테고리 목록" });
    const item = within(list)
      .getAllByRole("listitem")
      .find((element) => element.textContent?.includes(name));
    if (item === undefined) {
      throw new Error(`카테고리 항목이 없습니다: ${name}`);
    }
    return item;
  });
}

function choiceInputs() {
  return screen.getAllByRole("textbox", { name: /^보기 \d+$/ });
}

describe("관리 메뉴 (R3)", () => {
  it("ADMIN에게는 /admin으로 가는 관리 링크가 보인다", async () => {
    renderApp("/");

    const menu = await mainMenu();
    expect(await menu.findByRole("link", { name: "관리" })).toHaveAttribute(
      "href",
      "/admin",
    );
  });

  it.each([
    ["USER", USER],
    ["비회원", null],
  ])("%s에게는 관리 링크가 없다", async (_name, user) => {
    currentUser = user;
    renderApp("/");

    expect(
      await screen.findByRole("link", { name: HOME_CATEGORY }),
    ).toBeInTheDocument();
    const menu = await mainMenu();
    expect(menu.queryByRole("link", { name: "관리" })).not.toBeInTheDocument();
  });

  it("관리 링크를 누르면 카테고리 관리 화면과 관리 탭이 보인다", async () => {
    const user = userEvent.setup();
    renderApp("/");

    const menu = await mainMenu();
    await user.click(await menu.findByRole("link", { name: "관리" }));

    expect(
      await screen.findByRole("heading", { name: "카테고리 관리", level: 1 }),
    ).toBeInTheDocument();
    const tabs = within(screen.getByRole("navigation", { name: "관리 메뉴" }));
    expect(tabs.getByRole("link", { name: "카테고리" })).toHaveAttribute(
      "href",
      "/admin/categories",
    );
    expect(tabs.getByRole("link", { name: "문제" })).toHaveAttribute(
      "href",
      "/admin/questions",
    );
  });
});

describe("관리자 화면 접근 (화면 흐름)", () => {
  it("비회원이 오면 로그인 화면으로 보낸다", async () => {
    currentUser = null;
    const router = renderApp("/admin/categories");

    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it("USER가 오면 홈으로 보낸다", async () => {
    currentUser = USER;
    const router = renderApp("/admin/questions?category=java");

    expect(
      await screen.findByRole("link", { name: HOME_CATEGORY }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(
      screen.queryByRole("heading", { name: "문제 관리" }),
    ).not.toBeInTheDocument();
  });

  it("문제 탭을 누르면 문제 관리 화면으로 간다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/categories");

    const tabs = within(
      await screen.findByRole("navigation", { name: "관리 메뉴" }),
    );
    await user.click(tabs.getByRole("link", { name: "문제" }));

    expect(
      await screen.findByRole("heading", { name: "문제 관리", level: 1 }),
    ).toBeInTheDocument();
  });
});

describe("카테고리 관리 (R4, R7)", () => {
  async function fillCategoryForm(
    user: UserEvent,
    values: {
      slug?: string;
      name: string;
      description: string;
      sortOrder: string;
    },
  ) {
    if (values.slug !== undefined) {
      await user.clear(screen.getByLabelText("slug"));
      await user.type(screen.getByLabelText("slug"), values.slug);
    }
    await user.clear(screen.getByLabelText("이름"));
    await user.type(screen.getByLabelText("이름"), values.name);
    await user.clear(screen.getByLabelText("설명"));
    if (values.description !== "") {
      await user.type(screen.getByLabelText("설명"), values.description);
    }
    await user.clear(screen.getByLabelText("표시 순서"));
    await user.type(screen.getByLabelText("표시 순서"), values.sortOrder);
  }

  it("카테고리마다 이름, slug, 문제 수를 서버가 준 순서대로 보여 준다", async () => {
    renderApp("/admin/categories");

    const list = await screen.findByRole("list", { name: "카테고리 목록" });
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent("Java");
    expect(items[0]).toHaveTextContent("java");
    expect(items[0]).toHaveTextContent("문제 2개");
    expect(items[1]).toHaveTextContent("Kotlin");
    expect(items[1]).toHaveTextContent("문제 0개");
  });

  it("폼으로 카테고리를 추가하면 요청을 보내고 목록에 나타난다. 빈 설명은 null이다 (R4)", async () => {
    const user = userEvent.setup();
    renderApp("/admin/categories");
    await screen.findByRole("list", { name: "카테고리 목록" });

    await fillCategoryForm(user, {
      slug: "spring",
      name: "Spring",
      description: "",
      sortOrder: "30",
    });
    await user.click(screen.getByRole("button", { name: "추가" }));

    await findCategoryItem("Spring");
    expect(createCategory).toHaveBeenCalledWith({
      slug: "spring",
      name: "Spring",
      description: null,
      sortOrder: 30,
    });
  });

  it.each([
    [409, "이미 있는 slug입니다: java"],
    [400, "slug는 영문 소문자, 숫자, -만 쓸 수 있습니다."],
  ])(
    "추가가 %i이면 폼 위에 서버 메시지를 보여 주고 입력값을 남긴다 (R4)",
    async (status, message) => {
      vi.mocked(createCategory).mockRejectedValue(
        new ApiError(status, message),
      );
      const user = userEvent.setup();
      renderApp("/admin/categories");
      await screen.findByRole("list", { name: "카테고리 목록" });

      await fillCategoryForm(user, {
        slug: "java",
        name: "다른 자바",
        description: "",
        sortOrder: "1",
      });
      await user.click(screen.getByRole("button", { name: "추가" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(message);
      expect(screen.getByLabelText("slug")).toHaveValue("java");
      expect(screen.getByLabelText("이름")).toHaveValue("다른 자바");
    },
  );

  it("수정을 누르면 폼이 그 카테고리 값으로 채워지고, 저장하면 slug 경로로 보낸다 (R4)", async () => {
    const user = userEvent.setup();
    renderApp("/admin/categories");

    await user.click(
      within(await findCategoryItem("Java")).getByRole("button", {
        name: "수정",
      }),
    );

    expect(screen.getByLabelText("이름")).toHaveValue("Java");
    expect(screen.getByLabelText("설명")).toHaveValue("자바 기초");
    expect(screen.getByLabelText("표시 순서")).toHaveValue(10);
    // slug는 바꿀 수 없다
    const slug = screen.queryByLabelText("slug");
    if (slug !== null) {
      expect(slug).toBeDisabled();
    }

    await fillCategoryForm(user, {
      name: "자바",
      description: "자바 문법",
      sortOrder: "5",
    });
    await user.click(screen.getByRole("button", { name: "저장" }));

    await findCategoryItem("자바");
    expect(updateCategory).toHaveBeenCalledWith("java", {
      name: "자바",
      description: "자바 문법",
      sortOrder: 5,
    });
  });

  it("삭제를 확인하면 삭제 요청을 보내고 목록에서 빠진다 (R6)", async () => {
    const confirm = confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/categories");

    await user.click(
      within(await findCategoryItem("Kotlin")).getByRole("button", {
        name: "삭제",
      }),
    );

    expect(confirm).toHaveBeenCalled();
    await waitFor(() => {
      const list = screen.getByRole("list", { name: "카테고리 목록" });
      expect(within(list).getAllByRole("listitem")).toHaveLength(1);
    });
    expect(
      screen.getByRole("list", { name: "카테고리 목록" }),
    ).not.toHaveTextContent("Kotlin");
    expect(deleteCategory).toHaveBeenCalledWith("kotlin");
  });

  it("삭제 확인을 취소하면 요청을 보내지 않는다", async () => {
    confirmDialog(false);
    const user = userEvent.setup();
    renderApp("/admin/categories");

    await user.click(
      within(await findCategoryItem("Kotlin")).getByRole("button", {
        name: "삭제",
      }),
    );

    expect(deleteCategory).not.toHaveBeenCalled();
    expect(await findCategoryItem("Kotlin")).toBeInTheDocument();
  });

  it("문제가 남은 카테고리를 삭제해 409가 오면 서버 메시지를 보여 주고 목록에 남긴다 (R7)", async () => {
    const message =
      "삭제하지 않은 문제가 남아 있어 카테고리를 삭제할 수 없습니다.";
    vi.mocked(deleteCategory).mockRejectedValue(new ApiError(409, message));
    confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/categories");

    await user.click(
      within(await findCategoryItem("Java")).getByRole("button", {
        name: "삭제",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(await findCategoryItem("Java")).toBeInTheDocument();
  });
});

describe("문제 목록", () => {
  it("카테고리를 고르면 주소에 category가 붙고 그 카테고리의 문제가 보인다", async () => {
    const user = userEvent.setup();
    const router = renderApp("/admin/questions");

    await user.selectOptions(
      await screen.findByRole("combobox", { name: "카테고리" }),
      "java",
    );

    const list = await screen.findByRole("list", { name: "문제 목록" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(2);
    expect(getAdminQuestions).toHaveBeenCalledWith("java");
    expect(router.state.location.search).toBe("?category=java");
  });

  it("문제 내용은 수정 화면 링크이고, 새 문제 링크는 그 카테고리를 넘긴다", async () => {
    renderApp("/admin/questions?category=java");

    const list = await screen.findByRole("list", { name: "문제 목록" });
    expect(
      within(list).getByRole("link", { name: MULTIPLE_CHOICE.content }),
    ).toHaveAttribute("href", "/admin/questions/101");
    expect(screen.getByRole("link", { name: "새 문제" })).toHaveAttribute(
      "href",
      "/admin/questions/new?category=java",
    );
    expect(screen.getByRole("combobox", { name: "카테고리" })).toHaveValue(
      "java",
    );
  });
});

describe("문제 폼 (R5, D6)", () => {
  async function startNewQuestion() {
    const router = renderApp("/admin/questions/new?category=java");
    await screen.findByRole("textbox", { name: "문제" });
    return router;
  }

  async function setSortOrder(user: UserEvent, value: string) {
    await user.clear(screen.getByLabelText("표시 순서"));
    await user.type(screen.getByLabelText("표시 순서"), value);
  }

  it("새 문제는 넘겨받은 카테고리의 객관식으로, 빈 보기 2개와 함께 시작하고 모범 답안 칸은 없다", async () => {
    await startNewQuestion();

    expect(screen.getByRole("combobox", { name: "카테고리" })).toHaveValue(
      "java",
    );
    expect(screen.getByRole("combobox", { name: "유형" })).toHaveValue(
      "MULTIPLE_CHOICE",
    );
    expect(choiceInputs()).toHaveLength(2);
    expect(
      choiceInputs().map((input) => (input as HTMLInputElement).value),
    ).toEqual(["", ""]);
    expect(
      screen.queryByRole("textbox", { name: "모범 답안" }),
    ).not.toBeInTheDocument();
  });

  it("유형을 주관식·서술형으로 바꾸면 보기 편집 대신 모범 답안 칸이 보이고, 객관식으로 돌리면 다시 보기 편집이다", async () => {
    const user = userEvent.setup();
    await startNewQuestion();
    const type = screen.getByRole("combobox", { name: "유형" });

    await user.selectOptions(type, "SHORT_ANSWER");
    expect(
      screen.getByRole("textbox", { name: "모범 답안" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "보기 추가" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("textbox", { name: "보기 1" }),
    ).not.toBeInTheDocument();

    await user.selectOptions(type, "ESSAY");
    expect(
      screen.getByRole("textbox", { name: "모범 답안" }),
    ).toBeInTheDocument();

    await user.selectOptions(type, "MULTIPLE_CHOICE");
    expect(
      screen.queryByRole("textbox", { name: "모범 답안" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "보기 추가" }),
    ).toBeInTheDocument();
  });

  it("보기를 추가·삭제하고 정답을 고른 대로 객관식을 등록한 뒤 목록으로 돌아간다", async () => {
    const user = userEvent.setup();
    const router = await startNewQuestion();

    await user.type(
      screen.getByRole("textbox", { name: "문제" }),
      "새 문제 내용",
    );
    await setSortOrder(user, "30");
    await user.click(screen.getByRole("button", { name: "보기 추가" }));
    await user.type(screen.getByRole("textbox", { name: "보기 1" }), "A");
    await user.type(screen.getByRole("textbox", { name: "보기 2" }), "B");
    await user.type(screen.getByRole("textbox", { name: "보기 3" }), "C");
    await user.click(screen.getByRole("radio", { name: "보기 2 정답" }));
    await user.click(screen.getByRole("button", { name: "보기 1 삭제" }));

    // 남은 보기의 번호가 다시 매겨지고, 정답은 B에 그대로 있다
    expect(
      choiceInputs().map((input) => (input as HTMLInputElement).value),
    ).toEqual(["B", "C"]);
    expect(screen.getByRole("radio", { name: "보기 1 정답" })).toBeChecked();
    expect(
      screen.getByRole("radio", { name: "보기 2 정답" }),
    ).not.toBeChecked();

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/admin/questions");
    });
    expect(router.state.location.search).toBe("?category=java");
    expect(createQuestion).toHaveBeenCalledWith({
      categorySlug: "java",
      type: "MULTIPLE_CHOICE",
      content: "새 문제 내용",
      explanation: null,
      modelAnswer: null,
      sortOrder: 30,
      choices: [
        { content: "B", correct: true },
        { content: "C", correct: false },
      ],
    });
    expect(
      await screen.findByRole("link", { name: "새 문제 내용" }),
    ).toBeInTheDocument();
  });

  it("주관식은 모범 답안과 빈 보기 목록으로 등록한다", async () => {
    const user = userEvent.setup();
    await startNewQuestion();

    await user.selectOptions(
      screen.getByRole("combobox", { name: "유형" }),
      "SHORT_ANSWER",
    );
    await user.type(
      screen.getByRole("textbox", { name: "문제" }),
      "주관식 문제",
    );
    await user.type(screen.getByRole("textbox", { name: "해설" }), "해설 내용");
    await user.type(
      screen.getByRole("textbox", { name: "모범 답안" }),
      "모범 답안 내용",
    );
    await setSortOrder(user, "40");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(createQuestion).toHaveBeenCalledWith({
        categorySlug: "java",
        type: "SHORT_ANSWER",
        content: "주관식 문제",
        explanation: "해설 내용",
        modelAnswer: "모범 답안 내용",
        sortOrder: 40,
        choices: [],
      });
    });
  });

  it.each([
    // 문제 API에는 409 경우가 없으므로 400만 확인한다 (D13). 도메인 규칙 위반과 Bean Validation 실패 (D14) 두 문구
    [400, "객관식은 정답 보기가 정확히 1개여야 합니다."],
    [400, "입력값이 올바르지 않습니다. (항목: choices[0].content)"],
  ])(
    "저장이 %i이면 폼 위에 서버 메시지를 보여 주고 폼에 머문다 (D11, D13)",
    async (status, message) => {
      vi.mocked(createQuestion).mockRejectedValue(
        new ApiError(status, message),
      );
      const user = userEvent.setup();
      const router = await startNewQuestion();

      await user.type(
        screen.getByRole("textbox", { name: "문제" }),
        "문제 내용",
      );
      await user.type(screen.getByRole("textbox", { name: "보기 1" }), "A");
      await user.type(screen.getByRole("textbox", { name: "보기 2" }), "B");
      // 화면 검사(D23)를 통과해야 서버까지 간다
      await user.click(screen.getByRole("radio", { name: "보기 1 정답" }));
      await user.click(screen.getByRole("button", { name: "저장" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(message);
      expect(createQuestion).toHaveBeenCalledTimes(1);
      expect(router.state.location.pathname).toBe("/admin/questions/new");
      expect(screen.getByRole("textbox", { name: "문제" })).toHaveValue(
        "문제 내용",
      );
    },
  );

  it("수정 화면은 문제 값으로 채워지고, 고쳐서 저장하면 보기를 통째로 보내고 목록으로 돌아간다 (D6)", async () => {
    const user = userEvent.setup();
    const router = renderApp("/admin/questions/101");

    const content = await screen.findByRole("textbox", { name: "문제" });
    expect(content).toHaveValue(MULTIPLE_CHOICE.content);
    expect(screen.getByRole("combobox", { name: "유형" })).toHaveValue(
      "MULTIPLE_CHOICE",
    );
    expect(screen.getByRole("textbox", { name: "해설" })).toHaveValue(
      MULTIPLE_CHOICE.explanation,
    );
    expect(
      choiceInputs().map((input) => (input as HTMLInputElement).value),
    ).toEqual(["==", "equals()"]);
    expect(screen.getByRole("radio", { name: "보기 2 정답" })).toBeChecked();

    await user.clear(content);
    await user.type(content, "고친 문제");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/admin/questions");
    });
    expect(router.state.location.search).toBe("?category=java");
    expect(updateQuestion).toHaveBeenCalledWith(101, {
      categorySlug: "java",
      type: "MULTIPLE_CHOICE",
      content: "고친 문제",
      explanation: MULTIPLE_CHOICE.explanation,
      modelAnswer: null,
      sortOrder: 10,
      choices: [
        { content: "==", correct: false },
        { content: "equals()", correct: true },
      ],
    });
  });

  it("수정 화면에서 객관식을 서술형으로 바꾸면 보기 없이 모범 답안으로 보낸다 (D6)", async () => {
    const user = userEvent.setup();
    renderApp("/admin/questions/101");
    await screen.findByRole("textbox", { name: "문제" });

    await user.selectOptions(
      screen.getByRole("combobox", { name: "유형" }),
      "ESSAY",
    );
    await user.type(
      screen.getByRole("textbox", { name: "모범 답안" }),
      "서술형 모범 답안",
    );
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(updateQuestion).toHaveBeenCalledWith(
        101,
        expect.objectContaining({
          type: "ESSAY",
          modelAnswer: "서술형 모범 답안",
          choices: [],
        }),
      );
    });
  });

  it("서술형 수정 화면은 모범 답안 칸이 채워져 있고 보기 편집은 없다", async () => {
    renderApp("/admin/questions/102");

    expect(
      await screen.findByRole("textbox", { name: "모범 답안" }),
    ).toHaveValue(ESSAY.modelAnswer);
    expect(
      screen.queryByRole("button", { name: "보기 추가" }),
    ).not.toBeInTheDocument();
  });

  it("수정 화면에서 삭제를 확인하면 삭제하고 목록으로 돌아가며 그 문제는 목록에 없다 (R6)", async () => {
    confirmDialog(true);
    const user = userEvent.setup();
    const router = renderApp("/admin/questions/101");
    await screen.findByRole("textbox", { name: "문제" });

    await user.click(screen.getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/admin/questions");
    });
    expect(router.state.location.search).toBe("?category=java");
    expect(deleteQuestion).toHaveBeenCalledWith(101);
    const list = await screen.findByRole("list", { name: "문제 목록" });
    expect(
      within(list).queryByText(MULTIPLE_CHOICE.content),
    ).not.toBeInTheDocument();
    expect(within(list).getByText(ESSAY.content)).toBeInTheDocument();
  });

  it("문제 삭제 확인을 취소하면 요청을 보내지 않고 폼에 머문다", async () => {
    confirmDialog(false);
    const user = userEvent.setup();
    const router = renderApp("/admin/questions/101");
    await screen.findByRole("textbox", { name: "문제" });

    await user.click(screen.getByRole("button", { name: "삭제" }));

    expect(deleteQuestion).not.toHaveBeenCalled();
    expect(router.state.location.pathname).toBe("/admin/questions/101");
  });
});

// 코드 리뷰 반영 (D19~D21, D23)
// - D19: 표시 순서가 비었거나 정수가 아니면 요청을 보내지 않고 "표시 순서는 정수로 입력해 주세요." 를 보여 준다
//   (카테고리 폼·문제 폼 모두). D23부터는 폼 위 alert가 아니라 그 칸 아래 설명이다.
// - D23: 화면 검사 에러는 그 칸에 aria-invalid="true"와 aria-describedby(에러 문구 요소)로 연결한다.
//   테스트는 toHaveAccessibleDescription(문구)로 확인한다. 정답 미선택 에러는 모든 "보기 N 정답" radio에 연결한다.
// - D20: 문제 수정 화면에서 삭제하면, 카테고리 select를 바꿨더라도 불러온 문제의 원래 카테고리 목록으로 간다.
// - D21: 보기를 지워도 남은 보기의 입력값·정답 선택이 그 보기를 따라간다.
const SORT_ORDER_ERROR = "표시 순서는 정수로 입력해 주세요.";

// 그 칸에 에러가 연결됐는지 확인한다 (D23)
async function expectFieldError(element: HTMLElement, message: string) {
  await waitFor(() => {
    expect(element).toHaveAccessibleDescription(message);
  });
  expect(element).toHaveAttribute("aria-invalid", "true");
}

function expectNoFieldError(element: HTMLElement) {
  expect(element).not.toHaveAttribute("aria-invalid", "true");
}

const sortOrderInput = () =>
  screen.getByRole("spinbutton", { name: "표시 순서" });

describe("표시 순서는 정수만 받는다 (D19)", () => {
  async function typeSortOrder(user: UserEvent, value: string) {
    const input = screen.getByLabelText("표시 순서");
    await user.clear(input);
    if (value !== "") {
      await user.type(input, value);
    }
  }

  it.each(["", "1.5"])(
    "카테고리 폼의 표시 순서가 '%s'이면 추가 요청을 보내지 않고 에러를 보여 준다",
    async (sortOrder) => {
      const user = userEvent.setup();
      renderApp("/admin/categories");
      await screen.findByRole("list", { name: "카테고리 목록" });

      await user.type(screen.getByLabelText("slug"), "spring");
      await user.type(screen.getByLabelText("이름"), "Spring");
      await typeSortOrder(user, sortOrder);
      await user.click(screen.getByRole("button", { name: "추가" }));

      await expectFieldError(sortOrderInput(), SORT_ORDER_ERROR);
      expect(createCategory).not.toHaveBeenCalled();
      expect(screen.getByLabelText("slug")).toHaveValue("spring");
    },
  );

  it.each(["", "1.5"])(
    "카테고리 수정 폼의 표시 순서가 '%s'이면 저장 요청을 보내지 않는다",
    async (sortOrder) => {
      const user = userEvent.setup();
      renderApp("/admin/categories");

      await user.click(
        within(await findCategoryItem("Java")).getByRole("button", {
          name: "수정",
        }),
      );
      await typeSortOrder(user, sortOrder);
      await user.click(screen.getByRole("button", { name: "저장" }));

      await expectFieldError(sortOrderInput(), SORT_ORDER_ERROR);
      expect(updateCategory).not.toHaveBeenCalled();
    },
  );

  it.each(["", "1.5"])(
    "문제 폼의 표시 순서가 '%s'이면 저장 요청을 보내지 않고 폼에 머문다",
    async (sortOrder) => {
      const user = userEvent.setup();
      const router = renderApp("/admin/questions/new?category=java");
      await screen.findByRole("textbox", { name: "문제" });

      await user.type(screen.getByRole("textbox", { name: "문제" }), "문제");
      await user.type(screen.getByRole("textbox", { name: "보기 1" }), "A");
      await user.type(screen.getByRole("textbox", { name: "보기 2" }), "B");
      await user.click(screen.getByRole("radio", { name: "보기 1 정답" }));
      await typeSortOrder(user, sortOrder);
      await user.click(screen.getByRole("button", { name: "저장" }));

      await expectFieldError(sortOrderInput(), SORT_ORDER_ERROR);
      expect(createQuestion).not.toHaveBeenCalled();
      expect(router.state.location.pathname).toBe("/admin/questions/new");
    },
  );

  it("문제 수정 폼도 정수가 아닌 표시 순서는 보내지 않는다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/questions/101");
    await screen.findByRole("textbox", { name: "문제" });

    await typeSortOrder(user, "2.5");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await expectFieldError(sortOrderInput(), SORT_ORDER_ERROR);
    expect(updateQuestion).not.toHaveBeenCalled();
  });

  it("음수와 0은 정수라 그대로 보낸다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/categories");
    await screen.findByRole("list", { name: "카테고리 목록" });

    await user.type(screen.getByLabelText("slug"), "spring");
    await user.type(screen.getByLabelText("이름"), "Spring");
    await typeSortOrder(user, "-5");
    await user.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(createCategory).toHaveBeenCalledWith(
        expect.objectContaining({ slug: "spring", sortOrder: -5 }),
      );
    });
  });
});

describe("삭제 후 원래 카테고리 목록으로 간다 (D20)", () => {
  it("카테고리 select를 바꾼 뒤 삭제해도 불러온 문제의 카테고리 목록으로 간다", async () => {
    confirmDialog(true);
    const user = userEvent.setup();
    const router = renderApp("/admin/questions/101");
    await screen.findByRole("textbox", { name: "문제" });

    await user.selectOptions(
      screen.getByRole("combobox", { name: "카테고리" }),
      "kotlin",
    );
    await user.click(screen.getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/admin/questions");
    });
    expect(router.state.location.search).toBe("?category=java");
    expect(deleteQuestion).toHaveBeenCalledWith(101);
  });
});

describe("보기 삭제 후에도 입력값과 정답이 그 보기를 따라간다 (D21)", () => {
  function choiceValues() {
    return choiceInputs().map((input) => (input as HTMLInputElement).value);
  }

  it("세 보기 중 가운데를 지우면 세 번째 보기가 정답인 채로 두 번째 자리에 남고, 이어서 고쳐도 그 보기가 바뀐다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/questions/new?category=java");
    await screen.findByRole("textbox", { name: "문제" });

    await user.type(screen.getByRole("textbox", { name: "문제" }), "문제");
    await user.click(screen.getByRole("button", { name: "보기 추가" }));
    await user.type(screen.getByRole("textbox", { name: "보기 1" }), "A");
    await user.type(screen.getByRole("textbox", { name: "보기 2" }), "B");
    await user.type(screen.getByRole("textbox", { name: "보기 3" }), "C");
    await user.click(screen.getByRole("radio", { name: "보기 3 정답" }));

    await user.click(screen.getByRole("button", { name: "보기 2 삭제" }));

    expect(choiceValues()).toEqual(["A", "C"]);
    expect(
      screen.getByRole("radio", { name: "보기 1 정답" }),
    ).not.toBeChecked();
    expect(screen.getByRole("radio", { name: "보기 2 정답" })).toBeChecked();

    await user.type(screen.getByRole("textbox", { name: "보기 2" }), "!");
    expect(choiceValues()).toEqual(["A", "C!"]);

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(createQuestion).toHaveBeenCalledWith(
        expect.objectContaining({
          choices: [
            { content: "A", correct: false },
            { content: "C!", correct: true },
          ],
        }),
      );
    });
  });

  it("수정 화면에서 첫 보기를 지워도 남은 보기의 값과 정답이 유지된다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/questions/101");
    await screen.findByRole("textbox", { name: "문제" });

    await user.click(screen.getByRole("button", { name: "보기 추가" }));
    await user.type(
      screen.getByRole("textbox", { name: "보기 3" }),
      "hashCode()",
    );
    await user.click(screen.getByRole("button", { name: "보기 1 삭제" }));

    expect(choiceValues()).toEqual(["equals()", "hashCode()"]);
    expect(screen.getByRole("radio", { name: "보기 1 정답" })).toBeChecked();
    expect(
      screen.getByRole("radio", { name: "보기 2 정답" }),
    ).not.toBeChecked();
  });
});

describe("문제 폼 화면 검사 (D23)", () => {
  const contentInput = () => screen.getByRole("textbox", { name: "문제" });
  const choiceInput = (n: number) =>
    screen.getByRole("textbox", { name: `보기 ${n}` });
  const correctRadio = (n: number) =>
    screen.getByRole("radio", { name: `보기 ${n} 정답` });
  const save = () => screen.getByRole("button", { name: "저장" });

  async function startNewQuestion() {
    const router = renderApp("/admin/questions/new?category=java");
    await screen.findByRole("textbox", { name: "문제" });
    return router;
  }

  it("빈 새 객관식을 저장하면 문제·보기 1·보기 2·정답에 각각 에러가 나고 요청을 보내지 않는다", async () => {
    const user = userEvent.setup();
    const router = await startNewQuestion();

    await user.click(save());

    await expectFieldError(contentInput(), "문제 내용을 입력해 주세요.");
    await expectFieldError(choiceInput(1), "보기 1 내용을 입력해 주세요.");
    await expectFieldError(choiceInput(2), "보기 2 내용을 입력해 주세요.");
    await expectFieldError(correctRadio(1), "정답 보기를 하나 선택해 주세요.");
    await expectFieldError(correctRadio(2), "정답 보기를 하나 선택해 주세요.");
    expectNoFieldError(sortOrderInput());
    expect(createQuestion).not.toHaveBeenCalled();
    expect(router.state.location.pathname).toBe("/admin/questions/new");
  });

  it("보기를 채웠지만 정답을 고르지 않으면 정답 에러만 난다", async () => {
    const user = userEvent.setup();
    await startNewQuestion();

    await user.type(contentInput(), "문제 내용");
    await user.type(choiceInput(1), "A");
    await user.type(choiceInput(2), "B");
    await user.click(save());

    await expectFieldError(correctRadio(1), "정답 보기를 하나 선택해 주세요.");
    expectNoFieldError(contentInput());
    expectNoFieldError(choiceInput(1));
    expectNoFieldError(choiceInput(2));
    expect(createQuestion).not.toHaveBeenCalled();
  });

  it("공백만 있는 문제 내용과 보기는 빈 값으로 본다", async () => {
    const user = userEvent.setup();
    await startNewQuestion();

    await user.type(contentInput(), "   ");
    await user.type(choiceInput(1), "A");
    await user.type(choiceInput(2), "  ");
    await user.click(correctRadio(1));
    await user.click(save());

    await expectFieldError(contentInput(), "문제 내용을 입력해 주세요.");
    await expectFieldError(choiceInput(2), "보기 2 내용을 입력해 주세요.");
    expectNoFieldError(choiceInput(1));
    expectNoFieldError(correctRadio(1));
    expect(createQuestion).not.toHaveBeenCalled();
  });

  it("주관식의 모범 답안이 비면 모범 답안 칸에 에러가 나고 보기는 보지 않는다", async () => {
    const user = userEvent.setup();
    await startNewQuestion();

    await user.selectOptions(
      screen.getByRole("combobox", { name: "유형" }),
      "SHORT_ANSWER",
    );
    await user.type(contentInput(), "주관식 문제");
    await user.type(screen.getByRole("textbox", { name: "모범 답안" }), "  ");
    await user.click(save());

    await expectFieldError(
      screen.getByRole("textbox", { name: "모범 답안" }),
      "모범 답안을 입력해 주세요.",
    );
    expectNoFieldError(contentInput());
    expect(screen.queryByText(/정답 보기를 하나/)).not.toBeInTheDocument();
    expect(screen.queryByText(/보기 \d+ 내용을/)).not.toBeInTheDocument();
    expect(createQuestion).not.toHaveBeenCalled();
  });

  it("에러를 고친 뒤 다시 저장하면 요청을 보내고 목록으로 간다", async () => {
    const user = userEvent.setup();
    const router = await startNewQuestion();

    await user.click(save());
    await expectFieldError(contentInput(), "문제 내용을 입력해 주세요.");

    await user.type(contentInput(), "문제 내용");
    await user.type(choiceInput(1), "A");
    await user.type(choiceInput(2), "B");
    await user.click(correctRadio(2));
    await user.click(save());

    await waitFor(() => {
      expect(createQuestion).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/admin/questions");
    });
  });

  it("보기가 2개면 보기 삭제 버튼이 비활성이고, 추가하면 지울 수 있다", async () => {
    const user = userEvent.setup();
    await startNewQuestion();

    expect(screen.getByRole("button", { name: "보기 1 삭제" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "보기 2 삭제" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "보기 추가" }));

    expect(screen.getByRole("button", { name: "보기 1 삭제" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "보기 3 삭제" })).toBeEnabled();
  });
});

describe("카테고리 폼 화면 검사 (D23)", () => {
  const slugInput = () => screen.getByRole("textbox", { name: "slug" });
  const nameInput = () => screen.getByRole("textbox", { name: "이름" });

  async function startCategories() {
    renderApp("/admin/categories");
    await screen.findByRole("list", { name: "카테고리 목록" });
  }

  it.each(["Spring", "spring boot", "스프링", "a".repeat(51)])(
    "추가할 때 slug가 '%s'이면 slug 칸에 에러가 나고 요청을 보내지 않는다",
    async (slug) => {
      const user = userEvent.setup();
      await startCategories();

      await user.type(slugInput(), slug);
      await user.type(nameInput(), "Spring");
      await user.click(screen.getByRole("button", { name: "추가" }));

      await expectFieldError(
        slugInput(),
        "slug는 영어 소문자, 숫자, -로 50자 이내로 입력해 주세요.",
      );
      expectNoFieldError(nameInput());
      expect(createCategory).not.toHaveBeenCalled();
    },
  );

  it("추가할 때 빈 slug도 형식 에러다", async () => {
    const user = userEvent.setup();
    await startCategories();

    await user.type(nameInput(), "Spring");
    await user.click(screen.getByRole("button", { name: "추가" }));

    await expectFieldError(
      slugInput(),
      "slug는 영어 소문자, 숫자, -로 50자 이내로 입력해 주세요.",
    );
    expect(createCategory).not.toHaveBeenCalled();
  });

  it("이름이 공백뿐이면 이름 칸에 에러가 나고 요청을 보내지 않는다", async () => {
    const user = userEvent.setup();
    await startCategories();

    await user.type(slugInput(), "spring");
    await user.type(nameInput(), "   ");
    await user.click(screen.getByRole("button", { name: "추가" }));

    await expectFieldError(nameInput(), "이름을 입력해 주세요.");
    expectNoFieldError(slugInput());
    expect(createCategory).not.toHaveBeenCalled();
  });

  it("수정할 때 이름을 지우면 에러가 나고 저장 요청을 보내지 않는다", async () => {
    const user = userEvent.setup();
    await startCategories();

    await user.click(
      within(await findCategoryItem("Java")).getByRole("button", {
        name: "수정",
      }),
    );
    await user.clear(nameInput());
    await user.click(screen.getByRole("button", { name: "저장" }));

    await expectFieldError(nameInput(), "이름을 입력해 주세요.");
    expect(updateCategory).not.toHaveBeenCalled();
  });

  it("에러를 고친 뒤 다시 추가하면 요청을 보낸다", async () => {
    const user = userEvent.setup();
    await startCategories();

    await user.type(slugInput(), "Spring");
    await user.type(nameInput(), "Spring");
    await user.click(screen.getByRole("button", { name: "추가" }));
    await expectFieldError(
      slugInput(),
      "slug는 영어 소문자, 숫자, -로 50자 이내로 입력해 주세요.",
    );

    await user.clear(slugInput());
    await user.type(slugInput(), "spring");
    await user.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(createCategory).toHaveBeenCalledWith(
        expect.objectContaining({ slug: "spring", name: "Spring" }),
      );
    });
  });
});
