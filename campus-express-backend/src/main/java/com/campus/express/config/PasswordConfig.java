package com.campus.express.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置
 * 仅引入 spring-security-crypto 的 BCrypt 加盐哈希算法（规则：密码禁止明文存储）
 * 不启用 Spring Security 过滤链，登录鉴权由 JwtInterceptor 完成
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 强度 10：每次哈希自动生成随机盐，相同密码两次加密结果不同
        return new BCryptPasswordEncoder();
    }
}
