import { Box, List, RadioGroup, Text } from "@seed-design/react";
import { ListRadioItem } from "seed-design/ui/list";
import type { ChoiceResponse } from "../api/questions";

type ChoiceListProps = {
  readonly labelledBy: string; // 이 보기 묶음의 제목(문제 내용)을 가리키는 요소 id
  readonly choices: readonly ChoiceResponse[];
  readonly selectedChoiceId: number | null;
  readonly correctChoiceId: number | null; // 채점 전에는 null
  readonly disabled: boolean;
  readonly onSelect: (choiceId: number) => void;
};

export default function ChoiceList({
  labelledBy,
  choices,
  selectedChoiceId,
  correctChoiceId,
  disabled,
  onSelect,
}: ChoiceListProps) {
  return (
    // ListRadioItem에 자체 좌우 여백이 있어서, 그만큼 밖으로 당겨 글자를 문제와 맞춘다
    <Box bleedX="spacingX.globalGutter">
      <List.RadioRoot
        aria-labelledby={labelledBy}
        // undefined를 넘기면 SEED가 선택값을 자체 보관하는 방식으로 바뀌어 reducer와 어긋날 수 있다.
        // 그래서 고르지 않았을 때도 어떤 보기와도 일치하지 않는 빈 문자열을 넘긴다.
        value={selectedChoiceId === null ? "" : String(selectedChoiceId)}
        onValueChange={(value) => onSelect(Number(value))}
        disabled={disabled}
      >
        {choices.map((choice) => (
          <ListRadioItem
            key={choice.id}
            value={String(choice.id)}
            title={choice.content}
            prefix={
              <RadioGroup.ItemControl>
                <RadioGroup.ItemIndicator />
              </RadioGroup.ItemControl>
            }
            suffix={
              choice.id === correctChoiceId && (
                <Text color="fg.positive" textStyle="t4Bold">
                  정답
                </Text>
              )
            }
          />
        ))}
      </List.RadioRoot>
    </Box>
  );
}
