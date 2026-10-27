package com.campus.express.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器：统一维护 create_time / update_time
 *
 * <p>为什么需要：实体从库里查出后 update_time 非空，{@code updateById} 会把这个旧值一并写回 SET 子句；
 * 而 MySQL 的 {@code ON UPDATE CURRENT_TIMESTAMP} 只在「该列未显式赋值」时才生效，
 * 结果就是状态流转后 update_time 不前进。评价有效期（完成后 7 天）与接力超时都依赖这个时间，必须准确。</p>
 *
 * <p>更新时使用无条件覆盖而非 {@code strictUpdateFill}：后者只在字段为 null 时填充，
 * 而这里恰恰需要在旧值存在时把它刷新为当前时间。</p>
 */
@Component
public class MybatisFillConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 无条件覆盖：把从库里读出的旧 update_time 刷新为当前时间
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
