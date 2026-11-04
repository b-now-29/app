import { Text, VStack } from "@seed-design/react";
import { Callout } from "seed-design/ui/callout";

export default function ErrorPage() {
  return (
    <VStack role="alert" gap="x4">
      <Text as="h1" textStyle="screenTitle">
        문제가 발생했습니다
      </Text>
      <Callout tone="critical" description="잠시 후 다시 시도해 주세요." />
    </VStack>
  );
}
