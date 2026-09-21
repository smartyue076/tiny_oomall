package cn.edu.ecnu.oomall.core.exception;

import org.springframework.http.HttpStatus;

/** 可预期业务失败；由统一异常处理器转换为稳定的 API 错误响应。 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
