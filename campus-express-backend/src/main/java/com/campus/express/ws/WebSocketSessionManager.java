package com.campus.express.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话管理器：维护 用户ID -> 在线会话集合 的映射
 * 提供按用户推送 JSON 消息的能力（新留言 / 新通知产生时调用，用户离线时静默忽略）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionManager {

    private final ObjectMapper objectMapper;

    /** 用户ID -> 该用户的在线连接集合（同一用户可多端在线） */
    private final Map<Integer, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** 注册会话（连接建立时调用） */
    public void register(Integer userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    /** 注销会话（连接关闭或传输异常时调用） */
    public void unregister(Integer userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
    }

    /**
     * 向指定用户推送一条类型信号（如 new_message / new_notification）
     * 推送失败仅记日志，不影响业务主流程
     *
     * @param userId 接收用户 ID
     * @param type   推送类型标识
     */
    public void sendToUser(Integer userId, String type) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload = buildPayload(type);
        if (payload == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendQuietly(session, payload);
        }
    }

    /**
     * 向所有在线会话广播一条类型信号（如 new_order：新订单发布时需求大厅实时刷新）
     * 推送失败仅记日志，不影响业务主流程
     *
     * @param type 推送类型标识
     */
    public void broadcast(String type) {
        if (userSessions.isEmpty()) {
            return;
        }
        String payload = buildPayload(type);
        if (payload == null) {
            return;
        }
        for (Set<WebSocketSession> sessions : userSessions.values()) {
            for (WebSocketSession session : sessions) {
                sendQuietly(session, payload);
            }
        }
    }

    /** 序列化推送负载；失败记日志返回 null */
    private String buildPayload(String type) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type));
        } catch (Exception e) {
            log.warn("WebSocket 消息序列化失败: type={}", type, e);
            return null;
        }
    }

    /** 单会话发送；连接已断开时静默忽略 */
    private void sendQuietly(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.debug("WebSocket 推送失败（连接可能已断开）: {}", session.getId());
        }
    }
}
