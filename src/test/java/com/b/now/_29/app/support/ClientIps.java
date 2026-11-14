package com.b.now._29.app.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 테스트 요청의 클라이언트 IP. 로그인·가입 제한 횟수는 앱 메모리에 남고 테스트 클래스끼리 Spring 컨텍스트를 공유하므로, 횟수가 섞이지 않도록 요청마다 다른
 * IP를 쓴다 (docs/features/login-hardening.md D2).
 *
 * <p>Tomcat {@code RemoteIpValve}가 프록시로 믿는 사설 대역 (10/8, 172.16/12, 192.168/16, 127/8 등)과 겹치지 않도록 벤치마크용 대역
 * 198.18.0.0/16을 쓴다.
 */
public final class ClientIps {

    private static final AtomicInteger NEXT =
            new AtomicInteger(ThreadLocalRandom.current().nextInt(1 << 16));

    private ClientIps() {}

    /** 이 JVM 안에서 겹치지 않는 IP (65,536개까지). */
    public static String unique() {
        int n = NEXT.getAndIncrement() & 0xFFFF;
        return "198.18." + (n >> 8) + "." + (n & 0xFF);
    }

    /** MockMvc 요청의 {@code getRemoteAddr()}를 정한다. */
    public static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** 다른 테스트와 겹치지 않는 IP에서 보낸 요청으로 만든다. */
    public static RequestPostProcessor uniqueIp() {
        return from(unique());
    }
}
