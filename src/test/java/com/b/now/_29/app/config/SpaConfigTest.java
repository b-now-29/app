package com.b.now._29.app.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.controller.PingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SpaConfig 동작 확인. 테스트용 index.html은 src/test/resources/static/index.html 이며, 본문에 "SPA_INDEX"가 들어 있다.
 */
@WebMvcTest(PingController.class)
@Import(SpaConfig.class)
class SpaConfigTest {

    private static final String INDEX_MARKER = "SPA_INDEX";

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

    @Test
    @DisplayName("기존 API는 SpaConfig 영향 없이 그대로 동작한다")
    void existingApiStillWorks() throws Exception {
        mvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
