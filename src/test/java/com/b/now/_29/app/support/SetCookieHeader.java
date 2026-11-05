package com.b.now._29.app.support;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** 속성 순서에 묶이지 않도록 {@code Set-Cookie} 헤더를 나눠서 검사한다. */
public record SetCookieHeader(String name, String value, List<String> attributes) {

    public static SetCookieHeader from(MvcResult result) {
        return parse(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    public static SetCookieHeader parse(String header) {
        List<String> parts = Arrays.stream(header.split(";")).map(String::strip).toList();
        String nameAndValue = parts.getFirst();
        int separator = nameAndValue.indexOf('=');
        return new SetCookieHeader(
                nameAndValue.substring(0, separator),
                nameAndValue.substring(separator + 1),
                parts.subList(1, parts.size()));
    }
}
