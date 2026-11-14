package com.b.now._29.app.config;

import com.b.now._29.app.quiz.category.CategoryNotEmptyException;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.DuplicateCategorySlugException;
import com.b.now._29.app.quiz.question.InvalidChoiceException;
import com.b.now._29.app.quiz.question.InvalidQuestionException;
import com.b.now._29.app.quiz.question.QuestionNotFoundException;
import com.b.now._29.app.quiz.question.QuestionTypeChangeNotAllowedException;
import com.b.now._29.app.quiz.question.UnsupportedAnswerTypeException;
import com.b.now._29.app.user.DuplicateEmailException;
import com.b.now._29.app.user.InvalidCredentialsException;
import com.b.now._29.app.user.InvalidNicknameException;
import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.SelfDemotionNotAllowedException;
import com.b.now._29.app.user.TooManyAttemptsException;
import com.b.now._29.app.user.UserNotFoundException;
import com.b.now._29.app.user.WrongCurrentPasswordException;
import com.b.now._29.app.user.admin.AdminUserNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 예외를 ProblemDetail로 변환한다.
 *
 * <p>요청 형식 오류나 없는 경로 ({@code NoResourceFoundException}) 같은 MVC 예외는 부모 클래스가 처리한다.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String EMAIL_MESSAGE = "이메일 형식이 올바르지 않거나 255자를 넘습니다.";
    private static final String PASSWORD_MESSAGE = "비밀번호는 8자 이상, UTF-8 72바이트 이하여야 합니다.";

    /** 가입 검증 실패 필드 이름 → 문구. 응답에는 이 순서로 이어 붙인다. */
    private static final Map<String, String> SIGN_UP_FIELD_MESSAGES = new LinkedHashMap<>();

    static {
        SIGN_UP_FIELD_MESSAGES.put("email", EMAIL_MESSAGE);
        SIGN_UP_FIELD_MESSAGES.put("password", PASSWORD_MESSAGE);
        SIGN_UP_FIELD_MESSAGES.put("nickname", Nickname.RULE_MESSAGE);
    }

    @ExceptionHandler({
        CategoryNotFoundException.class,
        QuestionNotFoundException.class,
        AdminUserNotFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({
        InvalidChoiceException.class,
        UnsupportedAnswerTypeException.class,
        InvalidQuestionException.class,
        InvalidNicknameException.class,
        WrongCurrentPasswordException.class
    })
    public ProblemDetail handleBadRequest(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({InvalidCredentialsException.class, UserNotFoundException.class})
    public ProblemDetail handleUnauthorized(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({
        DuplicateEmailException.class,
        DuplicateCategorySlugException.class,
        CategoryNotEmptyException.class,
        QuestionTypeChangeNotAllowedException.class,
        SelfDemotionNotAllowedException.class
    })
    public ProblemDetail handleConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyAttemptsException exception) {
        Duration retryAfter = exception.retryAfter();
        long seconds = retryAfter.getSeconds() + (retryAfter.getNano() > 0 ? 1 : 0);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, seconds)))
                .body(problem);
    }

    /**
     * 가입 검증 실패는 실패한 필드를 알려 주는 한국어 문구로 응답한다 (D16). 그 밖의 검증 실패는 실패한 필드 경로를 정렬해 알려 준다 (D14).
     * 어느 쪽이든 입력값은 detail에 넣지 않는다.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Set<String> failed = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> messages = new ArrayList<>();
        SIGN_UP_FIELD_MESSAGES.forEach((field, message) -> {
            if (failed.contains(field)) {
                messages.add(message);
            }
        });
        boolean onlySignUpFields = SIGN_UP_FIELD_MESSAGES.keySet().containsAll(failed);
        if (messages.isEmpty()
                || !onlySignUpFields
                || !ex.getBindingResult().getGlobalErrors().isEmpty()) {
            String detail =
                    failed.isEmpty() ? "입력값이 올바르지 않습니다." : "입력값이 올바르지 않습니다. (항목: " + String.join(", ", failed) + ")";
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
            return handleExceptionInternal(ex, problem, headers, status, request);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, String.join(" ", messages));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** 깨진 JSON, 없는 enum 값, 타입 불일치. 파서 메시지에 입력값이 섞이므로 고정 문구만 응답한다 (D14). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** 내부 정보가 새지 않도록 예외 메시지는 응답에 넣지 않고 로그에만 남긴다. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        logger.error("처리하지 못한 예외", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }
}
