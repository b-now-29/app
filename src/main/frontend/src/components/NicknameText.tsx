import { displayName } from "../user/displayName";

type Props = {
  readonly member: { readonly nickname: string; readonly tag: number };
};

// 닉네임 가운데의 연속 공백을 저장된 그대로 보여 주려고 white-space: pre-wrap을 건다 (ranking D11).
// 닉네임#번호가 보이는 화면은 모두 이 컴포넌트를 쓴다.
export default function NicknameText({ member }: Props) {
  return <span style={{ whiteSpace: "pre-wrap" }}>{displayName(member)}</span>;
}
