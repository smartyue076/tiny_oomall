package cn.edu.ecnu.oomall.shop.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import cn.edu.ecnu.oomall.core.auth.UserContext;
import cn.edu.ecnu.oomall.shop.service.MerchantService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

/** 从请求头解析 Session，并严格在请求结束时清理线程上下文。 */
@Component
class SessionFilter implements Filter {
    private final MerchantService service;

    SessionFilter(MerchantService service) {
        this.service = service;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            String id = ((HttpServletRequest) request).getHeader("X-Session-Id");
            if (id != null && !id.isBlank()) {
                UserContext.set(service.authenticate(id));
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
