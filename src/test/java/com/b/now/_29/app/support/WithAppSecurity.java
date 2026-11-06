package com.b.now._29.app.support;

import com.b.now._29.app.config.JwtConfig;
import com.b.now._29.app.config.SecurityConfig;
import com.b.now._29.app.user.AuthCookies;
import com.b.now._29.app.user.TokenService;
import com.b.now._29.app.user.UserRoleService;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code @WebMvcTest}에 운영과 같은 보안 설정을 넣는다.
 *
 * <p>{@code @WebMvcTest}는 {@code @Configuration}을 스캔하지 않아서, 이 설정이 없으면 Spring Boot 기본 보안(모든 경로 인증, CSRF 켜짐)으로 실행된다.
 * 필터를 끄는 대신 실제 설정을 넣어 컨트롤러 테스트가 비회원 접근(R8)까지 함께 확인하게 한다.
 *
 * <p>{@code @EnableWebSecurity}: Spring Boot 4의 {@code @WebMvcTest}는 보안 자동 설정을 불러오지 않아 {@code SecurityConfig}에 필요한
 * {@code HttpSecurity} 빈이 없다.
 *
 * <p>{@code UserRoleService}: 관리자 경로 인가가 요청마다 DB의 role을 확인하는데 (D3), {@code @WebMvcTest}에는 Repository가 없으므로 mock으로
 * 넣는다. 기본값은 {@code isAdmin = false}(USER)다. 관리자로 요청하려면 테스트에서 {@code @Autowired UserRoleService}로 받아
 * {@code given(...isAdmin(id)).willReturn(true)}로 정한다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableWebSecurity
@Import({SecurityConfig.class, JwtConfig.class, TokenService.class, AuthCookies.class})
@MockitoBean(types = UserRoleService.class)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwts.SECRET)
public @interface WithAppSecurity {}
