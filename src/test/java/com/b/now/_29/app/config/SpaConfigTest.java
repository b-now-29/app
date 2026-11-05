package com.b.now._29.app.config;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.controller.PingController;
import com.b.now._29.app.support.WithAppSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SpaConfig 동작 확인. 테스트용 index.html은 src/test/resources/static/index.html 이며, 본문에 "SPA_INDEX"가 들어 있다.
 */
@WebMvcTest(PingController.class)
@Import(SpaConfig.class)
@WithAppSecurity
class SpaConfigTest {

    private static final String INDEX_MARKER = "SPA_INDEX";
    private static final String ASSET_MARKER = "SPA_ASSET";
    // Spring Security 기본값 "no-cache, no-store, max-age=0, must-revalidate"에도 max-age가 있어 0보다 큰 값만 인정한다
    private static final String POSITIVE_MAX_AGE = ".*max-age=[1-9].*";

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("없는 화면 경로는 index.html을 돌려준다 (React Router가 처리)")
    void unknownPageReturnsIndex() throws Exception {
        mvc.perform(get("/some/page"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(INDEX_MARKER)));
    }

    @Test
    @DisplayName("실제로 있는 파일은 그 파일을 그대로 돌려준다")
    void existingFileIsServed() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(INDEX_MARKER)));
    }

    @Test
    @DisplayName("매핑이 없는 /api 경로는 index.html이 아니라 404")
    void unknownApiReturns404() throws Exception {
        mvc.perform(get("/api/nope")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("확장자가 있는 없는 파일은 index.html이 아니라 404")
    void missingFileReturns404() throws Exception {
        mvc.perform(get("/missing.js")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/nope", "/missing.js"})
    @DisplayName("index.html을 돌려주지 않는 경로의 404 본문은 ProblemDetail이다")
    void notFoundBodyIsProblemDetail(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("/assets/ 아래 파일은 max-age로 캐시하고 no-store를 주지 않는다 (D18)")
    void assetsAreCached() throws Exception {
        mvc.perform(get("/assets/app-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ASSET_MARKER)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, matchesPattern(POSITIVE_MAX_AGE)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(containsString("no-store"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/some/page", "/index.html"})
    @DisplayName("SPA 경로 응답은 캐시되지 않는다 (D18)")
    void spaResponsesAreNotCached(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CACHE_CONTROL,
                                anyOf(containsString("no-store"), containsString("no-cache"))))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(matchesPattern(POSITIVE_MAX_AGE))));
    }

    @Test
    @DisplayName("기존 API는 SpaConfig 영향 없이 그대로 동작한다")
    void existingApiStillWorks() throws Exception {
        mvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
