import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { UserEvent } from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  getQuestions,
  NotFoundError,
  submitAnswer,
  submitTextAnswer,
} from "../api/questions";
import type {
  AnswerResponse,
  QuestionResponse,
  TextAnswerResponse,
} from "../api/questions";
import QuizErrorPage from "./QuizErrorPage";
import QuizPage from "./QuizPage";
import { quizLoader } from "./quizLoader";

// 모듈 전체를 자동으로 대체하면 NotFoundError도 가짜가 되어 404 판별을 검증할 수 없다
vi.mock("../api/questions", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/questions")>()),
  getQuestions: vi.fn(),
  submitAnswer: vi.fn(),
  submitTextAnswer: vi.fn(),
}));

const EXPLANATION = "해설 내용";

// 첫 문제의 보기는 id로 정렬해도, 글자로 정렬해도 서버 순서와 달라지도록 섞었다
const QUESTIONS: readonly QuestionResponse[] = [
  {
    id: 101,
    type: "MULTIPLE_CHOICE",
    content: "첫 번째 문제",
    choices: [
      { id: 13, content: "다람쥐" },
      { id: 11, content: "가방" },
      { id: 14, content: "라면" },
      { id: 12, content: "나무" },
    ],
  },
  {
    id: 102,
    type: "MULTIPLE_CHOICE",
    content: "두 번째 문제",
    choices: [
      { id: 21, content: "사과" },
      { id: 22, content: "포도" },
    ],
  },
  {
    id: 103,
    type: "MULTIPLE_CHOICE",
    content: "세 번째 문제",
    choices: [
      { id: 31, content: "연필" },
      { id: 32, content: "지우개" },
    ],
  },
];

const OTHER_CATEGORY_QUESTIONS: readonly QuestionResponse[] = [
  {
    id: 201,
    type: "MULTIPLE_CHOICE",
    content: "다른 카테고리의 첫 문제",
    choices: [
      { id: 41, content: "바다" },
      { id: 42, content: "하늘" },
    ],
  },
  {
    id: 202,
    type: "MULTIPLE_CHOICE",
    content: "다른 카테고리의 둘째 문제",
    choices: [
      { id: 51, content: "구름" },
      { id: 52, content: "바람" },
    ],
  },
];

const SHORT_ANSWER_QUESTIONS: readonly QuestionResponse[] = [
  { id: 301, type: "SHORT_ANSWER", content: "첫 주관식 문제", choices: [] },
  { id: 302, type: "SHORT_ANSWER", content: "둘째 주관식 문제", choices: [] },
];

const MULTIPLE_CHOICE_QUESTION: QuestionResponse = {
  id: 401,
  type: "MULTIPLE_CHOICE",
  content: "객관식 문제",
  choices: [
    { id: 61, content: "하나" },
    { id: 62, content: "둘" },
  ],
};
const SHORT_ANSWER_QUESTION: QuestionResponse = {
  id: 402,
  type: "SHORT_ANSWER",
  content: "주관식 문제",
  choices: [],
};

const MY_ANSWER = "내가 쓴 답";
const MODEL_ANSWER = "모범 답안 내용";
const REVEAL: TextAnswerResponse = {
  modelAnswer: MODEL_ANSWER,
  explanation: EXPLANATION,
};

const NOT_FOUND_TITLE = "페이지를 찾을 수 없습니다";
const LIST_LINK = "카테고리 목록";
const LIST_SCREEN = "카테고리 목록 화면";
const SCORE_TITLE = "풀이 결과";

function correctAnswer(correctChoiceId: number): AnswerResponse {
  return { correct: true, correctChoiceId, explanation: EXPLANATION };
}

function wrongAnswer(correctChoiceId: number): AnswerResponse {
  return { correct: false, correctChoiceId, explanation: EXPLANATION };
}

function CategoryListStub() {
  return <h1>{LIST_SCREEN}</h1>;
}

function renderQuiz() {
  const router = createMemoryRouter(
    [
      { path: "/", Component: CategoryListStub },
      {
        path: "/categories/:slug",
        Component: QuizPage,
        loader: quizLoader,
        ErrorBoundary: QuizErrorPage,
      },
    ],
    { initialEntries: ["/categories/java"] },
  );
  render(<RouterProvider router={router} />);
  return { router, user: userEvent.setup() };
}

async function startQuiz() {
  const rendered = renderQuiz();
  await screen.findByRole("radiogroup");
  return rendered;
}

const radioGroup = () => screen.getByRole("radiogroup");
const radio = (name: string) => screen.getByRole("radio", { name });
const checkedRadio = () => screen.queryByRole("radio", { checked: true });
const button = (name: string) => screen.getByRole("button", { name });
const queryButton = (name: string) => screen.queryByRole("button", { name });
const link = (name: string) => screen.getByRole("link", { name });
const heading = (name: string) => screen.getByRole("heading", { name });
const findHeading = (name: string) => screen.findByRole("heading", { name });
const findResult = () => screen.findByRole("status");
const queryResult = () => screen.queryByRole("status");
const queryCorrectMark = () => screen.queryByText("정답");

async function answer(user: UserEvent, choiceName: string) {
  await user.click(radio(choiceName));
  await user.click(button("제출"));
  await findResult();
}

async function solveAllQuestions(user: UserEvent) {
  await answer(user, "가방");
  await user.click(button("다음 문제"));
  await answer(user, "사과");
  await user.click(button("다음 문제"));
  await answer(user, "연필");
  await user.click(button("결과 보기"));
}

function pendingAnswer() {
  let resolve: (result: AnswerResponse) => void = () => {};
  const promise = new Promise<AnswerResponse>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function mockQuestionsBySlug() {
  vi.mocked(getQuestions).mockImplementation(async (slug) => {
    return slug === "java" ? QUESTIONS : OTHER_CATEGORY_QUESTIONS;
  });
}

describe("문제 풀이 화면", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(getQuestions).mockResolvedValue(QUESTIONS);
  });

  describe("문제 표시 (R2)", () => {
    it("주소의 slug로 문제를 불러와 첫 문제와 진행도를 보여 준다", async () => {
      await startQuiz();

      expect(getQuestions).toHaveBeenCalledWith("java");
      expect(heading("첫 번째 문제")).toBeInTheDocument();
      expect(screen.getByText("문제 1 / 3")).toBeInTheDocument();
    });

    it("보기를 서버가 준 순서대로 보여 준다", async () => {
      await startQuiz();

      const radios = screen.getAllByRole("radio");
      expect(radios).toHaveLength(4);
      ["다람쥐", "가방", "라면", "나무"].forEach((name, index) => {
        expect(radios[index]).toHaveAccessibleName(name);
      });
    });

    it("문제 내용이 라디오 그룹의 이름이 된다", async () => {
      await startQuiz();

      expect(radioGroup()).toHaveAccessibleName("첫 번째 문제");
    });
  });

  describe("보기 선택과 제출 (R4)", () => {
    it("보기를 고르기 전에는 제출할 수 없다", async () => {
      const { user } = await startQuiz();

      expect(checkedRadio()).not.toBeInTheDocument();
      expect(button("제출")).toBeDisabled();

      await user.click(button("제출"));

      expect(submitAnswer).not.toHaveBeenCalled();
    });

    it("보기 글자를 누르면 그 보기가 선택되고 제출할 수 있다", async () => {
      const { user } = await startQuiz();

      await user.click(screen.getByText("가방"));

      expect(radio("가방")).toBeChecked();
      expect(button("제출")).toBeEnabled();
    });

    it("제출하기 전에는 다른 보기로 바꿀 수 있다", async () => {
      const { user } = await startQuiz();

      await user.click(radio("가방"));
      await user.click(radio("나무"));

      expect(radio("나무")).toBeChecked();
      expect(radio("가방")).not.toBeChecked();
    });

    it("현재 문제와 고른 보기의 id로 채점을 요청한다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();

      await answer(user, "가방");

      expect(submitAnswer).toHaveBeenCalledTimes(1);
      expect(submitAnswer).toHaveBeenCalledWith(101, 11);
    });

    it("응답을 기다리는 동안에는 제출 버튼이 비활성이고 다시 눌러도 요청하지 않는다", async () => {
      const pending = pendingAnswer();
      vi.mocked(submitAnswer).mockReturnValue(pending.promise);
      const { user } = await startQuiz();
      await user.click(radio("가방"));
      // loading 중에는 버튼 안 구조가 바뀌어 이름으로 못 찾을 수 있으므로 미리 잡아 둔다
      const submit = button("제출");

      await user.click(submit);

      expect(submit).toBeDisabled();
      expect(queryResult()).not.toBeInTheDocument();

      await user.click(submit);

      expect(submitAnswer).toHaveBeenCalledTimes(1);

      pending.resolve(correctAnswer(11));
      expect(await findResult()).toHaveTextContent("정답입니다");
    });

    it("응답을 기다리는 동안에는 보기를 바꿀 수 없다", async () => {
      const pending = pendingAnswer();
      vi.mocked(submitAnswer).mockReturnValue(pending.promise);
      const { user } = await startQuiz();
      await user.click(radio("가방"));
      await user.click(button("제출"));

      await user.click(screen.getByText("나무"));

      expect(radio("나무")).toBeDisabled();
      expect(radio("나무")).not.toBeChecked();
      expect(radio("가방")).toBeChecked();

      pending.resolve(correctAnswer(11));
      await findResult();
    });
  });

  describe("채점 결과 (R5)", () => {
    it("정답이면 정답 문구와 해설을 보여 준다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();

      await answer(user, "가방");

      const result = await findResult();
      expect(result).toHaveTextContent("정답입니다");
      expect(result).toHaveTextContent(EXPLANATION);
    });

    it("오답이면 오답 문구와 해설을 보여 준다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(wrongAnswer(12));
      const { user } = await startQuiz();

      await answer(user, "가방");

      const result = await findResult();
      expect(result).toHaveTextContent("오답입니다");
      expect(result).toHaveTextContent(EXPLANATION);
    });

    it.each([
      { result: correctAnswer(11), label: "정답입니다" },
      { result: wrongAnswer(12), label: "오답입니다" },
    ])("해설이 null이면 $label 문구만 보여 준다", async ({ result, label }) => {
      vi.mocked(submitAnswer).mockResolvedValue({
        ...result,
        explanation: null,
      });
      const { user } = await startQuiz();

      await answer(user, "가방");

      expect((await findResult()).textContent).toBe(label);
    });

    it("채점 전에는 어느 보기에도 정답 표시가 없다", async () => {
      await startQuiz();

      expect(queryCorrectMark()).not.toBeInTheDocument();
    });

    it("오답이면 고른 보기를 유지한 채 정답 보기에 정답 글자를 붙인다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(wrongAnswer(12));
      const { user } = await startQuiz();

      await answer(user, "가방");

      expect(radio("나무 정답")).not.toBeChecked();
      expect(radio("가방")).toBeChecked();
      expect(screen.getAllByText("정답")).toHaveLength(1);
    });

    it("정답이면 고른 보기에 정답 글자를 붙인다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();

      await answer(user, "가방");

      expect(radio("가방 정답")).toBeChecked();
      expect(screen.getAllByText("정답")).toHaveLength(1);
    });

    it("채점 뒤에는 모든 보기가 비활성이고 눌러도 답이 바뀌지 않는다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(wrongAnswer(12));
      const { user } = await startQuiz();
      await answer(user, "가방");

      screen.getAllByRole("radio").forEach((choice) => {
        expect(choice).toBeDisabled();
      });

      await user.click(screen.getByText("라면"));

      expect(radio("라면")).not.toBeChecked();
      expect(radio("가방")).toBeChecked();
    });

    it("채점 뒤에는 제출 버튼 대신 다음으로 가는 버튼을 보여 준다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();

      await answer(user, "가방");

      expect(queryButton("제출")).not.toBeInTheDocument();
      expect(button("다음 문제")).toBeEnabled();
    });

    // 즉시 resolve하는 mock이라 실제 네트워크 지연에서의 안전까지 보장하지는 않는다
    it("제출 버튼을 더블클릭해도 채점 결과가 남아 있다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();
      await user.click(radio("가방"));

      await user.dblClick(button("제출"));

      expect(await findResult()).toHaveTextContent("정답입니다");
      expect(screen.getByText("문제 1 / 3")).toBeInTheDocument();
      expect(submitAnswer).toHaveBeenCalledTimes(1);
    });
  });

  describe("다음 문제", () => {
    it("다음 문제로 넘어가면 진행도, 문제, 라디오 그룹 이름, 보기가 바뀐다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();
      await answer(user, "가방");

      await user.click(button("다음 문제"));

      expect(screen.getByText("문제 2 / 3")).toBeInTheDocument();
      expect(heading("두 번째 문제")).toBeInTheDocument();
      expect(radioGroup()).toHaveAccessibleName("두 번째 문제");
      expect(screen.getAllByRole("radio")).toHaveLength(2);
      expect(radio("사과")).toBeEnabled();
    });

    it("다음 문제로 넘어가면 선택과 채점 결과가 비워진다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();
      await answer(user, "가방");

      await user.click(button("다음 문제"));

      expect(checkedRadio()).not.toBeInTheDocument();
      expect(button("제출")).toBeDisabled();
      expect(queryResult()).not.toBeInTheDocument();
      expect(queryCorrectMark()).not.toBeInTheDocument();
    });

    it("다음 문제의 제출은 그 문제의 id로 요청한다", async () => {
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(correctAnswer(11))
        .mockResolvedValueOnce(correctAnswer(21));
      const { user } = await startQuiz();
      await answer(user, "가방");
      await user.click(button("다음 문제"));

      await answer(user, "사과");

      expect(submitAnswer).toHaveBeenLastCalledWith(102, 21);
    });
  });

  describe("점수 (R6)", () => {
    it("마지막 문제에서만 결과 보기 버튼을 보여 준다", async () => {
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(correctAnswer(11))
        .mockResolvedValueOnce(correctAnswer(21))
        .mockResolvedValueOnce(correctAnswer(31));
      const { user } = await startQuiz();

      await answer(user, "가방");
      expect(button("다음 문제")).toBeInTheDocument();
      expect(queryButton("결과 보기")).not.toBeInTheDocument();
      await user.click(button("다음 문제"));

      await answer(user, "사과");
      expect(button("다음 문제")).toBeInTheDocument();
      expect(queryButton("결과 보기")).not.toBeInTheDocument();
      await user.click(button("다음 문제"));

      await answer(user, "연필");
      expect(button("결과 보기")).toBeInTheDocument();
      expect(queryButton("다음 문제")).not.toBeInTheDocument();
    });

    it("마지막 문제 다음에 풀이 결과 제목과 객관식 N문제 중 M문제 정답을 보여 준다 (D14)", async () => {
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(correctAnswer(11))
        .mockResolvedValueOnce(wrongAnswer(22))
        .mockResolvedValueOnce(correctAnswer(31));
      const { user } = await startQuiz();

      await solveAllQuestions(user);

      expect(heading(SCORE_TITLE)).toBeInTheDocument();
      expect(
        screen.getByText("객관식 3문제 중 2문제 정답"),
      ).toBeInTheDocument();
      expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    });

    it("모두 틀리면 0문제 정답을 보여 준다", async () => {
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(wrongAnswer(12))
        .mockResolvedValueOnce(wrongAnswer(22))
        .mockResolvedValueOnce(wrongAnswer(32));
      const { user } = await startQuiz();

      await solveAllQuestions(user);

      expect(
        screen.getByText("객관식 3문제 중 0문제 정답"),
      ).toBeInTheDocument();
    });

    it("점수 화면의 링크로 카테고리 목록에 돌아간다", async () => {
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(correctAnswer(11))
        .mockResolvedValueOnce(correctAnswer(21))
        .mockResolvedValueOnce(correctAnswer(31));
      const { user } = await startQuiz();
      await solveAllQuestions(user);

      expect(link(LIST_LINK)).toHaveAttribute("href", "/");

      await user.click(link(LIST_LINK));

      expect(await findHeading(LIST_SCREEN)).toBeInTheDocument();
    });
  });

  describe("채점 실패 (R7)", () => {
    it("실패하면 고른 보기를 유지한 채 에러를 표시하고 다시 제출할 수 있다", async () => {
      vi.mocked(submitAnswer).mockRejectedValue(new Error("HTTP 500"));
      const { user } = await startQuiz();
      await user.click(radio("가방"));

      await user.click(button("제출"));

      expect(await screen.findByRole("alert")).toHaveTextContent("실패");
      expect(radio("가방")).toBeChecked();
      expect(button("제출")).toBeEnabled();
      expect(queryResult()).not.toBeInTheDocument();
    });

    it("다시 제출해 성공하면 에러가 사라지고 채점 결과를 보여 준다", async () => {
      vi.mocked(submitAnswer)
        .mockRejectedValueOnce(new Error("HTTP 500"))
        .mockResolvedValueOnce(correctAnswer(11));
      const { user } = await startQuiz();
      await user.click(radio("가방"));
      await user.click(button("제출"));
      await screen.findByRole("alert");

      await user.click(button("제출"));

      expect(await findResult()).toHaveTextContent("정답입니다");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(submitAnswer).toHaveBeenCalledTimes(2);
      expect(submitAnswer).toHaveBeenLastCalledWith(101, 11);
    });

    it("다시 제출한 응답을 기다리는 동안에는 에러를 지운다 (D12)", async () => {
      const pending = pendingAnswer();
      vi.mocked(submitAnswer)
        .mockRejectedValueOnce(new Error("HTTP 500"))
        .mockReturnValueOnce(pending.promise);
      const { user } = await startQuiz();
      await user.click(radio("가방"));
      await user.click(button("제출"));
      await screen.findByRole("alert");

      await user.click(button("제출"));

      expect(screen.queryByRole("alert")).not.toBeInTheDocument();

      pending.resolve(correctAnswer(11));
      await findResult();
    });

    it("실패한 뒤에는 보기를 바꿔 제출할 수 있다 (D12)", async () => {
      vi.mocked(submitAnswer)
        .mockRejectedValueOnce(new Error("HTTP 500"))
        .mockResolvedValueOnce(correctAnswer(12));
      const { user } = await startQuiz();
      await user.click(radio("가방"));
      await user.click(button("제출"));
      await screen.findByRole("alert");

      await user.click(radio("나무"));

      expect(radio("나무")).toBeChecked();
      expect(radio("가방")).not.toBeChecked();
      expect(screen.getByRole("alert")).toBeInTheDocument();

      await user.click(button("제출"));
      await findResult();

      expect(submitAnswer).toHaveBeenLastCalledWith(101, 12);
    });
  });

  describe("빈 목록과 불러오기 실패 (R8)", () => {
    it("문제가 없으면 안내 문구와 목록 링크를 보여 준다", async () => {
      vi.mocked(getQuestions).mockResolvedValue([]);
      const { user } = renderQuiz();

      expect(await findHeading("아직 문제가 없습니다")).toBeInTheDocument();
      expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
      expect(queryButton("제출")).not.toBeInTheDocument();
      expect(link(LIST_LINK)).toHaveAttribute("href", "/");

      await user.click(link(LIST_LINK));

      expect(await findHeading(LIST_SCREEN)).toBeInTheDocument();
    });

    it("없는 카테고리면 404 화면을 보여 준다", async () => {
      vi.mocked(getQuestions).mockRejectedValue(
        new NotFoundError("없는 카테고리: java"),
      );

      renderQuiz();

      expect(await findHeading(NOT_FOUND_TITLE)).toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("그 밖의 실패는 404가 아닌 일반 에러 화면을 보여 준다", async () => {
      vi.mocked(getQuestions).mockRejectedValue(new Error("HTTP 500"));

      renderQuiz();

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "문제가 발생했습니다",
      );
      expect(
        screen.queryByRole("heading", { name: NOT_FOUND_TITLE }),
      ).not.toBeInTheDocument();
    });
  });

  describe("더블클릭과 키보드 입력 구분 (D18)", () => {
    const KEYS = [
      { name: "Enter", keys: "{Enter}" },
      { name: "Space", keys: " " },
    ];

    async function goToLastQuestion(user: UserEvent) {
      await answer(user, "가방");
      await user.click(button("다음 문제"));
      await answer(user, "사과");
      await user.click(button("다음 문제"));
    }

    function focusButton(name: string) {
      act(() => button(name).focus());
      expect(button(name)).toHaveFocus();
    }

    it.each(KEYS)(
      "다음 문제 버튼에 포커스를 두고 $name 키를 누르면 다음 문제로 넘어간다",
      async ({ keys }) => {
        vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
        const { user } = await startQuiz();
        await answer(user, "가방");
        focusButton("다음 문제");

        await user.keyboard(keys);

        expect(screen.getByText("문제 2 / 3")).toBeInTheDocument();
        expect(heading("두 번째 문제")).toBeInTheDocument();
      },
    );

    it.each(KEYS)(
      "결과 보기 버튼에 포커스를 두고 $name 키를 누르면 점수 화면으로 넘어간다",
      async ({ keys }) => {
        vi.mocked(submitAnswer)
          .mockResolvedValueOnce(correctAnswer(11))
          .mockResolvedValueOnce(wrongAnswer(22))
          .mockResolvedValueOnce(correctAnswer(31));
        const { user } = await startQuiz();
        await goToLastQuestion(user);
        await answer(user, "연필");
        focusButton("결과 보기");

        await user.keyboard(keys);

        expect(
          screen.getByText("객관식 3문제 중 2문제 정답"),
        ).toBeInTheDocument();
      },
    );

    it("마지막 문제에서 제출을 더블클릭해도 점수 화면으로 건너뛰지 않고 채점 결과가 남아 있다", async () => {
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(correctAnswer(11))
        .mockResolvedValueOnce(correctAnswer(21))
        .mockResolvedValueOnce(correctAnswer(31));
      const { user } = await startQuiz();
      await goToLastQuestion(user);
      await user.click(radio("연필"));

      await user.dblClick(button("제출"));

      expect(await findResult()).toHaveTextContent("정답입니다");
      expect(screen.getByText("문제 3 / 3")).toBeInTheDocument();
      expect(button("결과 보기")).toBeEnabled();
      expect(submitAnswer).toHaveBeenCalledTimes(3);
    });

    it("다음 문제 버튼을 더블클릭해도 한 문제만 넘어간다", async () => {
      vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(11));
      const { user } = await startQuiz();
      await answer(user, "가방");

      await user.dblClick(button("다음 문제"));

      expect(screen.getByText("문제 2 / 3")).toBeInTheDocument();
      expect(heading("두 번째 문제")).toBeInTheDocument();
      expect(queryResult()).not.toBeInTheDocument();
    });

    it("응답을 기다리는 중에 제출을 더블클릭하면 요청은 한 번이고 응답 뒤 채점 결과가 남아 있다", async () => {
      const pending = pendingAnswer();
      vi.mocked(submitAnswer).mockReturnValue(pending.promise);
      const { user } = await startQuiz();
      await user.click(radio("가방"));

      await user.dblClick(button("제출"));

      expect(submitAnswer).toHaveBeenCalledTimes(1);
      expect(queryResult()).not.toBeInTheDocument();

      pending.resolve(correctAnswer(11));

      expect(await findResult()).toHaveTextContent("정답입니다");
      expect(screen.getByText("문제 1 / 3")).toBeInTheDocument();
      expect(button("다음 문제")).toBeEnabled();
    });
  });

  describe("주관식 풀이", () => {
    const answerBox = () => screen.getByRole("textbox", { name: "내 답" });

    async function startShortAnswerQuiz(
      questions: readonly QuestionResponse[] = SHORT_ANSWER_QUESTIONS,
    ) {
      vi.mocked(getQuestions).mockResolvedValue(questions);
      vi.mocked(submitTextAnswer).mockResolvedValue(REVEAL);
      const rendered = renderQuiz();
      await screen.findByRole("textbox", { name: "내 답" });
      return rendered;
    }

    async function submitMyAnswer(user: UserEvent) {
      await user.type(answerBox(), MY_ANSWER);
      await user.click(button("제출"));
      await screen.findByRole("heading", { name: "모범 답안" });
    }

    describe("입력과 제출 조건 (R3, D11)", () => {
      it("답을 입력하기 전에는 제출할 수 없고 모범 답안도 보이지 않는다", async () => {
        const { user } = await startShortAnswerQuiz();

        expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
        expect(screen.queryByText(MODEL_ANSWER)).not.toBeInTheDocument();
        expect(button("제출")).toBeDisabled();

        await user.click(button("제출"));

        expect(submitTextAnswer).not.toHaveBeenCalled();
      });

      it("공백과 줄바꿈만 입력하면 제출할 수 없다", async () => {
        const { user } = await startShortAnswerQuiz();

        await user.type(answerBox(), "   {Enter}  ");

        expect(answerBox()).toHaveValue("   \n  ");
        expect(button("제출")).toBeDisabled();
      });

      it("공백이 아닌 답을 입력하면 제출할 수 있고, 다 지우면 다시 제출할 수 없다", async () => {
        const { user } = await startShortAnswerQuiz();

        await user.type(answerBox(), MY_ANSWER);
        expect(button("제출")).toBeEnabled();

        await user.clear(answerBox());
        expect(button("제출")).toBeDisabled();
      });

      it("Enter는 줄바꿈만 하고 제출하지 않는다", async () => {
        const { user } = await startShortAnswerQuiz();

        await user.type(answerBox(), "첫 줄{Enter}둘째 줄");

        expect(answerBox()).toHaveValue("첫 줄\n둘째 줄");
        expect(submitTextAnswer).not.toHaveBeenCalled();
        expect(queryButton("맞았어요")).not.toBeInTheDocument();
      });

      it("응답을 기다리는 동안에는 입력칸과 제출 버튼이 비활성이고 다시 요청하지 않는다", async () => {
        const { user } = await startShortAnswerQuiz();
        let resolve: (reveal: TextAnswerResponse) => void = () => {};
        vi.mocked(submitTextAnswer).mockReturnValue(
          new Promise((done) => {
            resolve = done;
          }),
        );
        await user.type(answerBox(), MY_ANSWER);
        // loading 중에는 버튼 안 구조가 바뀌어 이름으로 못 찾을 수 있으므로 미리 잡아 둔다
        const submit = button("제출");

        await user.click(submit);

        expect(submit).toBeDisabled();
        expect(answerBox()).toBeDisabled();

        await user.click(submit);

        expect(submitTextAnswer).toHaveBeenCalledTimes(1);

        resolve(REVEAL);
        await screen.findByRole("heading", { name: "모범 답안" });
      });
    });

    describe("제출 결과 (R4)", () => {
      it("현재 문제의 id와 입력한 답을 그대로 보낸다", async () => {
        const { user } = await startShortAnswerQuiz();

        await user.type(answerBox(), "  첫 줄{Enter}둘째 줄 ");
        await user.click(button("제출"));
        await screen.findByRole("heading", { name: "모범 답안" });

        expect(submitTextAnswer).toHaveBeenCalledTimes(1);
        expect(submitTextAnswer).toHaveBeenCalledWith(301, "  첫 줄\n둘째 줄 ");
        expect(submitAnswer).not.toHaveBeenCalled();
      });

      it("내 답, 모범 답안, 해설을 함께 보여 주고 입력칸을 비활성으로 둔다", async () => {
        const { user } = await startShortAnswerQuiz();

        await submitMyAnswer(user);

        expect(answerBox()).toHaveValue(MY_ANSWER);
        expect(answerBox()).toBeDisabled();
        expect(screen.getByText(MODEL_ANSWER)).toBeInTheDocument();
        expect(screen.getByText(EXPLANATION)).toBeInTheDocument();
      });

      it("해설이 null이면 해설 없이 모범 답안만 보여 준다", async () => {
        const { user } = await startShortAnswerQuiz();
        vi.mocked(submitTextAnswer).mockResolvedValue({
          modelAnswer: MODEL_ANSWER,
          explanation: null,
        });

        await submitMyAnswer(user);

        expect(screen.getByText(MODEL_ANSWER)).toBeInTheDocument();
        expect(screen.queryByText("해설")).not.toBeInTheDocument();
      });

      it("자기 채점 전에는 제출과 다음으로 가는 버튼 없이 두 버튼만 보여 준다 (R5)", async () => {
        const { user } = await startShortAnswerQuiz();

        await submitMyAnswer(user);

        expect(button("맞았어요")).toBeEnabled();
        expect(button("틀렸어요")).toBeEnabled();
        expect(queryButton("제출")).not.toBeInTheDocument();
        expect(queryButton("다음 문제")).not.toBeInTheDocument();
        expect(queryButton("결과 보기")).not.toBeInTheDocument();
      });
    });

    describe("자기 채점 (R5, D9)", () => {
      it.each(["맞았어요", "틀렸어요"])(
        "%s를 누르면 바로 다음 문제로 가서 입력칸과 모범 답안을 비운다",
        async (name) => {
          const { user } = await startShortAnswerQuiz();
          await submitMyAnswer(user);

          await user.click(button(name));

          expect(screen.getByText("문제 2 / 2")).toBeInTheDocument();
          expect(heading("둘째 주관식 문제")).toBeInTheDocument();
          expect(answerBox()).toHaveValue("");
          expect(answerBox()).toBeEnabled();
          expect(button("제출")).toBeDisabled();
          expect(screen.queryByText(MODEL_ANSWER)).not.toBeInTheDocument();
        },
      );

      it("다음 문제의 제출은 그 문제의 id로 요청한다", async () => {
        const { user } = await startShortAnswerQuiz();
        await submitMyAnswer(user);
        await user.click(button("틀렸어요"));

        await submitMyAnswer(user);

        expect(submitTextAnswer).toHaveBeenLastCalledWith(302, MY_ANSWER);
      });

      it.each([
        { name: "맞았어요", line: "주관식 1문제 중 1문제 맞음" },
        { name: "틀렸어요", line: "주관식 1문제 중 0문제 맞음" },
      ])(
        "마지막 문제에서 $name를 누르면 바로 점수 화면으로 간다",
        async ({ name, line }) => {
          const { user } = await startShortAnswerQuiz([SHORT_ANSWER_QUESTION]);
          await submitMyAnswer(user);

          await user.click(button(name));

          expect(heading(SCORE_TITLE)).toBeInTheDocument();
          expect(screen.getByText(line)).toBeInTheDocument();
          expect(link(LIST_LINK)).toHaveAttribute("href", "/");
        },
      );
    });

    describe("제출 실패 (R7, D6)", () => {
      it("실패하면 답을 유지한 채 에러를 표시하고 다시 제출할 수 있다", async () => {
        const { user } = await startShortAnswerQuiz();
        vi.mocked(submitTextAnswer).mockRejectedValue(new Error("HTTP 500"));
        await user.type(answerBox(), MY_ANSWER);

        await user.click(button("제출"));

        expect(await screen.findByRole("alert")).toHaveTextContent("실패");
        expect(answerBox()).toHaveValue(MY_ANSWER);
        expect(answerBox()).toBeEnabled();
        expect(button("제출")).toBeEnabled();
        expect(queryButton("맞았어요")).not.toBeInTheDocument();
      });

      it("다시 제출해 성공하면 에러가 사라지고 모범 답안을 보여 준다", async () => {
        const { user } = await startShortAnswerQuiz();
        vi.mocked(submitTextAnswer)
          .mockRejectedValueOnce(new Error("HTTP 500"))
          .mockResolvedValueOnce(REVEAL);
        await user.type(answerBox(), MY_ANSWER);
        await user.click(button("제출"));
        await screen.findByRole("alert");

        await user.click(button("제출"));

        expect(await screen.findByText(MODEL_ANSWER)).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(submitTextAnswer).toHaveBeenCalledTimes(2);
        expect(submitTextAnswer).toHaveBeenLastCalledWith(301, MY_ANSWER);
      });

      it("실패한 뒤에는 답을 고쳐 제출할 수 있다", async () => {
        const { user } = await startShortAnswerQuiz();
        vi.mocked(submitTextAnswer)
          .mockRejectedValueOnce(new Error("HTTP 400"))
          .mockResolvedValueOnce(REVEAL);
        await user.type(answerBox(), MY_ANSWER);
        await user.click(button("제출"));
        await screen.findByRole("alert");

        await user.type(answerBox(), " 추가");
        await user.click(button("제출"));
        await screen.findByText(MODEL_ANSWER);

        expect(submitTextAnswer).toHaveBeenLastCalledWith(
          301,
          `${MY_ANSWER} 추가`,
        );
      });
    });

    describe("더블클릭과 키보드 입력 구분 (D5)", () => {
      // 더블클릭의 두 번째 클릭이 새로 나타난 버튼에 걸리는 상황은 jsdom의 user-event로 재현되지 않는다
      // (같은 요소를 두 번 누른다). 그래서 두 번째 클릭에 해당하는 detail 2 클릭을 직접 보낸다.
      it.each(["맞았어요", "틀렸어요"])(
        "%s에 걸린 연속 클릭의 두 번째 클릭은 무시한다",
        async (name) => {
          const { user } = await startShortAnswerQuiz();
          await submitMyAnswer(user);

          fireEvent.click(button(name), { detail: 2 });

          expect(screen.getByText("문제 1 / 2")).toBeInTheDocument();
          expect(screen.getByText(MODEL_ANSWER)).toBeInTheDocument();
          expect(button(name)).toBeEnabled();
        },
      );

      it("제출을 더블클릭해도 모범 답안이 남아 있고 요청은 한 번이다", async () => {
        const { user } = await startShortAnswerQuiz();
        await user.type(answerBox(), MY_ANSWER);

        await user.dblClick(button("제출"));

        expect(await screen.findByText(MODEL_ANSWER)).toBeInTheDocument();
        expect(screen.getByText("문제 1 / 2")).toBeInTheDocument();
        expect(button("맞았어요")).toBeEnabled();
        expect(submitTextAnswer).toHaveBeenCalledTimes(1);
      });

      it("맞았어요를 더블클릭해도 한 문제만 넘어가고 한 번만 센다", async () => {
        const { user } = await startShortAnswerQuiz();
        await submitMyAnswer(user);

        await user.dblClick(button("맞았어요"));

        expect(screen.getByText("문제 2 / 2")).toBeInTheDocument();
        expect(answerBox()).toHaveValue("");

        await submitMyAnswer(user);
        await user.click(button("틀렸어요"));

        expect(
          screen.getByText("주관식 2문제 중 1문제 맞음"),
        ).toBeInTheDocument();
      });

      it.each([
        { name: "Enter", keys: "{Enter}" },
        { name: "Space", keys: " " },
      ])(
        "자기 채점 버튼에 포커스를 두고 $name 키를 누르면 다음 문제로 넘어간다",
        async ({ keys }) => {
          const { user } = await startShortAnswerQuiz();
          await submitMyAnswer(user);
          act(() => button("맞았어요").focus());

          await user.keyboard(keys);

          expect(screen.getByText("문제 2 / 2")).toBeInTheDocument();
        },
      );
    });

    describe("유형이 섞인 카테고리 (R1, R6, D10)", () => {
      it("객관식 다음 주관식이면 입력칸으로 바뀌고 유형별 점수 줄을 따로 보여 준다", async () => {
        vi.mocked(getQuestions).mockResolvedValue([
          MULTIPLE_CHOICE_QUESTION,
          SHORT_ANSWER_QUESTION,
        ]);
        vi.mocked(submitAnswer).mockResolvedValue(wrongAnswer(62));
        vi.mocked(submitTextAnswer).mockResolvedValue(REVEAL);
        const { user } = await startQuiz();

        await answer(user, "하나");
        await user.click(button("다음 문제"));

        expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
        expect(answerBox()).toHaveValue("");

        await submitMyAnswer(user);
        await user.click(button("맞았어요"));

        expect(heading(SCORE_TITLE)).toBeInTheDocument();
        expect(
          screen.getByText("객관식 1문제 중 0문제 정답"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("주관식 1문제 중 1문제 맞음"),
        ).toBeInTheDocument();
      });

      it("주관식 다음 객관식이면 이전 답이 남지 않아 보기를 고르기 전에는 제출할 수 없다 (D7)", async () => {
        const { user } = await startShortAnswerQuiz([
          SHORT_ANSWER_QUESTION,
          MULTIPLE_CHOICE_QUESTION,
        ]);
        vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(61));
        await submitMyAnswer(user);

        await user.click(button("틀렸어요"));

        expect(radioGroup()).toHaveAccessibleName("객관식 문제");
        expect(checkedRadio()).not.toBeInTheDocument();
        expect(button("제출")).toBeDisabled();

        await answer(user, "하나");
        await user.click(button("결과 보기"));

        expect(submitAnswer).toHaveBeenCalledWith(401, 61);
        expect(
          screen.getByText("객관식 1문제 중 1문제 정답"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("주관식 1문제 중 0문제 맞음"),
        ).toBeInTheDocument();
      });

      it("객관식만 있는 카테고리에는 주관식 줄을 표시하지 않는다", async () => {
        vi.mocked(getQuestions).mockResolvedValue([MULTIPLE_CHOICE_QUESTION]);
        vi.mocked(submitAnswer).mockResolvedValue(correctAnswer(61));
        const { user } = await startQuiz();

        await answer(user, "하나");
        await user.click(button("결과 보기"));

        expect(
          screen.getByText("객관식 1문제 중 1문제 정답"),
        ).toBeInTheDocument();
        expect(screen.queryByText(/주관식/)).not.toBeInTheDocument();
      });

      it("주관식만 있는 카테고리에는 객관식 줄을 표시하지 않는다", async () => {
        const { user } = await startShortAnswerQuiz([SHORT_ANSWER_QUESTION]);
        await submitMyAnswer(user);

        await user.click(button("맞았어요"));

        expect(
          screen.getByText("주관식 1문제 중 1문제 맞음"),
        ).toBeInTheDocument();
        expect(screen.queryByText(/객관식/)).not.toBeInTheDocument();
      });
    });
  });

  describe("카테고리 변경 (D9)", () => {
    it("다른 카테고리로 이동하면 첫 문제부터 새로 시작한다", async () => {
      mockQuestionsBySlug();
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(correctAnswer(11))
        .mockResolvedValueOnce(correctAnswer(21));
      const { router, user } = await startQuiz();
      await answer(user, "가방");
      await user.click(button("다음 문제"));
      await answer(user, "사과");

      await act(() => router.navigate("/categories/database"));

      expect(getQuestions).toHaveBeenLastCalledWith("database");
      expect(await findHeading("다른 카테고리의 첫 문제")).toBeInTheDocument();
      expect(screen.getByText("문제 1 / 2")).toBeInTheDocument();
      expect(checkedRadio()).not.toBeInTheDocument();
      expect(button("제출")).toBeDisabled();
      expect(queryResult()).not.toBeInTheDocument();
      expect(queryCorrectMark()).not.toBeInTheDocument();
    });

    it("다른 카테고리로 이동하면 앞 카테고리의 점수를 이어받지 않는다", async () => {
      mockQuestionsBySlug();
      vi.mocked(submitAnswer)
        .mockResolvedValueOnce(correctAnswer(11))
        .mockResolvedValueOnce(wrongAnswer(42))
        .mockResolvedValueOnce(wrongAnswer(52));
      const { router, user } = await startQuiz();
      await answer(user, "가방");

      await act(() => router.navigate("/categories/database"));
      await findHeading("다른 카테고리의 첫 문제");
      await answer(user, "바다");
      await user.click(button("다음 문제"));
      await answer(user, "구름");
      await user.click(button("결과 보기"));

      expect(
        screen.getByText("객관식 2문제 중 0문제 정답"),
      ).toBeInTheDocument();
    });

    it("응답을 기다리는 중에 이동하면 늦게 온 응답이 새 풀이에 섞이지 않는다", async () => {
      mockQuestionsBySlug();
      const pending = pendingAnswer();
      vi.mocked(submitAnswer).mockReturnValue(pending.promise);
      const { router, user } = await startQuiz();
      await user.click(radio("가방"));
      await user.click(button("제출"));

      await act(() => router.navigate("/categories/database"));
      await findHeading("다른 카테고리의 첫 문제");
      await act(async () => pending.resolve(correctAnswer(11)));

      expect(queryResult()).not.toBeInTheDocument();
      expect(radio("바다")).toBeEnabled();
      expect(button("제출")).toBeDisabled();
    });
  });
});
