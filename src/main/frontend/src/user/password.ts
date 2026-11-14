// 새 비밀번호와 확인 칸이 같은지 (화면에서만 비교하고, 서버에는 새 비밀번호 하나만 보낸다)
export function passwordsMatch(newPassword: string, confirm: string): boolean {
  return newPassword === confirm;
}
