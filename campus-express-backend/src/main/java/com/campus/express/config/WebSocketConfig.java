package com.campus.express.config;

import com.campus.express.util.JwtUtil;
import com.campus.express.ws.WebSocketPushHandler;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 配置：注册推送端点 /ws（前端连接：ws://host/ws?token=xxx）
 * 握手时从 query 参数解析 JWT 令牌得到 userId 作为连接身份，
 * 与 HTTP 接口共用同一套 JWT 令牌体系；令牌缺失或无效则拒绝握手
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketPushHandler pushHandler;
    private final JwtUtil jwtUtil;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pushHandler, "/ws")
                .addInterceptors(new JwtHandshakeInterceptor(jwtUtil))
                .setAllowedOriginPatterns("*");
    }

    /** 握手拦截器：校验 query 中的 token 并注入 userId 到会话属性 */
    static class JwtHandshakeInterceptor implements HandshakeInterceptor {

        private final JwtUtil jwtUtil;

        JwtHandshakeInterceptor(JwtUtil jwtUtil) {
            this.jwtUtil = jwtUtil;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String token = extractQueryParam(request.getURI().getQuery(), "token");
            Claims claims = token == null ? null : jwtUtil.parseToken(token);
            if (claims == null) {
                return false; // 未登录（令牌缺失/无效/过期）拒绝握手
            }
            attributes.put(WebSocketPushHandler.ATTR_USER_ID, jwtUtil.getUserId(claims));
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        /** 从 query string 提取指定参数值，不存在返回 null */
        private String extractQueryParam(String query, String name) {
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0 && pair.substring(0, idx).equals(name)) {
                    return pair.substring(idx + 1);
                }
            }
            return null;
        }
    }
}
