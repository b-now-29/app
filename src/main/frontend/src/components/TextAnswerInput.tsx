import { TextField, TextFieldTextarea } from "seed-design/ui/text-field";

type TextAnswerInputProps = {
  readonly value: string;
  readonly disabled: boolean; // 제출 중이거나 제출한 뒤에는 답을 바꿀 수 없다
  readonly onChange: (text: string) => void;
};

// 여러 줄 입력칸. Enter는 줄바꿈만 하고 제출은 버튼으로만 한다 (D11)
export default function TextAnswerInput({
  value,
  disabled,
  onChange,
}: TextAnswerInputProps) {
  return (
    <TextField
      label="내 답"
      value={value}
      disabled={disabled}
      onValueChange={({ value: text }) => onChange(text)}
    >
      <TextFieldTextarea placeholder="답을 입력하세요" />
    </TextField>
  );
}
