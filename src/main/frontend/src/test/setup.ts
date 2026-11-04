import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// SEED 컴포넌트가 ResizeObserver를 쓰는데 jsdom에는 없다. 크기 계산이 없는 테스트 환경에서는 아무 일도 하지 않아도 된다
class ResizeObserverStub implements ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal("ResizeObserver", ResizeObserverStub);

afterEach(() => {
  cleanup();
});
