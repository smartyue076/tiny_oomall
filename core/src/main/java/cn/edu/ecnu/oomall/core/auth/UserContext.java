package cn.edu.ecnu.oomall.core.auth;

/**
 * 为一次 HTTP 请求保存当前认证用户。
 *
 * 请求过滤器必须在请求结束时调用 clear()，避免 Tomcat 线程复用时泄露身份
 */
public final class UserContext {
    private static final ThreadLocal<UserToken> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserToken token) {
        HOLDER.set(token);
    }

    public static UserToken require() {
        UserToken token = HOLDER.get();
        if (token == null) {
            throw new IllegalStateException("unauthenticated");
        }
        return token;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
