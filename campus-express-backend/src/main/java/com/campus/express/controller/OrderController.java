package com.campus.express.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.Result;
import com.campus.express.config.JwtInterceptor;
import com.campus.express.dto.OrderPublishDTO;
import com.campus.express.dto.OrderStatusChangeDTO;
import com.campus.express.dto.OrderTransferDTO;
import com.campus.express.dto.OrderUpdateDTO;
import com.campus.express.service.OrderService;
import com.campus.express.vo.OrderDetailVO;
import com.campus.express.vo.OrderReuseVO;
import com.campus.express.vo.OrderVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 订单接口（/api/order）—— 系统业务核心
 * 全部需登录；抢单乐观锁、状态机流转、取消规则等业务校验均在 Service 层完成
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final HttpServletRequest request;

    /** 从 JWT 拦截器注入的 request attribute 取当前登录用户 ID */
    private Integer currentUserId() {
        return (Integer) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
    }

    /**
     * 发布代取需求
     * 权限：已登录（任意用户）；报酬金额受 R-17 约束（2~50 元）
     * @return 新订单 ID
     */
    @PostMapping("/publish")
    public Result<Integer> publish(@RequestBody @Valid OrderPublishDTO dto) {
        return Result.success("发布成功", orderService.publishOrder(dto));
    }

    /**
     * 修改【待接单】状态订单
     * 权限：仅订单发布者本人；订单已接单后禁止修改核心字段（R-11）
     */
    @PutMapping("/update-wait")
    public Result<Void> updateWait(@RequestBody @Valid OrderUpdateDTO dto) {
        orderService.updateWaitOrder(dto);
        return Result.success("修改成功", null);
    }

    /**
     * 取消订单
     * 权限：待接单 → 发布者本人（原因必选发布者清单）；已接单/取件中 → 接单者本人（原因必选接单者清单，扣信誉分退回待接单，R-12）
     * @param orderId 订单 ID
     * @param cancelReason 取消原因（CancelReason 清单内文案，中文参数由前端自动 URL 编码）
     */
    @PutMapping("/cancel")
    public Result<Void> cancel(@RequestParam("orderId") Integer orderId,
                               @RequestParam("cancelReason") String cancelReason) {
        orderService.cancelOrder(orderId, cancelReason);
        return Result.success("订单已取消", null);
    }

    /**
     * 抢单接单（乐观锁 + 事务）
     * 权限：已登录（接单方）；禁止接自己的单（R-8）、信誉分≥80（R-23）、
     * 不超过等级进行中上限（R-9）；并发冲突返回抢单失败（R-7）
     * @param orderId 订单 ID
     */
    @PutMapping("/take-order")
    public Result<Void> takeOrder(@RequestParam("orderId") Integer orderId) {
        orderService.takeOrder(orderId);
        return Result.success("抢单成功", null);
    }

    /**
     * 更新订单状态（取件中 / 配送中 / 待收货）
     * 权限：仅接单者本人；严格状态机流转，禁止跨状态跳转（R-1），变更写历史表（R-4）；
     * 流转到「待收货」时送达照片必填，无照片须显式 skipPhoto=true（无法拍照）
     */
    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestBody @Valid OrderStatusChangeDTO dto) {
        orderService.updateOrderStatus(dto.getOrderId(), dto.getTargetStatus(),
                currentUserId(), dto.getChangeNote(), Boolean.TRUE.equals(dto.getSkipPhoto()));
        return Result.success("状态更新成功", null);
    }

    /**
     * 接单者确认送达（配送中 → 待收货）
     * 权限：仅接单者本人；记录送达时间，需求方须在 24 小时内确认收货，超时系统自动确认收货；
     * 送达照片默认要求上传，无法拍照时传 skipPhoto=true 跳过
     * @param orderId 订单 ID
     * @param skipPhoto 无法拍照跳过标记（默认 false）
     */
    @PutMapping("/confirm-arrival")
    public Result<Void> confirmArrival(@RequestParam("orderId") Integer orderId,
                                       @RequestParam(value = "skipPhoto", defaultValue = "false") boolean skipPhoto) {
        orderService.confirmArrival(orderId, skipPhoto);
        return Result.success("已确认送达，等待需求方确认收货", null);
    }

    /**
     * 上传订单照片凭证（取件选填 / 送达必填）
     * 权限：仅订单接单者本人；multipart 表单字段名 file；
     * type=pickup 允许在已接单/取件中上传，type=deliver 允许在配送中上传；
     * 返回可访问相对路径（/uploads/order/xxx），经静态资源映射访问
     * @param orderId 订单 ID
     * @param type 照片类型：pickup（取件）/ deliver（送达）
     */
    @PostMapping("/{orderId}/photo")
    public Result<String> uploadPhoto(@PathVariable Integer orderId,
                                      @RequestParam("type") String type,
                                      @RequestParam("file") MultipartFile file) {
        return Result.success("照片上传成功", orderService.uploadOrderPhoto(orderId, type, file));
    }

    /**
     * 发起转单接力
     * 权限：仅订单接单者本人；订单须处于已接单/取件中；原因须为转单原因清单内文案；
     * 同一订单同时仅允许一个接力挂单；接力不改变订单状态、不扣信誉分
     * @param dto 订单 ID + 转单原因
     */
    @PostMapping("/transfer")
    public Result<Void> initiateTransfer(@RequestBody @Valid OrderTransferDTO dto) {
        orderService.initiateTransfer(dto.getOrderId(), dto.getReason());
        return Result.success("已发起转单接力，等待其他接单者接力", null);
    }

    /**
     * 撤回转单接力
     * 权限：仅订单接单者本人；撤回后订单继续由本人履约
     * @param orderId 订单 ID
     */
    @PutMapping("/transfer/cancel")
    public Result<Void> cancelTransfer(@RequestParam("orderId") Integer orderId) {
        orderService.cancelTransfer(orderId);
        return Result.success("已撤回转单接力", null);
    }

    /**
     * 接力接单（乐观锁 + 事务）
     * 权限：仅接单者角色（canPublish=false）；禁止接力自己发起的转单/自己发布的订单；
     * 信誉分≥80 且不超过等级进行中上限；并发冲突返回接力失败
     * @param orderId 订单 ID
     */
    @PutMapping("/relay-take")
    public Result<Void> relayTake(@RequestParam("orderId") Integer orderId) {
        orderService.relayTakeOrder(orderId);
        return Result.success("接力成功，订单已转由您履约", null);
    }

    /**
     * 接力专区分页（转单接力中的订单）
     * 权限：已登录；支持关键词与报酬范围筛选
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数
     * @param keyword 关键词：模糊匹配快递公司/取件码/取件地点/送达地点/备注（可空）
     * @param minReward 报酬下限（可空）
     * @param maxReward 报酬上限（可空）
     */
    @GetMapping("/relay-page")
    public Result<IPage<OrderVO>> relayPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页条数不能超过 100") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.math.BigDecimal minReward,
            @RequestParam(required = false) java.math.BigDecimal maxReward) {
        return Result.success(orderService.getRelayOrderPage(pageNum, pageSize, keyword, minReward, maxReward));
    }

    /**
     * 需求方确认收货（待收货 → 已完成），报酬结算给接单者
     * 权限：仅订单发布者本人（R-3）；订单须处于待收货状态；
     * 若 24 小时内未确认，定时任务将自动确认收货（报酬照常结算）
     * @param orderId 订单 ID
     */
    @PutMapping("/confirm-finish")
    public Result<Void> confirmFinish(@RequestParam("orderId") Integer orderId) {
        orderService.updateOrderStatus(orderId, OrderStatus.COMPLETED, currentUserId(), "需求方确认收货", false);
        return Result.success("确认收货成功", null);
    }

    /**
     * 需求大厅分页（仅待接单订单，按发布时间降序）
     * 权限：已登录；支持关键词与报酬范围筛选
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数
     * @param keyword 关键词：模糊匹配快递公司/取件码/取件地点/送达地点/备注（可空）
     * @param minReward 报酬下限（可空）
     * @param maxReward 报酬上限（可空）
     */
    @GetMapping("/hall-page")
    public Result<IPage<OrderVO>> hallPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页条数不能超过 100") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.math.BigDecimal minReward,
            @RequestParam(required = false) java.math.BigDecimal maxReward) {
        return Result.success(orderService.getHallOrderPage(pageNum, pageSize, keyword, minReward, maxReward));
    }

    /**
     * 我的发布订单分页（区分进行中/历史）
     * 权限：已登录（本人发布）
     * @param statusList 状态过滤：不传=全部；传"进行中"查未完结订单，传"历史"查已完成/已取消
     */
    @GetMapping("/my-publish-page")
    public Result<IPage<OrderVO>> myPublishPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页条数不能超过 100") long pageSize,
            @RequestParam(required = false) List<String> statusList) {
        return Result.success(orderService.getMyPublishPage(pageNum, pageSize, statusList));
    }

    /**
     * 我的接单订单分页（区分进行中/历史）
     * 权限：已登录（本人接单）
     * @param statusList 状态过滤规则同 my-publish-page
     */
    @GetMapping("/my-take-page")
    public Result<IPage<OrderVO>> myTakePage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页条数不能超过 100") long pageSize,
            @RequestParam(required = false) List<String> statusList) {
        return Result.success(orderService.getMyTakePage(pageNum, pageSize, statusList));
    }

    /**
     * 再次发布模板：复制历史订单的包裹信息，用于一键快速发布
     * 权限：仅订单发布者本人；期望取件时间不预填（重新发布时应重新选择）
     * @param orderId 订单 ID（任何状态均可复制，均属发布者自己的单）
     */
    @GetMapping("/{orderId}/reuse-template")
    public Result<OrderReuseVO> reuseTemplate(@PathVariable Integer orderId) {
        return Result.success(orderService.getReuseTemplate(orderId));
    }

    /**
     * 发布者提醒接单者（催单）
     * 权限：仅订单发布者本人；订单须处于已接单/取件中/配送中；
     * 10 分钟内限一次（防刷），提醒以站内通知送达接单者并实时推送角标
     * @param orderId 订单 ID
     */
    @PostMapping("/{orderId}/urge")
    public Result<Void> urge(@PathVariable Integer orderId) {
        orderService.urgeTaker(orderId);
        return Result.success("已提醒接单者", null);
    }

    /**
     * 获取订单详情（订单 + 双方昵称 + 完整状态流转 + 双向评价）
     * 权限：已登录（建议仅订单双方可见，展示层控制）
     * @param orderId 订单 ID
     */
    @GetMapping("/{orderId}")
    public Result<OrderDetailVO> detail(@PathVariable Integer orderId) {
        return Result.success(orderService.getOrderVOById(orderId));
    }
}
