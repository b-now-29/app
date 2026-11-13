import { TextField, TextFieldInput } from "seed-design/ui/text-field";
import AuthForm from "../components/AuthForm";

export default function SignUpPage() {
  return (
    <AuthForm
      title="회원가입"
      submitLabel="회원가입"
      passwordAutoComplete="new-password"
      altLink={{ to: "/login", label: "로그인" }}
    >
      <TextField label="닉네임">
        <TextFieldInput name="nickname" autoComplete="nickname" />
      </TextField>
    </AuthForm>
  );
}
