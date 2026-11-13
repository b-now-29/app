import { Text, VStack } from "@seed-design/react";
import { useId } from "react";

type Option = { readonly value: string; readonly label: string };

type Props = {
  readonly label: string;
  readonly value: string;
  readonly options: readonly Option[];
  readonly onChange: (value: string) => void;
};

// SEED에 select가 없어서 기본 <select>를 쓴다 (D10)
export default function SelectField({
  label,
  value,
  options,
  onChange,
}: Props) {
  const id = useId();
  return (
    <VStack gap="x1">
      <label htmlFor={id}>
        <Text as="span" textStyle="t3Medium">
          {label}
        </Text>
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </VStack>
  );
}
