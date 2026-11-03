import { Link, Outlet } from "react-router";

export default function RootLayout() {
  return (
    <>
      <nav>
        <Link to="/">홈</Link> | <Link to="/about">소개</Link>
      </nav>
      <main>
        <Outlet /> {/* 여기에 주소에 맞는 자식 화면이 들어간다 */}
      </main>
    </>
  );
}
