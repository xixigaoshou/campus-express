package com.campus.express.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.express.entity.Order;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动填充处理器单元测试：验证 update_time 会被旧值刷新，而不是原样写回
 */
class MybatisFillConfigTest {

    private final MybatisFillConfig handler = new MybatisFillConfig();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @Test
    @DisplayName("更新填充：实体带着旧 update_time 时也会被刷新为当前时间")
    void updateFillOverwritesStaleValue() {
        Order order = new Order();
        LocalDateTime stale = LocalDateTime.now().minusDays(3);
        order.setUpdateTime(stale);

        handler.updateFill(SystemMetaObject.forObject(order));

        assertNotNull(order.getUpdateTime());
        assertTrue(order.getUpdateTime().isAfter(stale),
                "update_time 必须前进，否则评价有效期会把完成时间算错");
    }

    @Test
    @DisplayName("插入填充：create_time / update_time 为空时自动补当前时间")
    void insertFillSetsTimestamps() {
        Order order = new Order();

        handler.insertFill(SystemMetaObject.forObject(order));

        assertNotNull(order.getCreateTime());
        assertNotNull(order.getUpdateTime());
    }
}
