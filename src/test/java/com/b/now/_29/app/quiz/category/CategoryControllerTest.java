package com.b.now._29.app.quiz.category;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@AutoConfigureRestDocs
class CategoryControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CategoryService categoryService;

    @Test
    @DisplayName("카테고리 목록을 JSON 배열로 반환한다")
    void getCategories() throws Exception {
        given(categoryService.getCategories())
                .willReturn(List.of(new CategoryResponse("java", "Java"), new CategoryResponse("spring", "Spring")));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("java"))
                .andExpect(jsonPath("$[0].name").value("Java"))
                .andExpect(jsonPath("$[1].slug").value("spring"))
                .andDo(document(
                        "categories-list",
                        responseFields(
                                fieldWithPath("[].slug").description("카테고리 식별자 (URL에 사용)"),
                                fieldWithPath("[].name").description("화면에 표시할 카테고리 이름"))));
    }
}
