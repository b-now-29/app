// seed-design/ui/text-field.tsx가 process.env.NODE_ENV를 쓴다. 이 값은 Vite가 빌드 때 치환한다.
// @types/node를 켜면 브라우저 코드에서 Node API까지 타입 검사를 통과하므로 이 값만 선언한다.
declare const process: { readonly env: { readonly NODE_ENV: string } };
