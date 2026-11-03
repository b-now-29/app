import { useLoaderData } from "react-router";

export default function HomePage() {
  const data = useLoaderData() as { status: string }; // router.tsx의 loader 결과
  return <h1>홈 — 서버 상태: {data.status}</h1>;
}
