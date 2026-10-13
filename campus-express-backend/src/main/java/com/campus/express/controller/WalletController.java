package com.campus.express.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.common.Result;
import com.campus.express.dto.WalletRechargeDTO;
import com.campus.express.dto.WalletWithdrawDTO;
import com.campus.express.service.UserService;
import com.campus.express.service.WalletService;
import com.campus.express.vo.WalletLogVO;
import com.campus.express.vo.WalletVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包接口（/api/wallet）—— 虚拟钱包模块（R-25 ~ R-28）
 * 全部需登录；userId 从 JWT 解析，仅允许访问本人钱包数据，越权拦截
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserService userService;

    /**
     * 查询本人钱包信息（可用余额 / 冻结余额）
     * 权限：已登录（仅本人）
     */
    @GetMapping("/info")
    public Result<WalletVO> info() {
        return Result.success(walletService.getWalletInfo(userService.getCurrentUser().getUserId()));
    }

    /**
     * 分页查询本人资金流水（新到旧，logType 转文本）
     * 权限：已登录（仅本人流水）
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @param logType  流水类型过滤（1充值 2冻结 3解冻 4结算收入 5支出扣减，可空=全部）
     */
    @GetMapping("/log/page")
    public Result<IPage<WalletLogVO>> logPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer logType) {
        return Result.success(walletService.pageWalletLogs(userService.getCurrentUser().getUserId(), pageNum, pageSize, logType));
    }

    /**
     * 模拟充值（无真实支付）
     * 权限：已登录（仅本人）；金额 0.5 ~ 100 元；须校验支付密码，成功写入充值流水
     */
    @PostMapping("/recharge")
    public Result<Void> recharge(@RequestBody @Valid WalletRechargeDTO dto) {
        Integer userId = userService.getCurrentUser().getUserId();
        // 资金操作前置校验支付密码（未设置会引导先到个人中心设置）
        userService.verifyPayPassword(userId, dto.getPayPassword());
        walletService.recharge(userId, dto.getRechargeAmount());
        return Result.success("充值成功", null);
    }

    /**
     * 提现（模拟，无真实支付通道）
     * 权限：已登录（仅本人）；金额 0.5 ~ 100 元且不超过可用余额；须校验支付密码，成功写入提现流水
     * 补齐资金闭环：接单者赚取的报酬可取出，否则钱包只进不出
     */
    @PostMapping("/withdraw")
    public Result<Void> withdraw(@RequestBody @Valid WalletWithdrawDTO dto) {
        Integer userId = userService.getCurrentUser().getUserId();
        userService.verifyPayPassword(userId, dto.getPayPassword());
        walletService.withdraw(userId, dto.getWithdrawAmount());
        return Result.success("提现成功", null);
    }
}
