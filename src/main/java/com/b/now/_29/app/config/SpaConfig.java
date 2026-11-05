package com.b.now._29.app.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * React(SPA) 빌드 결과(classpath:/static)를 서빙한다.
 *
 * <p>요청한 파일이 있으면 그 파일을, 없으면 index.html을 돌려줘서 React Router가 {@code /users/1} 같은
 * 경로를 처리하게 한다. {@code /api/**}는 컨트롤러가 먼저 처리하고, 매핑이 없는 {@code /api} 경로는
 * index.html이 아니라 404가 된다.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 해시가 붙은 파일이라 오래 캐시해도 된다 (D18). Spring Security 기본 헤더는 Cache-Control이 이미 있으면 덮어쓰지 않는다
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    static class SpaResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            Resource file = location.createRelative(path);
            if (file.exists() && file.isReadable()) {
                return file;
            }
            if (path.startsWith("api/") || path.contains(".")) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() ? index : null;
        }
    }
}
