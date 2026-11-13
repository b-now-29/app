// 회원을 화면에 보여 주는 이름 "닉네임#번호" (docs/features/nickname.md R2, R9, R11).
// 메뉴, 내 정보, 관리자 회원 목록이 같이 쓰므로 한 곳에 둔다.
export function displayName(member: {
  readonly nickname: string;
  readonly tag: number;
}): string {
  return `${member.nickname}#${member.tag}`;
}
