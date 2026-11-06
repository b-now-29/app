import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import SelfGradeButtons from "./SelfGradeButtons";

const button = (name: string) => screen.getByRole("button", { name });

describe("SelfGradeButtons", () => {
  it.each([
    { name: "맞았어요", correct: true },
    { name: "틀렸어요", correct: false },
  ])(
    "$name를 누르면 그 결과로 onSelect를 부른다",
    async ({ name, correct }) => {
      const onSelect = vi.fn();
      const user = userEvent.setup();
      render(<SelfGradeButtons onSelect={onSelect} />);

      await user.click(button(name));

      expect(onSelect).toHaveBeenCalledTimes(1);
      expect(onSelect).toHaveBeenCalledWith(correct);
    },
  );

  // 저장 중에 다시 누르면 기록이 중복 저장되므로 두 버튼을 모두 막는다 (R3, 제약 사항)
  it("disabled면 두 버튼이 모두 비활성이고 눌러도 onSelect를 부르지 않는다", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<SelfGradeButtons onSelect={onSelect} disabled />);

    expect(button("맞았어요")).toBeDisabled();
    expect(button("틀렸어요")).toBeDisabled();

    await user.click(button("맞았어요"));
    await user.click(button("틀렸어요"));

    expect(onSelect).not.toHaveBeenCalled();
  });

  it("disabled를 주지 않으면 두 버튼이 활성이다", () => {
    render(<SelfGradeButtons onSelect={vi.fn()} />);

    expect(button("맞았어요")).toBeEnabled();
    expect(button("틀렸어요")).toBeEnabled();
  });
});
