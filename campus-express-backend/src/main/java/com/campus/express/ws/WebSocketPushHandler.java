package com.campus.express.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 推送端点处理器：连接建立/关闭/异常时在会话管理器注册或注销
 * 本端点仅服务端向下推送（新留言/新通知信号），客户端无需上行业务消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPushHandler extends TextWebSocketHandler {

    /** 握手阶段写入会话属性的用户 ID 键 */
    public static final String ATTR_USER_ID = "userId";

    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get(ATTR_USER_ID);
        if (userId == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            return;
        }
        sessionManager.register(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Integer userId = (Integer) session.getAttributes().get(ATTR_USER_ID);
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Integer userId = (Integer) session.getAttributes().get(ATTR_USER_ID);
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }
}
