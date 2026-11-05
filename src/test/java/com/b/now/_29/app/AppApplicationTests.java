package com.b.now._29.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.b.now._29.app.support.TestJwts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
class AppApplicationTests {
    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
