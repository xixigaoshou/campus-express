package com.campus.express;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园快递代取平台 - 后端启动类
 * 运行方式：IDEA 中直接运行本类的 main 方法
 * @MapperScan 扫描所有 Mapper 接口（com.campus.express.mapper 包）
 * @EnableScheduling 启用定时任务（转单接力超时撤回、待收货 24h 自动确认收货、超时提醒/自动取消）
 */
@SpringBootApplication
@MapperScan("com.campus.express.mapper")
@EnableScheduling
public class CampusExpressApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusExpressApplication.class, args);
        System.out.println("""

                ==============================================
                  校园快递代取平台后端启动成功
                  接口前缀: http://localhost:8080/api
                ==============================================""");
    }
}
