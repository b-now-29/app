import {
  Field,
  TextField,
  useTextFieldWithGraphemes,
} from "@seed-design/react";

// 서버 TextAnswerRequest의 @Size(max = 5000)와 숫자는 같지만 단위가 다르다.
// 여기는 눈에 보이는 글자(그래핌), 서버는 UTF-16 코드 단위로 센다.
// 이모지가 많으면 이 제한 안에서도 서버가 400을 줄 수 있고, 그때는 제출 실패 문구로 처리한다 (D7).
const MAX_GRAPHEME_COUNT = 5000;

// PC 폭(RootLayout의 640px)에서 한글 약 1500자가 들어가는 높이 (D12).
// 높이는 폭과 줄바꿈으로 정해지므로, 모바일은 더 적은 글자에서 이 높이에 닿는다.
const MAX_HEIGHT = "860px";

type TextAnswerInputProps = {
  readonly value: string;
  readonly disabled: boolean; // 제출 중이거나 제출한 뒤에는 답을 바꿀 수 없다
  readonly onChange: (text: string) => void;
};

// 여러 줄 입력칸. Enter는 줄바꿈만 하고 제출은 버튼으로만 한다.
// src/seed-design/의 TextField는 글자 수를 아래쪽에만 그리므로, 라벨과 같은 줄에 두기 위해 직접 조립한다 (D14).
export default function TextAnswerInput({
  value,
  disabled,
  onChange,
}: TextAnswerInputProps) {
  const { textFieldRootProps, counterProps } = useTextFieldWithGraphemes({
    value,
    maxGraphemeCount: MAX_GRAPHEME_COUNT,
    // SEED의 maxGraphemeCount는 표시만 하고 값을 자르지 않으므로 slicedValue를 쓴다 (D6)
    onValueChange: ({ slicedValue }) => onChange(slicedValue),
  });

  return (
    <Field.Root disabled={disabled}>
      <Field.Header>
        <Field.Label>내 답</Field.Label>
        {/* 라벨 안에 넣으면 글자 수가 입력칸 이름에 섞이므로 라벨 옆에 둔다 */}
        <Field.CharacterCount {...counterProps} />
      </Field.Header>
      <TextField.Root {...textFieldRootProps}>
        <TextField.Textarea
          placeholder="답을 입력하세요"
          // SEED의 자동 높이 코드가 style.overflow를 저장했다가 되돌린다.
          // overflowY만 주면 저장 값이 비어 스크롤 설정이 지워지므로 overflow로 준다 (D13).
          style={{ maxHeight: MAX_HEIGHT, overflow: "auto" }}
        />
      </TextField.Root>
    </Field.Root>
  );
}
