import { Text, VStack } from "@seed-design/react";
import { Form, Navigate, useActionData, useNavigation } from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import { TextField, TextFieldInput } from "seed-design/ui/text-field";
import NicknameText from "../components/NicknameText";
import type { MeActionResult } from "./meRoute";
import { useCurrentUser } from "./routeLoaderData";
import { useResetKeyOnSave } from "./useResetKeyOnSave";

type PasswordResult = Extract<MeActionResult, { intent: "password" }>;

// 비밀번호 폼의 결과를 kind로 나눠 문구를 정한다. 빠진 kind가 있으면 타입 검사에서 걸린다.
function PasswordMessage({ result }: { result: PasswordResult | undefined }) {
  if (result === undefined) {
    return null;
  }
  switch (result.kind) {
    case "mismatch":
      return (
        <Text role="alert" color="fg.critical">
          새 비밀번호가 서로 다릅니다.
        </Text>
      );
    case "error":
      return (
        <Text role="alert" color="fg.critical">
          {result.error}
        </Text>
      );
    case "saved":
      return (
        <Text color="fg.positive">
          비밀번호를 바꿨습니다. 다른 기기에서는 다시 로그인해야 합니다.
        </Text>
      );
    default: {
      const unreachable: never = result;
      return unreachable;
    }
  }
}

export default function MePage() {
  // 현재 사용자는 root loader가 이미 받아 둔 값을 쓴다 (같은 조회를 또 하지 않는다)
  const user = useCurrentUser();
  const actionData = useActionData<MeActionResult | undefined>();
  const submitting = useNavigation().state === "submitting";
  // 두 폼이 같은 action을 쓰므로, 결과를 intent로 나눠 각 폼은 자기 결과만 본다
  const nicknameResult =
    actionData?.intent === "nickname" ? actionData : undefined;
  const passwordResult =
    actionData?.intent === "password" ? actionData : undefined;
  const nicknameKey = useResetKeyOnSave(nicknameResult);
  const passwordKey = useResetKeyOnSave(passwordResult);

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
      <Form method="post" key={nicknameKey}>
        <VStack gap="x4">
          <input type="hidden" name="intent" value="nickname" />
          {/* 처음부터, 그리고 저장 성공 후 폼이 다시 그려질 때도 빈칸이다 (defaultValue를 주지 않는다) */}
          <TextField label="닉네임">
            <TextFieldInput name="nickname" autoComplete="nickname" />
          </TextField>
          {nicknameResult?.kind === "error" && (
            <Text role="alert" color="fg.critical">
              {nicknameResult.error}
            </Text>
          )}
          <ActionButton type="submit" disabled={submitting}>
            저장
          </ActionButton>
        </VStack>
      </Form>
      <Text as="h2" textStyle="t5Bold">
        비밀번호 변경
      </Text>
      {/* 성공하면 key가 바뀌어 세 칸이 비워지고, 실패하면 입력이 남는다 */}
      <Form method="post" key={`password-${passwordKey}`}>
        <VStack gap="x4">
          <input type="hidden" name="intent" value="password" />
          <TextField label="현재 비밀번호">
            <TextFieldInput
              name="currentPassword"
              type="password"
              autoComplete="current-password"
            />
          </TextField>
          <TextField label="새 비밀번호">
            <TextFieldInput
              name="newPassword"
              type="password"
              autoComplete="new-password"
            />
          </TextField>
          <TextField label="새 비밀번호 확인">
            <TextFieldInput
              name="confirmPassword"
              type="password"
              autoComplete="new-password"
            />
          </TextField>
          <PasswordMessage result={passwordResult} />
          <ActionButton type="submit" disabled={submitting}>
            변경
          </ActionButton>
        </VStack>
      </Form>
    </VStack>
  );
}
