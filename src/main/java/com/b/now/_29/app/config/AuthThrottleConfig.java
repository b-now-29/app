package com.b.now._29.app.config;

import com.b.now._29.app.user.AttemptLimiter;
import com.b.now._29.app.user.AuthLimiters;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthThrottleConfig {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration SIGN_UP_WINDOW = Duration.ofHours(1);

    @Bean
    public AuthLimiters authLimiters() {
        Clock clock = Clock.systemUTC();
        return new AuthLimiters(
                new AttemptLimiter(5, LOGIN_WINDOW, clock),
                new AttemptLimiter(50, LOGIN_WINDOW, clock),
                new AttemptLimiter(10, SIGN_UP_WINDOW, clock));
    }
}
