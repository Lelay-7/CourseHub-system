package com.bugaoshan.common;
import com.bugaoshan.controller.ChatController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(assignableTypes=ChatController.class) @Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatExceptionHandler {
    @ExceptionHandler(ChatException.class) public ResponseEntity<Result<Void>> chat(ChatException e) {
        var response=ResponseEntity.status(e.status()); if(e.retryAfterSeconds()!=null) response.header("Retry-After",""+e.retryAfterSeconds());
        return response.body(Result.error(e.status(),e.getMessage()));
    }
    @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<Void>> business(BusinessException e) { return ResponseEntity.status(e.getCode()==401?401:403).body(Result.error(e.getCode()==401?401:403,e.getMessage())); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<Result<Void>> malformed(Exception e) { return ResponseEntity.badRequest().body(Result.error(400,"请求参数不正确")); }
}
