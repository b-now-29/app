import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

describe("테스트 환경", () => {
  it("jest-dom matcher를 쓸 수 있다", () => {
    render(<button>제출</button>);
    expect(screen.getByRole("button", { name: "제출" })).toBeInTheDocument();
  });

  it("앞 테스트의 화면이 남아 있지 않다", () => {
    render(<button>제출</button>);
    expect(screen.getByRole("button", { name: "제출" })).toBeInTheDocument();
  });
});
