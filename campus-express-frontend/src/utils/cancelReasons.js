/**
 * 取消原因清单（与后端 CancelReason 常量保持一致）
 * reason 为提交给后端的取消原因文案（写入 t_order.cancel_reason），
 * desc 为前端展示的说明文字；编号为展示序号，不参与存储
 */
export const publisherCancelReasons = [
  { reason: '自己已取/不需要了', desc: '顺路取了，或收件人自己拿了' },
  { reason: '发错信息/下错单', desc: '取件码、取件点、宿舍楼填错' },
  { reason: '等太久没人接', desc: '发布时间过长，不想等了' },
  { reason: '临时改主意', desc: '不着急取，或不想花钱了' },
  { reason: '联系不上接单者', desc: '接单后一直联系不上对方' },
  { reason: '协商一致取消', desc: '与接单者说好取消' },
  { reason: '快递被退回/已签收', desc: '驿站退回，或已被他人签收' },
  { reason: '取件时间对不上', desc: '接单者时间与自己时间冲突' },
  { reason: '报酬填错了', desc: '金额写错，想重新发布' },
  { reason: '其他原因', desc: '兜底选项' }
]

export const takerCancelReasons = [
  { reason: '联系不上需求方', desc: '电话不接、消息不回' },
  { reason: '驿站关门/取件点关闭', desc: '到达时驿站已关门' },
  { reason: '快递柜故障/无法取件', desc: '柜子打不开、系统故障' },
  { reason: '包裹找不到/已被取走', desc: '驿站说没有，或被误领' },
  { reason: '包裹太大/太重拿不了', desc: '超出预期，一个人拿不动' },
  { reason: '宿舍楼进不去/太难找', desc: '门禁、楼层、位置难找' },
  { reason: '临时有事/时间冲突', desc: '突然有课、考试、急事' },
  { reason: '同时接单太多忙不过来', desc: '手上单多，时间排不开' },
  { reason: '身体原因/受伤', desc: '腿伤、生病等无法跑动' },
  { reason: '天气原因/下雨等', desc: '暴雨、高温等不便出行' },
  { reason: '报酬太低/不划算', desc: '距离远、报酬少，不想跑' },
  { reason: '需求方要求过多/难沟通', desc: '反复改要求、态度差' },
  { reason: '其他原因', desc: '兜底选项' }
]
