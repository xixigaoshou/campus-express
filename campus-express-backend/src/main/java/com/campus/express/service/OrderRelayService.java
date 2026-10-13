package com.campus.express.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.vo.OrderVO;

import java.math.BigDecimal;

/**
 * 转单接力领域服务：接力发起/撤回/承接、接力专区查询、接力超时自动撤回
 * 接力期间订单状态保持不变，仅以 transfer_status 标记挂单
 */
public interface OrderRelayService {

    /**
     * 发起转单接力：接单者在取件前无法继续履约时，把订单挂到接力专区等待其他接单者接力
     * 权限：仅订单接单者本人；订单须处于已接单/取件中；原因须为 TransferReason 清单内文案；
     * 同一订单同时仅允许一个接力挂单；接力不改变订单状态、不扣信誉分
     */
    void initiateTransfer(Integer orderId, String reason);

    /** 撤回转单接力：仅原接单者本人；撤回后订单继续由本人履约 */
    void cancelTransfer(Integer orderId);

    /**
     * 接力接单（乐观锁 + 事务）：其他接单者承接接力订单，接单者由原接单者换为接力者
     * 权限：仅接单者角色（canPublish=false）；禁止接力自己发起的转单/自己发布的订单；
     * 信誉分≥80 且不超过等级进行中上限；并发冲突返回接力失败
     */
    void relayTakeOrder(Integer orderId);

    /**
     * 接力专区分页（transfer_status='待接力' 的订单）
     * @param keyword   关键词：模糊匹配快递公司/取件码/取件地点/送达地点/备注，空则不过滤
     * @param minReward 报酬下限，空则不过滤
     * @param maxReward 报酬上限，空则不过滤
     */
    IPage<OrderVO> getRelayOrderPage(long current, long size, String keyword,
                                     BigDecimal minReward, BigDecimal maxReward);

    /**
     * 定时任务：转单接力超时自动撤回（每 60 秒扫描一次）
     * 规则：发起接力后 relay.timeoutMinutes（默认 30 分钟）内无人接力 → 自动撤回接力标记，通知原接单者继续履约
     */
    void relayTimeoutTask();

    /**
     * 关闭订单当前挂起的转单接力流水（接力成功/撤回/过期/超时取消时调用，仅作用于「待接力」记录）
     * 供超时调度领域在订单超时处理时同步使接力失效
     */
    void closeActiveTransfer(Integer orderId, String status);
}