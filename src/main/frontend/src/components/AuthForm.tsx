import { Text, VStack } from "@seed-design/react";
import {
  Form,
  Link,
  Navigate,
  useActionData,
  useNavigation,
  useRouteLoaderData,
} from "react-router";
import { ActionButton } from "seed-design/ui/action-button";
import { TextField, TextFieldInput } from "seed-design/ui/text-field";
import type { UserResponse } from "../api/users";
import type { ActionError } from "../routes/authActions";

type Props = {
  readonly title: string;
  readonly submitLabel: string;
  readonly passwordAutoComplete: "current-password" | "new-password";
  readonly altLink: { readonly to: string; readonly label: string };
};

// 로그인·회원가입 화면이 같이 쓰는 폼. 제출하면 이 화면 route의 action이 호출된다.
export default function AuthForm({
  title,
  submitLabel,
  passwordAutoComplete,
  altLink,
}: Props) {
  const user = useRouteLoaderData("root") as UserResponse | null; // 루트 loader 결과
  const actionData = useActionData<ActionError>(); // action이 돌려준 에러
  const submitting = useNavigation().state === "submitting";

  // 이미 로그인한 사용자는 홈으로 보낸다
  if (user) {
    return <Navigate to="/" replace />;
  }

  return (
    <Form method="post">
      <VStack gap="x4">
        <Text as="h1" textStyle="screenTitle">
          {title}
        </Text>
        <TextField label="이메일">
          <TextFieldInput name="email" type="email" autoComplete="email" />
        </TextField>
        <TextField label="비밀번호">
          <TextFieldInput
            name="password"
            type="password"
            autoComplete={passwordAutoComplete}
          />
        </TextField>
        {actionData?.error && (
          <Text role="alert" color="fg.critical">
            {actionData.error}
          </Text>
        )}
        <ActionButton type="submit" disabled={submitting}>
          {submitLabel}
        </ActionButton>
        <ActionButton asChild variant="ghost" size="small">
          <Link to={altLink.to}>{altLink.label}</Link>
        </ActionButton>
      </VStack>
    </Form>
  );
}
