package com.campus.express.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 1. 注册 JWT 登录拦截器（放行注册/登录，其余全部鉴权）
 * 2. 跨域配置（前端 Vite 开发服务器 5173 端口）
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                // 放行无需登录的接口
                .excludePathPatterns(
                        "/api/user/register",       // 用户注册
                        "/api/user/register/code",  // 发送注册短信验证码（免登录）
                        "/api/user/login",          // 用户登录
                        "/api/user/password/reset/code", // 找回密码·发送短信验证码（免登录）
                        "/api/user/password/reset", // 找回密码·重置密码（免登录）
                        "/uploads/**",              // 上传的静态资源（头像图片，img 标签加载无 token）
                        "/doc.html",                // Knife4j 在线 API 文档页（免登录）
                        "/webjars/**",              // Knife4j 前端静态资源
                        "/v3/api-docs/**",          // OpenAPI 规范原始 JSON
                        "/swagger-ui/**",           // Swagger UI 资源
                        "/swagger-resources/**",    // Knife4j 资源索引
                        "/favicon.ico",
                        "/error"                    // 错误页
                );
    }

    /** CORS：允许前端开发服务器跨域访问 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 静态资源映射：/uploads/** -> 项目根目录 uploads/（头像等上传文件访问）
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadRoot = "file:" + System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadRoot);
    }

    /**
     * MyBatis-Plus 插件：
     * - 乐观锁插件：配合实体 @Version 实现抢单并发控制（规则 R-7）
     * - 分页插件：需求大厅分页加载（3.8）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页上限 100：即使请求传了超大 pageSize，也只会按 100 条查询，避免单次拉取百万行拖垮数据库
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
