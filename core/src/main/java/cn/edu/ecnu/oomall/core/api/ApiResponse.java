package cn.edu.ecnu.oomall.core.api;

/** 所有对外 HTTP 接口使用的统一响应包装。 */
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }
}
