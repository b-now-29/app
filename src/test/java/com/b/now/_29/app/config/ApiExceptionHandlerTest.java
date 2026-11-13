package com.b.now._29.app.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.support.WithAppSecurity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 400 문구 (D14). 특정 API에 묶이지 않도록 테스트 전용 컨트롤러로 확인한다.
 *
 * <ul>
 *   <li>가입 이외의 Bean Validation 실패: {@code 입력값이 올바르지 않습니다. (항목: a, b)} — 실패한 필드 경로를 정렬하고 중복 없이, 입력값은
 *       넣지 않는다
 *   <li>읽을 수 없는 본문 (깨진 JSON, 빈 본문, 없는 enum 값, 타입 불일치): {@code 요청 형식이 올바르지 않습니다.}
 *   <li>가입 검증 문구는 그대로다 ({@code UserControllerTest}가 확인한다)
 * </ul>
 */
@WebMvcTest(ApiExceptionHandlerTest.ProbeController.class)
// 테스트 클래스 안의 클래스는 컴포넌트 스캔에서 빠지므로 (TestTypeExcludeFilter) 직접 등록한다
@Import(ApiExceptionHandlerTest.ProbeController.class)
@WithAppSecurity
class ApiExceptionHandlerTest {

    private static final String INVALID_PREFIX = "입력값이 올바르지 않습니다. (항목: ";
    private static final String UNREADABLE = "요청 형식이 올바르지 않습니다.";

    @Autowired
    MockMvc mvc;

    enum Color {
        RED,
        BLUE
    }

    record ProbeItem(@NotBlank String content) {}

    record ProbeRequest(
            @NotBlank String name,
            @Size(max = 3) @Pattern(regexp = "[a-z]*") String code,
            Color color,
            // Jackson 3는 기본형 필드가 빠지면 본문을 읽지 못하므로 (FAIL_ON_NULL_FOR_PRIMITIVES) 래퍼 타입으로 둔다
            Integer count,
            @NotNull @Valid List<ProbeItem> items) {}

    @RestController
    static class ProbeController {

        @PostMapping("/api/v1/test-probe")
        String probe(@Valid @RequestBody ProbeRequest request) {
            return "ok";
        }
    }

    @Test
    @DisplayName("검증을 통과하면 정상 응답한다 (테스트 컨트롤러 확인)")
    void validRequestPasses() throws Exception {
        post("{\"name\": \"a\", \"code\": \"abc\", \"color\": \"RED\", \"count\": 1, \"items\": []}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("필드 하나가 실패하면 그 필드 이름을 담는다 (D14)")
    void namesSingleFailedField() throws Exception {
        expectInvalid(post("{\"name\": \" \", \"items\": []}"), INVALID_PREFIX + "name)");
    }

    @Test
    @DisplayName("여러 필드가 실패하면 중첩 경로를 포함해 정렬한 순서로 담는다 (D14)")
    void namesFailedFieldsSorted() throws Exception {
        expectInvalid(
                post("{\"name\": \"\", \"code\": \"abcd\", \"items\": [{\"content\": \"ok\"}, {\"content\": \" \"}]}"),
                INVALID_PREFIX + "code, items[1].content, name)");
    }

    @Test
    @DisplayName("누락된 필수 목록도 필드 이름으로 알려 준다 (D14)")
    void namesMissingList() throws Exception {
        expectInvalid(post("{\"name\": \"a\"}"), INVALID_PREFIX + "items)");
    }

    @Test
    @DisplayName("한 필드가 여러 제약을 어겨도 이름은 한 번만 담는다 (D14)")
    void listsFieldOnce() throws Exception {
        // code "ABCD"는 @Size와 @Pattern을 함께 어긴다
        expectInvalid(post("{\"name\": \" \", \"code\": \"ABCD\", \"items\": []}"), INVALID_PREFIX + "code, name)");
    }

    @Test
    @DisplayName("detail에 입력값을 넣지 않는다 (D14)")
    void doesNotEchoInput() throws Exception {
        String secret = "SECRET-INPUT-VALUE";

        post("{\"name\": \"a\", \"code\": \"" + secret + "\", \"items\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_PREFIX + "code)"))
                .andExpect(content().string(not(containsString(secret))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"name\": ",
                "",
                "not json",
                "{\"name\": \"a\", \"color\": \"GREEN\", \"items\": []}",
                "{\"name\": \"a\", \"count\": \"many\", \"items\": []}"
            })
    @DisplayName("깨진 JSON, 빈 본문, 없는 enum 값, 타입 불일치는 요청 형식 문구다 (D14)")
    void unreadableBody(String body) throws Exception {
        post(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(UNREADABLE))
                .andExpect(content().string(not(containsString("GREEN"))))
                .andExpect(content().string(not(containsString("many"))));
    }

    private ResultActions post(String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/test-probe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static void expectInvalid(ResultActions actions, String detail) throws Exception {
        actions.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(detail));
    }
}
