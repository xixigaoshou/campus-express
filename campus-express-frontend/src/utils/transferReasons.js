/**
 * 转单接力原因清单（与后端 TransferReason 保持一致）
 * 转单不扣信誉分：订单由其他接单者接力完成，原接单者不再履约
 */
export const transferReasons = [
  { reason: '临时有事/时间冲突', desc: '突然有课、考试或急事，无法按时取件' },
  { reason: '同时接单太多忙不过来', desc: '手上订单较多，时间排不开' },
  { reason: '身体原因/受伤', desc: '生病或受伤，暂时无法跑动' },
  { reason: '天气原因/下雨等', desc: '暴雨、高温等不便出行' },
  { reason: '距离太远/不顺路', desc: '取件点或送达点较远，路程不划算' },
  { reason: '驿站关门/无法取件', desc: '到达时驿站已关门或快递柜故障' },
  { reason: '包裹太大/太重拿不了', desc: '超出预期，一个人拿不动' },
  { reason: '其他原因', desc: '兜底选项' }
]

/**
 * 发起转单接力所需最低信誉分（与后端 business.relay.min-credit 保持一致）
 * 仅作前端提示，最终以后端校验为准
 */
export const TRANSFER_MIN_CREDIT = 90