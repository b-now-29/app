import AuthForm from "../components/AuthForm";

export default function SignUpPage() {
  return (
    <AuthForm
      title="회원가입"
      submitLabel="회원가입"
      passwordAutoComplete="new-password"
      altLink={{ to: "/login", label: "로그인" }}
    />
  );
}
