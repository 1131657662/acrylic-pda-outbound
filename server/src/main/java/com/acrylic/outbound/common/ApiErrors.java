package com.acrylic.outbound.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    private final ApiResponses responses;
    public ApiErrors(ApiResponses responses) { this.responses = responses; }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> business(BusinessException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status())
            .body(responses.body(error.code(), error.getMessage(), null, request));
    }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<?> invalid(Exception ignored, HttpServletRequest request) {
        return business(new BusinessException(400, "INVALID_REQUEST", "请求字段缺失或格式错误"), request);
    }

    /**
     * Refusals of a request that cannot be parsed write without reading the database: the instance
     * may be unable to serve anything at all while it answers.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void tooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        responses.writeWithoutState(request, response, 413, "FILE_TOO_LARGE", "文件不得超过2MiB");
    }

    /**
     * The servlet container enforces the part and request limits while it parses, and the framework
     * reports that parse failure. A size limit stays a 413; any other parse failure is the client's
     * malformed request; neither path reads the database.
     */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public void multipart(Exception error, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (sizeLimited(error)) {
            responses.writeWithoutState(request, response, 413, "FILE_TOO_LARGE", "文件不得超过2MiB");
        } else {
            responses.writeWithoutState(request, response, 400, "INVALID_REQUEST", "请求字段缺失或格式错误");
        }
    }

    private static boolean sizeLimited(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof MaxUploadSizeExceededException) return true;
            if (current.getClass().getSimpleName().endsWith("SizeLimitExceededException")) return true;
        }
        return false;
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> missing(HttpServletRequest request) {
        return business(new BusinessException(404, "NOT_FOUND", "接口不存在"), request);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method(HttpServletRequest request) {
        return business(new BusinessException(405, "METHOD_NOT_ALLOWED", "此接口不接受该请求方法"), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> duplicate(HttpServletRequest request) {
        return business(new BusinessException(409, "WRITE_CONFLICT", "并发写入冲突，请核对后重试"), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception error, HttpServletRequest request) {
        LoggerFactory.getLogger(getClass()).error("Request {} failed", request.getAttribute("requestId"), error);
        return business(new BusinessException(500, "INTERNAL_ERROR", "操作失败，请凭请求编号联系管理员"), request);
    }
}
