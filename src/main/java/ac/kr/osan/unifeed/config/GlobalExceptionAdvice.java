package ac.kr.osan.unifeed.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

// ############################################################################
// GlobalExceptionAdvice — 화면(Controller)에서 안 잡힌 "없는 리소스" 예외를 404로 바꾼다.
// ----------------------------------------------------------------------------
// 없는 게시글 주소(/posts/999999)로 들어오면 서비스가 던지는 NoSuchElementException이
// 그대로 올라가 500 에러가 났다. 서버 잘못이 아니라 "없는 주소"이므로 404가 맞다.
//
// API 컨트롤러들은 각자 try/catch로 404 JSON을 만들어 돌려주고 있어서 여기까지 오지 않는다
// (즉 이 핸들러는 그 외의 안 잡힌 경우에만 동작한다).
// ############################################################################
@ControllerAdvice
public class GlobalExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return "error"; // templates/error.html
    }

    // @Valid @RequestBody 검증 실패 — 기본 응답은 스프링의 ProblemDetail 형식이라
    // 화면 JS가 쓰는 data.message 와 안 맞는다. 첫 번째 오류 메시지를 그 형식으로 돌려준다.
    // (BindingResult를 직접 받는 폼 컨트롤러는 예외를 안 던지므로 여기로 오지 않는다)
    @Override
    protected org.springframework.http.ResponseEntity<Object> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException ex,
            org.springframework.http.HttpHeaders headers,
            org.springframework.http.HttpStatusCode status,
            org.springframework.web.context.request.WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");
        return org.springframework.http.ResponseEntity.badRequest()
                .body(java.util.Map.of("message", message));
    }
}
