import { Text, VStack } from "@seed-design/react";
import {
  Form,
  Navigate,
  useActionData,
  useNavigation,
  useRouteLoaderData,
} from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import { TextField, TextFieldInput } from "seed-design/ui/text-field";
import type { UserResponse } from "../api/users";
import NicknameText from "../components/NicknameText";
import type { ActionError } from "./actionError";
import type { NicknameSaved } from "./meRoute";
import { useResetKeyOnSave } from "./useResetKeyOnSave";

export default function MePage() {
  // 현재 사용자는 root loader가 이미 받아 둔 값을 쓴다 (같은 조회를 또 하지 않는다)
  const user = useRouteLoaderData("root") as UserResponse | null;
  const actionData = useActionData<ActionError | NicknameSaved | undefined>();
  const submitting = useNavigation().state === "submitting";
  const formKey = useResetKeyOnSave(actionData);

  // 비회원(또는 로그아웃 직후)이면 로그인 화면으로 보낸다
  if (user === null) {
    return <Navigate to="/login" replace />;
  }

  return (
    <VStack gap="x4">
      <Text as="h1" textStyle="screenTitle">
        내 정보
      </Text>
      <Text textStyle="t4Regular">{user.email}</Text>
      <Text textStyle="t4Bold">
        <NicknameText member={user} />
      </Text>
      <Form method="post" key={formKey}>
        <VStack gap="x4">
          {/* 처음부터, 그리고 저장 성공 후 폼이 다시 그려질 때도 빈칸이다 (defaultValue를 주지 않는다) */}
          <TextField label="닉네임">
            <TextFieldInput name="nickname" autoComplete="nickname" />
          </TextField>
          {actionData && "error" in actionData && (
            <Text role="alert" color="fg.critical">
              {actionData.error}
            </Text>
          )}
          <ActionButton type="submit" disabled={submitting}>
            저장
          </ActionButton>
        </VStack>
      </Form>
    </VStack>
  );
}
