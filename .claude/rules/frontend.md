---
paths:
  - "src/main/frontend/**"
---

# 프론트엔드 규칙

## 프론트엔드 구조

- `src/api/` — API 호출 함수와 응답 타입. 타입 이름·필드는 백엔드 DTO와 맞춘다. 컴포넌트에서 `fetch`를 직접 쓰지 않는다.
- `src/quiz/` — 풀이 흐름 상태 (현재 문제, 제출 여부, 자기 채점 결과, 점수 집계)를 다루는 reducer. React를 import하지 않는 순수 TypeScript. 채점·정답 판단 같은 도메인 규칙은 프론트에 두지 않는다 (서버 책임).
- `src/components/` — 재사용 UI 컴포넌트 (보기 목록, 유형별 답 입력칸 등).
- `src/routes/` — 화면 + `loader`/`action`. 에러 화면은 route의 `ErrorBoundary`.
- `src/seed-design/` — SEED Design CLI가 생성한 코드 (린트·포맷 제외). 직접 고치지 않고, 추가는 `npx @seed-design/cli@latest add ui:<이름>`. 화면은 SEED 컴포넌트를 먼저 쓰고, 없을 때만 CSS를 직접 작성한다. 당근 로고·이름·캐릭터, 당근 고유 컴포넌트 (`manner-temp` 등)는 쓰지 않는다.
- 서버 데이터는 `loader`, 화면 상태는 `useState`. 여러 값이 같이 바뀌는 상태 (풀이 진행)는 `useReducer` + `src/quiz/`의 reducer. 별도 상태 관리 라이브러리는 쓰지 않는다.

## 프론트엔드 코드 스타일

- 모르는 값은 `unknown`으로 받고 타입을 좁혀서 쓴다.
- 불변: state·props를 직접 수정하지 않는다 (spread, `map`, `filter`로 새 값을 만든다). 도메인 타입은 `readonly`.
- 컴포넌트는 props → 화면만 담당한다. 계산 로직은 `src/quiz/` 함수로 빼서 테스트한다.
- 문제 유형 분기는 union 타입 (`type: 'MULTIPLE_CHOICE' | 'SHORT_ANSWER' | 'ESSAY'`) + `switch` 한 곳에서 하고, `default`에서 `never` 검사로 빠진 유형이 없게 한다.
- 유형마다 값만 다르면 `switch` 대신 `Record<QuestionType, ...>`을 쓴다. 빠진 유형은 타입 검사에서 걸린다.
- 함수 컴포넌트 + hooks만 쓴다.

## 프론트엔드 테스트

- Vitest + React Testing Library. 테스트 파일은 대상 파일 옆에 `*.test.ts(x)`.
- 풀이 흐름 reducer (`src/quiz/`): 상태 전이마다 단위 테스트. TDD 대상.
- 컴포넌트: 사용자 관점으로 테스트한다 (`getByRole`, `getByLabelText`로 찾고 `user-event`로 조작). 내부 state나 CSS class는 검사하지 않는다. 핵심 상호작용 (답 선택·제출·결과 표시)만.
- API 호출은 `vi.mock`으로 대체한다.
