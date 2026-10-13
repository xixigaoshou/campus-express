package com.campus.express.config;

import com.campus.express.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 登录鉴权拦截器
 * - 校验请求头 Authorization: Bearer <token>
 * - 未携带 / 无效 / 过期：返回 401 并中断请求
 * - 校验通过：将当前登录用户信息（userId/username/role）写入 request attribute，供 Controller/Service 使用
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /** 存放当前登录用户信息的 request attribute 名 */
    public static final String ATTR_USER_ID = "loginUserId";
    public static final String ATTR_USERNAME = "loginUsername";
    public static final String ATTR_ROLE = "loginRole";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行（CORS 跨域场景）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        String token = jwtUtil.extractToken(header);
        Claims claims = token == null ? null : jwtUtil.parseToken(token);

        if (claims == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 注意：Map.of 不允许 null 值，data 必须用可空 Map 承载
            Map<String, Object> body = new HashMap<>();
            body.put("code", 401);
            body.put("message", "未登录或登录已过期，请重新登录");
            body.put("data", null);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }

        // 校验通过：注入当前登录用户信息
        request.setAttribute(ATTR_USER_ID, jwtUtil.getUserId(claims));
        request.setAttribute(ATTR_USERNAME, claims.get("username", String.class));
        request.setAttribute(ATTR_ROLE, claims.get("role", Integer.class));
        return true;
    }
}
