import { HStack } from "@seed-design/react";
import { ActionButton } from "seed-design/ui/action-button";

type SelfGradeButtonsProps = {
  readonly onSelect: (correct: boolean) => void;
  // 저장 중에 다시 누르면 기록이 중복 저장되므로 두 버튼을 모두 막는다
  readonly disabled?: boolean;
};

export default function SelfGradeButtons({
  onSelect,
  disabled = false,
}: SelfGradeButtonsProps) {
  // 제출을 더블클릭하면 두 번째 클릭이 같은 자리의 이 버튼에 걸려 고르지 않은 점수가 들어간다 (D5).
  // detail은 연속 클릭 횟수이고 키보드 입력은 0이라 키보드 조작은 막히지 않는다.
  const handle = (correct: boolean) => (event: { detail: number }) => {
    if (event.detail > 1) return;
    onSelect(correct);
  };
  return (
    <HStack gap="x2">
      <ActionButton
        variant="neutralSolid"
        disabled={disabled}
        onClick={handle(true)}
      >
        맞았어요
      </ActionButton>
      <ActionButton
        variant="neutralWeak"
        disabled={disabled}
        onClick={handle(false)}
      >
        틀렸어요
      </ActionButton>
    </HStack>
  );
}
