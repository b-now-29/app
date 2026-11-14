package com.b.now._29.app.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.b.now._29.app.user.TokenSubject;
import com.b.now._29.app.user.TokenUserVerifier;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * {@link WithAppSecurity}가 넣는 {@code TokenUserVerifier} mock에 테스트마다 {@code isCurrent(any()) = true}를 정해, 회원 토큰이 기존처럼
 * 인증되게 한다.
 *
 * <p>인증 필터가 토큰의 회원이 DB에 있고 토큰 버전이 지금 버전과 같은지 {@code TokenUserVerifier.isCurrent}로 확인하므로, 이 기본값이 없으면 mock의
 * 기본값 {@code false} 때문에 모든 회원 토큰이 비회원이 된다. 회원 없음이나 무효가 된 토큰을 확인하는 테스트는
 * {@code given(tokenUserVerifier.isCurrent(subject)).willReturn(false)}로 덮어쓴다.
 *
 * <p>{@code @MockitoBean} mock은 테스트가 끝날 때 초기화되므로 테스트마다 다시 정한다. Spring의 mock 초기화 리스너보다 뒤에 돈다.
 */
public class ExistingUsersByDefault extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        TokenUserVerifier tokenUserVerifier =
                testContext.getApplicationContext().getBean(TokenUserVerifier.class);
        given(tokenUserVerifier.isCurrent(any(TokenSubject.class))).willReturn(true);
    }
}
