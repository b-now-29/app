import AuthForm from "../components/AuthForm";

export default function LoginPage() {
  return (
    <AuthForm
      title="로그인"
      submitLabel="로그인"
      passwordAutoComplete="current-password"
      altLink={{ to: "/signup", label: "회원가입" }}
    />
  );
}
