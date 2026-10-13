package com.campus.express.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在线 API 文档配置（Knife4j / OpenAPI 3）
 *
 * <p>启动后端后访问 <a href="http://localhost:8080/doc.html">http://localhost:8080/doc.html</a>
 * 即可查看并直接调试全部 REST 接口；OpenAPI 规范原始 JSON 位于 /v3/api-docs。</p>
 *
 * <p>说明：文档页由 Knife4j 自动扫描 Controller 生成，无需逐个接口写注解；
 * 因此 /doc.html、/webjars/**、/v3/api-docs/** 需在 {@link WebMvcConfig} 中放行，
 * 否则会被 JwtInterceptor 拦截返回 401。</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusExpressOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("校园快递代取平台 API")
                .description("需求发布、抢单接单、履约状态机、转单接力、钱包结算、评价留言等接口。"
                        + "除注册/登录/找回密码外，其余接口均需在请求头携带 Authorization: Bearer <token>。")
                .version("1.0.0"));
    }
}