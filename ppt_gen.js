// ============================================================
// 校园快递代取平台 - 需求分析报告汇报PPT
// ============================================================
const pptxgen = require("pptxgenjs");
const pres = new pptxgen();
pres.author = "易涛，熊孟霖，彭效然";
pres.title = "校园快递代取平台 需求分析报告";

// ============================================================
// SLIDE DIMENSIONS - DECLARE FIRST
// ============================================================
pres.layout = "LAYOUT_16x9";
const SLIDE_W = 10;
const SLIDE_H = 5.625;
const MARGIN = 0.5;
const CONTENT_X = MARGIN;
const CONTENT_Y = 1.15;
const CONTENT_W = SLIDE_W - 2 * MARGIN;   // 9
const CONTENT_H = SLIDE_H - MARGIN - 0.3; // 4.825 内容底界
const CENTER_X = SLIDE_W / 2;

// ============================================================
// THEME
// ============================================================
const C = {
  primary: "1B4965",    // 深蓝 主色
  primary2: "2C6E8F",   // 蓝青
  secondary: "5FA8D3",  // 浅蓝
  accent: "FFB703",     // 快递橙 强调
  accent2: "E8A300",    // 深橙
  dark: "14304A",       // 深底
  dark2: "1D4466",
  light: "F5F9FC",      // 浅底
  light2: "EAF3F9",
  card: "FFFFFF",
  text: "22313F",
  muted: "5C7285",
  line: "D6E4EE",
  white: "FFFFFF",
};
const F_TITLE = "SimSun";          // 宋体 serif
const F_BODY = "Microsoft YaHei";  // 微软雅黑 sans

// ============================================================
// CONTAINER SYSTEM (with overflow protection)
// ============================================================
function createVirtualNode(type, data, parentX = 0, parentY = 0) {
  const opts = data.opts || {};
  const node = { type, data, absX: parentX + (opts.x || 0), absY: parentY + (opts.y || 0), w: opts.w || 0, h: opts.h || 0, children: [] };
  node.addShape = function (shapeType, opts = {}) { const c = createVirtualNode("shape", { shapeType, opts }, node.absX, node.absY); node.children.push(c); return c; };
  node.addText = function (text, opts = {}) { const c = createVirtualNode("text", { text, opts: { fit: "shrink", ...opts } }, node.absX, node.absY); node.children.push(c); return c; };
  node.addImage = function (opts = {}) { const c = createVirtualNode("image", { opts }, node.absX, node.absY); node.children.push(c); return c; };
  node.addTable = function (tableData, opts = {}) { const c = createVirtualNode("table", { tableData, opts }, node.absX, node.absY); node.children.push(c); return c; };
  return node;
}
function flattenNode(node, realSlide) {
  const absOpts = { ...node.data.opts, x: node.absX, y: node.absY };
  if (node.type === "shape") realSlide.addShape(node.data.shapeType, absOpts);
  else if (node.type === "text") realSlide.addText(node.data.text, absOpts);
  else if (node.type === "image") realSlide.addImage(absOpts);
  else if (node.type === "table") realSlide.addTable(node.data.tableData, absOpts);
  node.children.forEach((c) => flattenNode(c, realSlide));
}
const originalAddSlide = pres.addSlide.bind(pres);
pres.addSlide = function (options) {
  const realSlide = originalAddSlide(options);
  const virtualSlide = {
    children: [], _realSlide: realSlide,
    set background(v) { realSlide.background = v; },
    get background() { return realSlide.background; },
    addShape: function (shapeType, opts = {}) { const n = createVirtualNode("shape", { shapeType, opts }, 0, 0); this.children.push(n); return n; },
    addText: function (text, opts = {}) { const n = createVirtualNode("text", { text, opts: { fit: "shrink", ...opts } }, 0, 0); this.children.push(n); return n; },
    addImage: function (opts = {}) { const n = createVirtualNode("image", { opts }, 0, 0); this.children.push(n); return n; },
    addTable: function (tableData, opts = {}) { const n = createVirtualNode("table", { tableData, opts }, 0, 0); this.children.push(n); return n; },
    addChart: function (ct, d, o) { realSlide.addChart(ct, d, o); },
    render: function () { this.children.forEach((c) => flattenNode(c, realSlide)); },
  };
  return virtualSlide;
};

// ============================================================
// HELPERS
// ============================================================
const shadow = { type: "outer", blur: 4, offset: 2, color: "000000", opacity: 0.18 };

// 内容页顶部：章节标签 + 标题
function contentHeader(slide, chap, title, subtitle) {
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 0.32, w: 0.16, h: 0.6, fill: { color: C.accent }, rectRadius: 0.04,
  });
  slide.addText(chap, {
    x: 0.78, y: 0.28, w: 1.9, h: 0.32, fontFace: F_BODY, fontSize: 11, bold: true, color: C.accent2, margin: 0,
  });
  slide.addText(title, {
    x: 0.78, y: 0.52, w: 7.5, h: 0.44, fontFace: F_TITLE, fontSize: 24, bold: true, color: C.text, margin: 0,
  });
  if (subtitle) {
    slide.addText(subtitle, {
      x: 8.2, y: 0.55, w: 1.8, h: 0.4, fontFace: F_BODY, fontSize: 10, color: C.muted, align: "right", margin: 0,
    });
  }
}

// 卡片：左侧橙色条 + 白底圆角
function card(slide, x, y, w, h, title, bodyLines, opts = {}) {
  const tColor = opts.titleColor || C.primary;
  const bar = opts.bar || C.accent;
  const bg = opts.fill || C.card;
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, fill: { color: bg }, line: { color: C.line, width: 1 }, rectRadius: 0.06, shadow });
  slide.addShape(pres.shapes.RECTANGLE, { x: x + 0.02, y: y + 0.22, w: 0.07, h: h - 0.44, fill: { color: bar } });
  slide.addText(title, { x: x + 0.22, y: y + 0.12, w: w - 0.4, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: tColor, margin: 0 });
  const items = (Array.isArray(bodyLines) ? bodyLines : [bodyLines]).map((t, i) => ({
    text: t, options: { bullet: i < (Array.isArray(bodyLines) ? bodyLines.length : 1), paraSpaceAfter: 3, fontSize: 11.5, color: opts.textColor || C.text, fontFace: F_BODY, breakLine: true },
  }));
  items[items.length - 1].options.breakLine = false;
  slide.addText(items, { x: x + 0.22, y: y + 0.5, w: w - 0.42, h: h - 0.6, valign: "top", fit: "shrink", margin: 0 });
}

// 序号圆
function numBadge(slide, x, y, n, size = 0.42, color = C.primary) {
  slide.addShape(pres.shapes.OVAL, { x, y, w: size, h: size, fill: { color } });
  slide.addText(String(n), { x, y, w: size, h: size, align: "center", valign: "middle", fontFace: F_BODY, fontSize: 14, bold: true, color: C.white, margin: 0 });
}

// 章节分隔（深色页样式统一）
function sectionCover(n, zh, en, desc) {
  const s = pres.addSlide();
  s.background = { color: C.dark };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 0.14, h: 5.625, fill: { color: C.accent } });
  s.addText("第 " + n + " 章", { x: 0.9, y: 1.5, w: 3, h: 0.5, fontFace: F_BODY, fontSize: 16, color: C.accent, margin: 0 });
  s.addText(zh, { x: 0.9, y: 1.95, w: 8.5, h: 0.9, fontFace: F_TITLE, fontSize: 40, bold: true, color: C.white, margin: 0 });
  if (en) s.addText(en, { x: 0.9, y: 2.9, w: 8.5, h: 0.5, fontFace: F_BODY, fontSize: 16, color: C.secondary, margin: 0 });
  if (desc) s.addText(desc, { x: 0.9, y: 3.5, w: 8, h: 0.9, fontFace: F_BODY, fontSize: 13, color: "B8CCDD", margin: 0 });
  s.addShape(pres.shapes.OVAL, { x: 8.6, y: 1.1, w: 1.6, h: 1.6, fill: { color: C.dark2 }, line: { color: C.primary2, width: 1.5 } });
  s.addText(n, { x: 8.6, y: 1.1, w: 1.6, h: 1.6, align: "center", valign: "middle", fontFace: F_TITLE, fontSize: 34, bold: true, color: C.accent, margin: 0 });
  s.render();
  return s;
}

// ============================================================
// SLIDE 1: 封面
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.dark };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.1, fill: { color: C.accent } });
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 5.525, w: 10, h: 0.1, fill: { color: C.accent } });
  s.addShape(pres.shapes.OVAL, { x: 8.2, y: -1.2, w: 3.4, h: 3.4, fill: { color: C.dark2 } });
  s.addShape(pres.shapes.OVAL, { x: -1.4, y: 4.2, w: 3.0, h: 3.0, fill: { color: C.dark2 } });
  s.addText("赣南师范大学科技学院", { x: 1, y: 0.7, w: 8, h: 0.5, align: "center", fontFace: F_TITLE, fontSize: 18, bold: true, color: C.white, margin: 0, charSpacing: 1 });
  s.addText("计算机类课程实验报告", { x: 1, y: 1.15, w: 8, h: 0.45, align: "center", fontFace: F_TITLE, fontSize: 15, color: C.secondary, margin: 0 });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 2.6, y: 2.0, w: 4.8, h: 0.05, fill: { color: C.accent }, rectRadius: 0.02 });
  s.addText("校园快递代取平台", { x: 0.8, y: 2.35, w: 8.4, h: 0.95, align: "center", fontFace: F_TITLE, fontSize: 44, bold: true, color: C.white, margin: 0, charSpacing: 2.5 });
  s.addText("需求分析报告", { x: 0.8, y: 3.28, w: 8.4, h: 0.6, align: "center", fontFace: F_TITLE, fontSize: 22, color: C.accent, margin: 0, charSpacing: 1.5 });
  s.addText([
    { text: "小组成员：易涛，熊孟霖，彭效然", options: { breakLine: true, fontSize: 13, color: "D7E4EF" } },
    { text: "指导老师：曾小龙    日期：2026/9/23", options: { fontSize: 13, color: "D7E4EF" } },
  ], { x: 1, y: 4.45, w: 8, h: 0.75, align: "center", fontFace: F_BODY, margin: 0 });
  s.render();
}

// ============================================================
// SLIDE 2: 目录
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 0.14, h: 5.625, fill: { color: C.accent } });
  s.addText("目录", { x: 0.7, y: 0.5, w: 3, h: 0.7, fontFace: F_TITLE, fontSize: 34, bold: true, color: C.text, margin: 0 });
  s.addText("CONTENTS", { x: 0.72, y: 1.18, w: 3, h: 0.35, fontFace: F_BODY, fontSize: 12, color: C.muted, margin: 0 });
  const toc = [
    ["01", "项目背景与目标", "背景 · 目标 · 范围 · 可行性"],
    ["02", "用户角色分析", "两类角色 · 用例总览"],
    ["03", "功能需求", "5项核心功能 · 辅助功能"],
    ["04", "非功能需求", "性能 · 安全 · 可用性等六类"],
    ["05", "数据需求", "实体关系 · 表设计 · 完整性"],
    ["06", "业务规则", "状态流转 · 抢单 · 报酬 · 信誉"],
    ["07", "外部接口需求", "站内自实现 · 取舍说明"],
    ["08", "技术选型建议", "架构 · 技术栈 · 分工 · 计划"],
  ];
  toc.forEach((t, i) => {
    const col = i < 4 ? 0 : 1;
    const row = i % 4;
    const x = 0.9 + col * 4.55;
    const y = 1.75 + row * 0.92;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 4.25, h: 0.76, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.1, shadow });
    s.addText(t[0], { x: x + 0.2, y: y + 0.14, w: 0.8, h: 0.5, fontFace: F_TITLE, fontSize: 20, bold: true, color: C.accent2, margin: 0 });
    s.addText(t[1], { x: x + 1.0, y: y + 0.1, w: 3.1, h: 0.32, fontFace: F_BODY, fontSize: 14, bold: true, color: C.text, margin: 0 });
    s.addText(t[2], { x: x + 1.0, y: y + 0.42, w: 3.1, h: 0.28, fontFace: F_BODY, fontSize: 9.5, color: C.muted, margin: 0 });
  });
  s.render();
}

// ============================================================
// SLIDE 3: 1.1 项目背景
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 1 章", "1.1 项目背景", "校园快递代取平台");
  card(s, 0.5, 1.35, 3.0, 3.75, "校园快递痛点", [
    "取件时间与课程安排冲突，驿站营业时间固定",
    "高峰期驿站排队时间长，影响学习生活",
    "取件点多且分散，跨区域取件往返成本高",
    "找人代取缺乏可信渠道，无责任追溯机制",
  ], { fill: C.card });
  card(s, 3.72, 1.35, 3.0, 3.75, "代取需求增长", [
    "快递业务量逐年上升，驿站货架一位难求",
    "同学间互助代取普遍，但靠口头约定效率低",
    "需要一个可信的线上撮合平台承接需求",
    "平台化可沉淀评价与信誉，提升代取可信度",
  ], { fill: C.card, bar: C.secondary });
  const bg = s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 6.95, y: 1.35, w: 3.05, h: 3.75, fill: { color: C.primary }, rectRadius: 0.08, shadow });
  s.addText("项目定位", { x: 7.15, y: 1.55, w: 2.7, h: 0.35, fontFace: F_BODY, fontSize: 14, bold: true, color: C.accent, margin: 0 });
  s.addText("面向高校校园的快递代取信息撮合平台：需求方发布代取需求，接单者自愿抢单承接，平台保障订单状态全程可追踪、沟通可留痕、服务可评价。", {
    x: 7.15, y: 2.0, w: 2.7, h: 1.6, fontFace: F_BODY, fontSize: 12, color: C.white, autoFit: false, fit: "none", margin: 0, valign: "top",
  });
  s.addText("Web 开发课程期末项目", { x: 7.15, y: 4.35, w: 2.7, h: 0.6, fontFace: F_BODY, fontSize: 11, bold: true, color: C.secondary, margin: 0 });
  s.render();
}

// ============================================================
// SLIDE 4: 1.2 项目目标
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 1 章", "1.2 项目目标", "总体目标 + 具体目标");
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 1.3, w: 9, h: 0.85, fill: { color: C.primary }, rectRadius: 0.08, shadow });
  s.addText("总体目标：基于 Web 应用与数据库技术，建设一个稳定、易用、可信的校园快递代取平台，让代取需求发布与承接实现全流程线上化。", {
    x: 0.8, y: 1.3, w: 8.5, h: 0.85, fontFace: F_BODY, fontSize: 14, color: C.white, valign: "middle", margin: 0, autoFit: false, fit: "none",
  });
  const goals = [
    ["01", "提供代取需求的发布与承接渠道，需求方 30 秒内完成发布，接单者实时浏览抢单", "发布 · 接单"],
    ["02", "实现订单状态全程可追踪（待接单→已完成），状态变更实时可查、操作留痕", "状态流转"],
    ["03", "提供订单留言沟通与双向评价机制，沉淀信誉分，构建可信代取环境", "留言 · 评价"],
    ["04", "个人订单中心统一管理我的发布/我的接单，并统计订单与信誉数据", "订单中心"],
  ];
  goals.forEach((g, i) => {
    const x = 0.5 + (i % 2) * 4.62;
    const y = 2.35 + Math.floor(i / 2) * 1.42;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 4.38, h: 1.26, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
    numBadge(s, x + 0.2, y + 0.18, i + 1);
    s.addText(g[1], { x: x + 0.78, y: y + 0.12, w: 3.5, h: 0.8, fontFace: F_BODY, fontSize: 10.5, color: C.text, margin: 0, valign: "middle", autoFit: false, fit: "none" });
    s.addText(g[2], { x: x + 0.78, y: y + 0.92, w: 3.5, h: 0.28, fontFace: F_BODY, fontSize: 9, bold: true, color: C.accent2, margin: 0 });
  });
  s.render();
}

// ============================================================
// SLIDE 5: 1.3 项目范围 + 1.4 意义与可行性
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 1 章", "1.3 项目范围 / 1.4 意义与可行性", "范围聚焦 + 可行性");
  s.addText("项目范围", { x: 0.5, y: 1.25, w: 4.4, h: 0.35, fontFace: F_BODY, fontSize: 14, bold: true, color: C.primary, margin: 0 });
  card(s, 0.5, 1.62, 4.4, 1.62, "功能范围 / 技术范围", [
    "核心：发布需求、接单、状态流转、留言、订单中心、评价信誉；适配 4 项课程技术点",
    "管理后台、第三方接口不在本轮范围；Vue3 + Spring Boot 前后端分离",
  ], { fill: C.card, bar: C.primary });
  card(s, 0.5, 3.38, 4.4, 1.72, "项目边界", [
    "仅面向校内用户，学号/工号实名注册",
    "报酬结算线下进行，平台不做支付担保",
  ], { fill: C.card, bar: C.primary });
  s.addText("意义与可行性", { x: 5.1, y: 1.25, w: 4.4, h: 0.35, fontFace: F_BODY, fontSize: 14, bold: true, color: C.primary, margin: 0 });
  const feas = [
    ["技术可行", "Java/Spring Boot/MyBatis/MySQL 技术成熟，小组已掌握；Vue3 生态完善", C.primary],
    ["经济可行", "全部采用开源技术栈，部署在实验室服务器，无额外成本", C.primary2],
    ["操作可行", "Web 端无需安装，校园网内访问；界面简洁，操作门槛低", C.accent2],
  ];
  feas.forEach((f, i) => {
    const y = 1.62 + i * 1.16;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 5.1, y, w: 4.4, h: 1.0, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
    s.addShape(pres.shapes.RECTANGLE, { x: 5.12, y: y + 0.18, w: 0.07, h: 0.64, fill: { color: f[2] } });
    s.addText(f[0], { x: 5.35, y: y + 0.1, w: 4.0, h: 0.3, fontFace: F_BODY, fontSize: 12, bold: true, color: f[2], margin: 0 });
    s.addText(f[1], { x: 5.35, y: y + 0.4, w: 4.0, h: 0.55, fontFace: F_BODY, fontSize: 10, color: C.text, margin: 0, valign: "top", autoFit: false, fit: "none" });
  });
  s.render();
}

// ============================================================
// SLIDE 6: 2.1 角色划分 + 2.2/2.3 角色职责
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 2 章", "2.1 用户角色分析：两类核心角色", "角色总体划分");
  const roles = [
    { name: "需求发布者", en: "需求侧发起方", icon: "发", color: C.primary, items: ["通常是课程时间冲突、不便取件的同学", "发布代取需求：取件点、送达点、期望时间、报酬", "查看订单状态、留言沟通、确认收货并评价"] },
    { name: "接单者（代取人员）", en: "供给侧承接方", icon: "接", color: C.accent2, items: ["通常为空闲时间较多的同学，自愿接单", "浏览需求大厅、抢单承接、按状态履约更新", "完成配送获取报酬，接受双方互评积累信誉"] },
  ];
  roles.forEach((r, i) => {
    const x = 0.5 + i * 4.62;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.3, w: 4.38, h: 3.85, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
    s.addShape(pres.shapes.OVAL, { x: x + 0.25, y: 1.55, w: 0.72, h: 0.72, fill: { color: r.color } });
    s.addText(r.icon, { x: x + 0.25, y: 1.55, w: 0.72, h: 0.72, align: "center", valign: "middle", fontFace: F_TITLE, fontSize: 24, bold: true, color: C.white, margin: 0 });
    s.addText(r.name, { x: x + 1.1, y: 1.5, w: 3.1, h: 0.4, fontFace: F_BODY, fontSize: 16, bold: true, color: r.color, margin: 0 });
    s.addText(r.en, { x: x + 1.1, y: 1.9, w: 3.1, h: 0.3, fontFace: F_BODY, fontSize: 10.5, color: C.muted, margin: 0 });
    const items = r.items.map((t, j) => ({ text: t, options: { bullet: true, paraSpaceAfter: 5, fontSize: 11, color: C.text, fontFace: F_BODY, breakLine: true } }));
    items[items.length - 1].options.breakLine = false;
    s.addText(items, { x: x + 0.3, y: 2.55, w: 3.85, h: 2.5, valign: "top", margin: 0, fit: "shrink" });
  });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 5.28, w: 9, h: 0.28, fill: { color: C.light2 }, rectRadius: 0.05 });
  s.addText("双方围绕同一笔订单形成供需关系：发布者发起生命周期，接单者推动履约，平台保障过程透明。", {
    x: 0.7, y: 5.28, w: 8.6, h: 0.28, fontFace: F_BODY, fontSize: 10, color: C.muted, align: "center", margin: 0,
  });
  s.render();
}

// ============================================================
// SLIDE 7: 2.4 用户用例总览
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 2 章", "2.4 用户用例总览", "核心用例清单");
  const rows = [
    ["需求发布者", "注册登录 · 发布代取需求 · 查看订单状态 · 留言沟通 · 取消订单 · 评价接单者"],
    ["接单者（代取人员）", "注册登录 · 浏览需求并抢单 · 更新订单状态 · 留言沟通 · 查看收益 · 评价需求方"],
    ["双方（协作场景）", "接收订单通知 · 处理超时提醒 · 协商解决问题 · 查看信誉与历史订单"],
  ];
  s.addTable([
    [
      { text: "角色", options: { fill: { color: C.primary }, color: C.white, bold: true, fontSize: 13, align: "center", valign: "middle" } },
      { text: "主要用例", options: { fill: { color: C.primary }, color: C.white, bold: true, fontSize: 13, align: "center", valign: "middle" } },
    ],
    ...rows.map((r) => [
      { text: r[0], options: { fill: { color: C.light2 }, color: C.primary, bold: true, fontSize: 12, align: "center", valign: "middle" } },
      { text: r[1], options: { fill: { color: C.white }, color: C.text, fontSize: 11, align: "left", valign: "middle" } },
    ]),
  ], { x: 0.5, y: 1.5, w: 9, colW: [2.6, 6.4], rowH: 0.95, border: { pt: 1, color: C.line }, margin: 0 });
  s.addText("用例设计覆盖订单全生命周期：发布 → 接单 → 履约 → 完成 → 评价，每个关键节点均有对应的系统功能支撑。", {
    x: 0.5, y: 4.75, w: 9, h: 0.4, fontFace: F_BODY, fontSize: 11, color: C.accent2, bold: true, align: "center", margin: 0,
  });
  s.render();
}

// ============================================================
// SLIDE 8: 3.1 功能需求总览
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 3 章", "3.1 功能需求总览：功能 × 技术点映射", "核心基础功能全覆盖");
  const rows = [
    ["用户管理", "注册 · 登录 · 个人资料维护", "会话管理、增删改查"],
    ["需求发布", "代取需求填写、发布、编辑、撤销", "增删改查全套"],
    ["接单处理", "抢单、接单、履约信息更新", "用户关联查询、事务处理"],
    ["订单状态流转", "状态更新、状态历史记录", "订单状态流转"],
    ["消息留言", "发送留言、会话查看、已读标记", "会话管理、用户关联查询"],
    ["个人订单中心", "我的发布/我的接单、订单详情、统计", "增删改查、关联查询"],
    ["需求列表浏览", "列表展示、排序、分页", "增删改查"],
    ["评价与信誉", "双向评价、信誉分动态管理", "数据统计、关联查询"],
  ];
  s.addTable([
    [
      { text: "功能模块", options: { fill: { color: C.primary }, color: C.white, bold: true, fontSize: 12.5, align: "center", valign: "middle" } },
      { text: "功能子项", options: { fill: { color: C.primary }, color: C.white, bold: true, fontSize: 12.5, align: "center", valign: "middle" } },
      { text: "对应课程技术点", options: { fill: { color: C.accent2 }, color: C.white, bold: true, fontSize: 12.5, align: "center", valign: "middle" } },
    ],
    ...rows.map((r) => [
      { text: r[0], options: { fill: { color: C.light2 }, color: C.primary, bold: true, fontSize: 10.5, align: "center", valign: "middle" } },
      { text: r[1], options: { fill: { color: C.white }, color: C.text, fontSize: 10, align: "left", valign: "middle" } },
      { text: r[2], options: { fill: { color: C.light }, color: C.accent2, bold: true, fontSize: 10, align: "center", valign: "middle" } },
    ]),
  ], { x: 0.5, y: 1.3, w: 9, colW: [2.3, 4.4, 2.3], rowH: 0.42, border: { pt: 1, color: C.line }, margin: 0 });
  s.render();
}

// ============================================================
// SLIDE 9: 3.2 用户注册与登录（JWT 会话管理）
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 3 章", "3.2 用户注册与登录 · JWT 会话管理", "课程技术点：会话管理");
  card(s, 0.5, 1.35, 3.0, 2.1, "注册", [
    "学号/工号 + 手机号唯一性校验",
    "密码加盐哈希存储，禁止明文",
    "注册成功后即可登录",
  ], { fill: C.card });
  card(s, 0.5, 3.6, 3.0, 1.75, "登录校验", [
    "账号密码登录，签发 JWT 令牌",
    "令牌携带用户身份与过期时间",
    "退出登录或过期后令牌失效",
  ], { fill: C.card, bar: C.secondary });
  // 右侧：JWT 流程（原生形状）
  s.addText("会话管理流程", { x: 3.75, y: 1.35, w: 5.6, h: 0.32, fontFace: F_BODY, fontSize: 14, bold: true, color: C.primary, margin: 0 });
  const steps = [
    ["登录请求", "客户端提交账号密码", C.primary],
    ["校验签发", "服务端验证通过后签发 JWT 令牌", C.primary2],
    ["携带令牌", "前端统一存储，请求头携带 Token", C.secondary],
    ["鉴权放行", "拦截器校验有效性/权限，放行接口", C.accent2],
  ];
  const nodeW = 5.3, nodeH = 0.72, gapV = 1.0;
  steps.forEach((st, i) => {
    const y = 1.8 + i * gapV;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 3.75, y, w: nodeW, h: nodeH, fill: { color: st[2] }, rectRadius: 0.1, shadow });
    s.addText(st[0], { x: 3.95, y: y + 0.08, w: 1.7, h: 0.3, fontFace: F_BODY, fontSize: 11.5, bold: true, color: C.white, margin: 0 });
    s.addText(st[1], { x: 3.95, y: y + 0.36, w: 4.9, h: 0.32, fontFace: F_BODY, fontSize: 9.5, color: "EAF3F9", margin: 0 });
    if (i < steps.length - 1) {
      s.addShape(pres.shapes.LINE, { x: 6.4, y: y + nodeH, w: 0, h: gapV - nodeH, line: { color: C.secondary, width: 1.5, endArrowType: "triangle" } });
    }
  });
  s.render();
}

// ============================================================
// SLIDE 10: 3.3 发布代取需求 + 3.4 接单处理（抢单并发）
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 3 章", "3.3 发布代取需求 / 3.4 接单处理 · 抢单并发", "增删改查 · 用户关联查询 + 事务");
  // 上半：发布流程
  s.addText("发布流程", { x: 0.5, y: 1.25, w: 4.4, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  const flow = ["填写需求信息", "提交需求", "校验与入库", "进入待接单"];
  const fw = 1.75, fgap = 0.24;
  const fx0 = (10 - (flow.length * fw + (flow.length - 1) * fgap)) / 2;
  flow.forEach((f, i) => {
    const x = fx0 + i * (fw + fgap);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.62, w: fw, h: 0.55, fill: { color: [C.primary, C.primary2, C.secondary, C.accent2][i] }, rectRadius: 0.12, shadow });
    s.addText(f, { x, y: 1.62, w: fw, h: 0.55, align: "center", valign: "middle", fontFace: F_BODY, fontSize: 11.5, bold: true, color: C.white, margin: 0 });
    if (i < flow.length - 1) {
      s.addShape(pres.shapes.LINE, { x: x + fw, y: 1.9, w: fgap, h: 0, line: { color: C.secondary, width: 1.8, endArrowType: "triangle" } });
    }
  });
  const fields = [
    ["取件点", "驿站/快递柜位置"],
    ["送达点", "宿舍楼栋与房间号"],
    ["期望时间", "送达截止时间"],
    ["报酬", "自愿定价 ≥0"],
    ["备注", "包裹大小等说明"],
  ];
  fields.forEach((f, i) => {
    const x = 0.5 + i * 1.82;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 2.3, w: 1.72, h: 0.8, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08 });
    s.addText(f[0], { x, y: 2.38, w: 1.72, h: 0.28, align: "center", fontFace: F_BODY, fontSize: 11, bold: true, color: C.primary, margin: 0 });
    s.addText(f[1], { x, y: 2.66, w: 1.72, h: 0.38, align: "center", fontFace: F_BODY, fontSize: 8, color: C.muted, margin: 0 });
  });
  // 下半：接单（乐观锁 + 事务）
  s.addText("接单并发控制：多人同时抢单，同一订单只能被一人承接", { x: 0.5, y: 3.32, w: 9, h: 0.32, fontFace: F_BODY, fontSize: 12.5, bold: true, color: C.accent2, margin: 0 });
  card(s, 0.5, 3.7, 4.4, 1.32, "乐观锁（version 字段）", [
    "UPDATE ... WHERE id=? AND version=?，影响 0 行即接单失败",
    "失败提示“手慢一步”，刷新重试",
  ], { fill: C.card });
  card(s, 5.1, 3.7, 4.4, 1.32, "事务（@Transactional）", [
    "状态更新 + 历史记录写入原子执行，失败整体回滚",
    "同一订单不会出现重复接单数据",
  ], { fill: C.card, bar: C.secondary });
  s.render();
}

// ============================================================
// SLIDE 11: 3.5 订单状态流转（状态机）
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 3 章", "3.5 订单状态流转 · 状态机设计", "课程技术点：订单状态流转");
  const states = [
    ["待接单", "已发布待承接", C.primary],
    ["已接单", "接单者已承接", C.primary2],
    ["取件中", "已到驿站取件", C.secondary],
    ["配送中", "送往送达点", "3E8FB5"],
    ["已完成", "双方确认收货", C.accent2],
  ];
  const nw = 1.62, nh = 0.85, ng = 0.22;
  const nx0 = (10 - (states.length * nw + (states.length - 1) * ng)) / 2;
  const ny = 1.75;
  states.forEach((st, i) => {
    const x = nx0 + i * (nw + ng);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: ny, w: nw, h: nh, fill: { color: st[2] }, rectRadius: 0.1, shadow });
    s.addText(st[0], { x, y: ny + 0.1, w: nw, h: 0.32, align: "center", fontFace: F_BODY, fontSize: 13, bold: true, color: C.white, margin: 0 });
    s.addText(st[1], { x, y: ny + 0.46, w: nw, h: 0.3, align: "center", fontFace: F_BODY, fontSize: 7.5, color: "EAF3F9", margin: 0 });
    if (i < states.length - 1) {
      s.addShape(pres.shapes.LINE, { x: x + nw, y: ny + nh / 2, w: ng, h: 0, line: { color: C.accent, width: 2, endArrowType: "triangle" } });
    }
  });
  // 取消分支
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 3.1, y: 3.1, w: 1.7, h: 0.7, fill: { color: "8A97A5" }, rectRadius: 0.1, shadow });
  s.addText("已取消", { x: 3.1, y: 3.22, w: 1.7, h: 0.3, align: "center", fontFace: F_BODY, fontSize: 12, bold: true, color: C.white, margin: 0 });
  s.addText("待接单/已接单/取件中可取消", { x: 3.1, y: 3.52, w: 1.7, h: 0.25, align: "center", fontFace: F_BODY, fontSize: 7, color: "E8EEF4", margin: 0 });
  s.addShape(pres.shapes.LINE, { x: 3.95, y: 3.1, w: 0.5, h: -0.25, line: { color: "8A97A5", width: 1.5, endArrowType: "triangle" } });
  s.addShape(pres.shapes.LINE, { x: 3.95, y: 2.6, w: 0, h: 0.2, line: { color: "8A97A5", width: 1.5 } });
  s.addText("状态规则", { x: 0.5, y: 4.05, w: 9, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 4.4, w: 9, h: 0.85, fill: { color: C.light2 }, rectRadius: 0.08 });
  s.addText("规则：状态只能沿合法路径迁移（R-1~R-5）；每次变更写入状态历史表（R-6），支持全程追踪；完成/取消后订单锁定不可再变更。", {
    x: 0.75, y: 4.4, w: 8.5, h: 0.85, fontFace: F_BODY, fontSize: 11.5, color: C.text, valign: "middle", margin: 0, autoFit: false, fit: "none",
  });
  s.render();
}

// ============================================================
// SLIDE 12: 3.6 消息留言 + 3.9 评价与信誉管理
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 3 章", "3.6 消息留言 / 3.9 评价与信誉管理", "会话管理 · 关联查询 + 信誉机制");
  // 左：留言
  s.addText("消息留言 · 订单会话", { x: 0.5, y: 1.25, w: 4.4, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  card(s, 0.5, 1.62, 4.4, 1.62, "设计定位：跟帖式留言板", [
    "每个订单即一个会话，order_id 即会话 ID",
    "发布者与接单者围绕订单随时沟通，无需实时推送",
  ], { fill: C.card });
  card(s, 0.5, 3.38, 4.4, 1.62, "核心接口（3 个）", [
    "发送留言：INSERT 新增记录，关联订单与发送者",
    "查询列表：按 order_id 查询并 JOIN t_user 显示昵称",
    "标记已读：UPDATE is_read，未读角标计数",
  ], { fill: C.card, bar: C.secondary });
  // 右：评价信誉
  s.addText("双向评价 · 信誉分机制", { x: 5.1, y: 1.25, w: 4.4, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  card(s, 5.1, 1.62, 4.4, 1.62, "双向评价", [
    "订单完成后双方互评：1~5 星 + 标签，避免重复评价",
    "初始 100 分；好评 +1、差评 -2、恶意取消 -5",
    "分值划分等级，等级决定同时接单上限",
  ], { fill: C.card });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 5.1, y: 3.38, w: 4.4, h: 1.62, fill: { color: C.primary }, rectRadius: 0.1, shadow });
  s.addText([
    { text: "信誉分计算示例", options: { breakLine: true, fontSize: 12, color: C.accent, bold: true } },
    { text: "小张初始 100 分，完成 10 单（9 好评 + 1 差评）", options: { breakLine: true, fontSize: 10.5, color: "D7E4EF" } },
    { text: "100 + 9×1 - 1×2 = 107 分", options: { breakLine: true, fontSize: 14, color: C.white, bold: true } },
    { text: "等级“良好”，同时接单上限 3 单 → 5 单", options: { fontSize: 10, color: "D7E4EF" } },
  ], { x: 5.4, y: 3.52, w: 3.8, h: 1.36, fontFace: F_BODY, margin: 0, valign: "middle" });
  s.render();
}

// ============================================================
// SLIDE 14: 3.7 订单中心 + 3.8 需求列表
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 3 章", "3.7 个人订单中心 / 3.8 需求列表浏览", "订单全生命周期管理");
  card(s, 0.5, 1.35, 4.4, 3.85, "个人订单中心", [
    "我的发布：发布中订单状态一览、撤销/编辑",
    "我的接单：待取/取件中/配送中任务提醒",
    "订单详情：状态历史时间线 + 留言会话 + 评价入口",
    "数据统计：累计订单数、完成率、信誉分变化",
    "按状态筛选（进行中/已完成/已取消）快速定位",
  ], { fill: C.card });
  card(s, 5.1, 1.35, 4.4, 3.85, "需求大厅（列表浏览）", [
    "按发布时间倒序展示所有待接单需求",
    "状态筛选：只看待接单，提高抢单效率",
    "分页展示，默认每页 10 条",
    "卡片式布局：取件点/送达点/报酬一目了然",
    "需求方也可浏览自己的需求被浏览情况",
  ], { fill: C.card, bar: C.secondary });
  s.render();
}

// ============================================================
// SLIDE 15: 4. 非功能需求
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 4 章", "4.1 ~ 4.6 非功能需求", "六类质量属性");
  const nfr = [
    ["性能", "并发 200 请求/秒稳定运行；页面响应 <3s；数据库查询 <1s", C.primary],
    ["安全", "密码加盐哈希；JWT 鉴权；防 SQL 注入；操作权限校验", C.primary2],
    ["可用性", "界面统一、操作 ≤3 步可达；错误提示清晰；帮助说明齐全", C.secondary],
    ["可靠性", "事务保证数据一致；异常自动回滚；关键操作可恢复", "3E8FB5"],
    ["可维护性", "分层架构；接口文档化；代码注释规范；日志可查", C.accent2],
    ["兼容性", "支持 Chrome/Edge 主流浏览器；1080p 屏幕适配；跨平台 Web", "C77D1F"],
  ];
  nfr.forEach((n, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = 0.5 + col * 4.62;
    const y = 1.35 + row * 1.28;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 4.38, h: 1.12, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
    s.addShape(pres.shapes.RECTANGLE, { x: x + 0.02, y: y + 0.18, w: 0.07, h: 0.76, fill: { color: n[2] } });
    s.addText(n[0], { x: x + 0.25, y: y + 0.12, w: 1.2, h: 0.32, fontFace: F_BODY, fontSize: 14, bold: true, color: n[2], margin: 0 });
    s.addText(n[1], { x: x + 0.25, y: y + 0.46, w: 4.0, h: 0.6, fontFace: F_BODY, fontSize: 10, color: C.text, margin: 0, valign: "top", autoFit: false, fit: "none" });
  });
  s.render();
}

// ============================================================
// SLIDE 17: 5.1 数据实体 + 5.2 核心表
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 5 章", "5.1 数据实体与关系 / 5.2 核心表设计", "5 大核心实体 · 10 张表");
  s.addText("实体关系", { x: 0.5, y: 1.25, w: 4.4, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  const ents = [
    ["用户", "1:N 订单 / N:N 评价", C.primary],
    ["订单", "1:N 状态历史 / 1:N 留言", C.primary2],
    ["状态历史", "记录每次状态变更", C.secondary],
    ["留言", "订单会话内多条", "3E8FB5"],
    ["评价", "订单完成后 1~2 条", C.accent2],
  ];
  const ew = 1.72, eh = 0.78, eg = 0.24;
  ents.forEach((e, i) => {
    const x = 0.5 + i * (ew + eg);
    const y = 1.62;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: ew, h: eh, fill: { color: e[2] }, rectRadius: 0.08, shadow });
    s.addText(e[0], { x, y: y + 0.08, w: ew, h: 0.3, align: "center", fontFace: F_BODY, fontSize: 12, bold: true, color: C.white, margin: 0 });
    s.addText(e[1], { x, y: y + 0.4, w: ew, h: 0.32, align: "center", fontFace: F_BODY, fontSize: 7.5, color: "EAF3F9", margin: 0 });
    if (i < ents.length - 1) {
      s.addShape(pres.shapes.LINE, { x: x + ew, y: y + eh / 2, w: eg, h: 0, line: { color: C.muted, width: 1.2, endArrowType: "triangle" } });
    }
  });
  s.addText("核心数据表（5.2）", { x: 0.5, y: 2.75, w: 9, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  s.addTable([
    [
      { text: "表名", options: { fill: { color: C.primary }, color: C.white, bold: true, fontSize: 11, align: "center", valign: "middle" } },
      { text: "用途", options: { fill: { color: C.primary }, color: C.white, bold: true, fontSize: 11, align: "center", valign: "middle" } },
      { text: "关键字段", options: { fill: { color: C.primary }, color: C.white, bold: true, fontSize: 11, align: "center", valign: "middle" } },
    ],
    [
      ["t_user", "用户信息", "user_id, account, phone, password, credit"],
      ["t_order", "代取订单", "order_id, publisher, taker, status, reward, version"],
      ["t_status_history", "状态变更历史", "history_id, order_id, status, change_time"],
      ["t_message", "订单留言", "message_id, order_id, sender, content, is_read"],
      ["t_evaluation", "双向评价", "evaluation_id, order_id, from_user, star, tag"],
    ].map((r) => [
      { text: r[0], options: { fill: { color: C.light2 }, color: C.primary, bold: true, fontSize: 10.5, align: "center", valign: "middle" } },
      { text: r[1], options: { fill: { color: C.white }, color: C.text, fontSize: 10, align: "left", valign: "middle" } },
      { text: r[2], options: { fill: { color: C.white }, color: C.text, fontSize: 9.5, align: "left", valign: "middle" } },
    ]),
  ], { x: 0.5, y: 3.12, w: 9, colW: [2.0, 2.3, 4.7], rowH: 0.4, border: { pt: 1, color: C.line }, margin: 0 });
  s.render();
}

// ============================================================
// SLIDE 18: 5.3 完整性 + 5.4 数据量
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 5 章", "5.3 数据完整性 / 5.4 数据量估算", "约束设计 + 规模评估");
  const cons = [
    ["实体完整性", "主键唯一且非空"],
    ["参照完整性", "外键须关联存在记录，防孤儿数据"],
    ["域完整性", "状态取值受限、报酬≥0、星级 1~5"],
    ["业务完整性", "事务保证关联操作原子性"],
  ];
  cons.forEach((c, i) => {
    const x = 0.5 + i * 2.32;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.3, w: 2.16, h: 1.5, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
    numBadge(s, x + 0.15, 1.45, i + 1, 0.4, C.primary);
    s.addText(c[0], { x: x + 0.65, y: 1.48, w: 1.45, h: 0.35, fontFace: F_BODY, fontSize: 11.5, bold: true, color: C.primary, margin: 0 });
    s.addText(c[1], { x: x + 0.16, y: 1.95, w: 1.86, h: 0.8, fontFace: F_BODY, fontSize: 9, color: C.text, margin: 0, valign: "top", autoFit: false, fit: "none" });
  });
  s.addText("数据规模（运行一学期）", { x: 0.5, y: 3.05, w: 9, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  const stats = [
    ["用户", "1,000", "注册用户约 1000 人"],
    ["订单", "12,000", "日均新增约 100 笔"],
    ["留言", "48,000", "月度约 12000 条"],
    ["评价", "24,000", "订单完成后产生"],
    ["状态历史", "36,000", "每单平均 3 次变更"],
  ];
  stats.forEach((st, i) => {
    const x = 0.5 + i * 1.82;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 3.45, w: 1.72, h: 1.35, fill: { color: C.light2 }, rectRadius: 0.08 });
    s.addText(st[1], { x, y: 3.55, w: 1.72, h: 0.5, align: "center", fontFace: F_TITLE, fontSize: 20, bold: true, color: C.primary, margin: 0 });
    s.addText(st[0], { x, y: 4.08, w: 1.72, h: 0.3, align: "center", fontFace: F_BODY, fontSize: 10.5, bold: true, color: C.accent2, margin: 0 });
    s.addText(st[2], { x, y: 4.36, w: 1.72, h: 0.4, align: "center", fontFace: F_BODY, fontSize: 7.5, color: C.muted, margin: 0 });
  });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 4.95, w: 9, h: 0.3, fill: { color: C.primary }, rectRadius: 0.05 });
  s.addText("总量约十万条以内，属轻量级：单库单表 + 合理索引即可满足性能要求", {
    x: 0.5, y: 4.95, w: 9, h: 0.3, align: "center", fontFace: F_BODY, fontSize: 10.5, bold: true, color: C.white, margin: 0,
  });
  s.render();
}

// ============================================================
// SLIDE 19: 6. 业务规则
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 6 章", "6.1 ~ 6.6 业务规则", "规则 R-1 ~ R-25");
  const rules = [
    ["状态流转", "R-1~R-5 合法路径迁移；R-6 每次变更记历史", C.primary],
    ["接单抢单", "R-7~R-10 同一订单仅一人承接；乐观锁防重复", C.primary2],
    ["订单取消", "R-11~R-13 待接单可撤；接单后协商取消并扣信誉", C.secondary],
    ["超时处理", "R-14~R-17 接单/取件/配送超时自动提醒降级", "3E8FB5"],
    ["报酬结算", "R-18~R-21 完成后自动确认；双方可协商调整", C.accent2],
    ["评价信誉", "R-22~R-25 好评+1 差评-2；等级决定接单上限", "C77D1F"],
  ];
  rules.forEach((r, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = 0.5 + col * 4.62;
    const y = 1.35 + row * 1.28;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 4.38, h: 1.12, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
    s.addShape(pres.shapes.RECTANGLE, { x: x + 0.02, y: y + 0.18, w: 0.07, h: 0.76, fill: { color: r[2] } });
    s.addText(r[0], { x: x + 0.25, y: y + 0.12, w: 1.4, h: 0.32, fontFace: F_BODY, fontSize: 14, bold: true, color: r[2], margin: 0 });
    s.addText(r[1], { x: x + 0.25, y: y + 0.46, w: 4.0, h: 0.6, fontFace: F_BODY, fontSize: 10.5, color: C.text, margin: 0, valign: "top", autoFit: false, fit: "none" });
  });
  s.render();
}

// ============================================================
// SLIDE 20: 7. 外部接口需求
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 7 章", "7.1 外部接口需求概述", "本轮以站内自实现为主");
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 1.3, w: 9, h: 1.0, fill: { color: C.primary }, rectRadius: 0.08, shadow });
  s.addText("核心策略：通知提醒站内化、验证码本地生成校验、位置数据静态描述——不依赖任何第三方外部接口，核心业务闭环自洽。", {
    x: 0.8, y: 1.3, w: 8.4, h: 1.0, fontFace: F_BODY, fontSize: 14, color: C.white, valign: "middle", margin: 0, autoFit: false, fit: "none",
  });
  const why = [
    ["业务闭环自足", "站内方式已覆盖全部课程技术点，核心功能不依赖第三方", C.primary],
    ["成本与资质", "短信/支付等需企业资质与费用申请，校园环境不便获取", C.primary2],
    ["周期优先", "3 人小组开发周期有限，优先保证核心质量与测试充分性", C.accent2],
  ];
  why.forEach((w, i) => {
    const y = 2.55 + i * 0.92;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y, w: 9, h: 0.76, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
    numBadge(s, 0.75, y + 0.18, i + 1, 0.4, w[2]);
    s.addText(w[0], { x: 1.35, y: y + 0.1, w: 1.9, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: w[2], margin: 0 });
    s.addText(w[1], { x: 1.35, y: y + 0.4, w: 7.9, h: 0.32, fontFace: F_BODY, fontSize: 10.5, color: C.text, margin: 0 });
  });
  s.addText("后续如需对接短信、地图、支付、推送等外部接口，可基于本章框架扩展设计。", {
    x: 0.5, y: 5.0, w: 9, h: 0.35, fontFace: F_BODY, fontSize: 10.5, color: C.muted, align: "center", margin: 0,
  });
  s.render();
}

// ============================================================
// SLIDE 21: 8.1 总体架构
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 8 章", "8.1 总体架构：前后端分离 B/S 三层架构", "技术选型建议");
  const layers = [
    { name: "表现层", tech: "Vue 3 + Element Plus（SPA）", color: C.accent2, items: ["Vue Router 路由 / Pinia 状态 / Axios 请求封装", "页面：需求大厅 · 订单中心 · 留言会话 · 个人中心"] },
    { name: "服务层", tech: "Spring Boot 3.x RESTful API", color: C.primary, items: ["业务逻辑 / MyBatis 持久化 / JWT 鉴权拦截器", "事务 @Transactional + 乐观锁 version"] },
    { name: "数据层", tech: "MySQL 8.0 关系型数据库", color: C.primary2, items: ["5 张核心表 + 关联表，utf8mb4 字符集", "索引优化：order_id / publisher_id / taker_id / status"] },
  ];
  layers.forEach((l, i) => {
    const y = 1.35 + i * 1.36;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y, w: 9, h: 1.18, fill: { color: l.color }, rectRadius: 0.1, shadow });
    s.addText(l.name, { x: 0.8, y: y + 0.12, w: 2.3, h: 0.4, fontFace: F_TITLE, fontSize: 17, bold: true, color: C.white, margin: 0 });
    s.addText(l.tech, { x: 0.8, y: y + 0.5, w: 4.2, h: 0.32, fontFace: F_BODY, fontSize: 10.5, color: "EAF3F9", margin: 0 });
    const items = l.items.map((t, j) => ({ text: t, options: { bullet: true, paraSpaceAfter: 2, fontSize: 9.5, color: C.white, fontFace: F_BODY, breakLine: true } }));
    items[items.length - 1].options.breakLine = false;
    s.addText(items, { x: 5.2, y: y + 0.12, w: 4.1, h: 1.0, valign: "middle", margin: 0, fit: "shrink" });
    if (i < layers.length - 1) {
      s.addShape(pres.shapes.LINE, { x: 5.0, y: y + 1.18, w: 0, h: 0.18, line: { color: C.muted, width: 1.2, endArrowType: "triangle" } });
    }
  });
  s.render();
}

// ============================================================
// SLIDE 22: 8.2 ~ 8.5 技术栈 · 数据库 · 开发运行环境
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 8 章", "8.2 ~ 8.5 技术栈 / 数据库 / 开发运行环境", "统一技术栈 + 部署准备");
  const be = [
    ["Java 17", "开发语言，LTS 长期支持版本"],
    ["Spring Boot 3.x", "核心框架，自动配置 + 依赖管理"],
    ["MyBatis", "持久层，XML/注解写 SQL"],
    ["JWT 令牌", "无状态认证，适配前后端分离"],
    ["乐观锁+事务", "@Transactional + version 字段"],
  ];
  const fe = [
    ["Vue 3", "组合式 API，响应式数据绑定"],
    ["Element Plus", "组件库，开箱即用"],
    ["Vite", "构建工具，开发热更新"],
    ["Vue Router + Axios", "前端路由与请求封装"],
    ["Pinia", "状态管理，全局会话信息"],
  ];
  s.addText("后端技术栈", { x: 0.5, y: 1.28, w: 4.4, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  be.forEach((b, i) => {
    const y = 1.66 + i * 0.58;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y, w: 4.4, h: 0.48, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08 });
    s.addText(b[0], { x: 0.7, y: y + 0.04, w: 2.0, h: 0.4, fontFace: F_BODY, fontSize: 11, bold: true, color: C.primary, margin: 0 });
    s.addText(b[1], { x: 2.7, y: y + 0.04, w: 2.15, h: 0.4, fontFace: F_BODY, fontSize: 8.5, color: C.muted, margin: 0 });
  });
  s.addText("前端技术栈", { x: 5.1, y: 1.28, w: 4.4, h: 0.32, fontFace: F_BODY, fontSize: 13, bold: true, color: C.primary, margin: 0 });
  fe.forEach((f, i) => {
    const y = 1.66 + i * 0.58;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 5.1, y, w: 4.4, h: 0.48, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08 });
    s.addText(f[0], { x: 5.3, y: y + 0.04, w: 2.0, h: 0.4, fontFace: F_BODY, fontSize: 11, bold: true, color: C.accent2, margin: 0 });
    s.addText(f[1], { x: 7.3, y: y + 0.04, w: 2.15, h: 0.4, fontFace: F_BODY, fontSize: 8.5, color: C.muted, margin: 0 });
  });
  // 底部：数据库与开发运行环境
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 4.62, w: 9, h: 0.62, fill: { color: C.light2 }, rectRadius: 0.08 });
  s.addText("数据库：MySQL 8.0+（utf8mb4，order_id / publisher_id / taker_id / status 索引，Redis 仅作可选缓存）", {
    x: 0.7, y: 4.62, w: 8.6, h: 0.3, fontFace: F_BODY, fontSize: 10, bold: true, color: C.primary, margin: 0,
  });
  s.addText("开发运行：IntelliJ IDEA + VS Code · JDK 17 + Node.js 18+ · Maven 3.8+ · Navicat · 实验室服务器部署", {
    x: 0.7, y: 4.9, w: 8.6, h: 0.3, fontFace: F_BODY, fontSize: 9.5, color: C.text, margin: 0,
  });
  s.render();
}

// ============================================================
// SLIDE 24: 8.6 三人分工 · 四阶段迭代开发（左右对照版式）
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.light };
  contentHeader(s, "第 8 章", "三人分工 · 四阶段迭代开发", "8.6 分工与计划");
  // 左侧：小组分工表（绿色表头 + 成员/主要负责模块）
  const mh = C.primary2;
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 1.45, w: 4.1, h: 3.5, fill: { color: C.card }, line: { color: C.line, width: 1 }, rectRadius: 0.08, shadow });
  s.addText("小组分工", { x: 0.75, y: 1.62, w: 3.6, h: 0.35, fontFace: F_BODY, fontSize: 14, bold: true, color: C.primary, margin: 0 });
  s.addShape(pres.shapes.RECTANGLE, { x: 0.75, y: 2.0, w: 1.1, h: 0.045, fill: { color: C.primary2 } });
  const divRows = [
    ["成员一", "数据库设计 · ER图与表结构 · 索引约束 · 初始化测试数据"],
    ["成员二", "订单逻辑后端 · 状态流转 · JWT会话管理 · 接单事务与乐观锁"],
    ["成员三", "前端展示 · 前后端联调 · 测试用例与测试汇总"],
  ];
  divRows.forEach((r, i) => {
    const y = 2.22 + i * 0.9;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.75, y, w: 3.6, h: 0.78, fill: { color: i % 2 === 0 ? C.light2 : C.white }, line: { color: C.line, width: 1 }, rectRadius: 0.06 });
    s.addText(r[0], { x: 0.9, y: y + 0.08, w: 0.85, h: 0.3, fontFace: F_BODY, fontSize: 11, bold: true, color: C.primary2, margin: 0 });
    s.addText(r[1], { x: 1.78, y: y + 0.06, w: 2.5, h: 0.66, fontFace: F_BODY, fontSize: 8.5, color: C.text, valign: "middle", margin: 0, autoFit: false, fit: "none" });
  });
  s.addShape(pres.shapes.RECTANGLE, { x: 4.62, y: 1.45, w: 0.03, h: 3.5, fill: { color: C.line } });
  // 右侧：四阶段开发计划
  s.addText("四阶段开发计划", { x: 4.95, y: 1.45, w: 4.5, h: 0.35, fontFace: F_BODY, fontSize: 14, bold: true, color: C.primary, margin: 0 });
  s.addShape(pres.shapes.RECTANGLE, { x: 4.95, y: 1.83, w: 1.1, h: 0.045, fill: { color: C.accent } });
  const stages = [
    ["第一阶段", "需求与设计", "需求分析 · ER图 · 接口文档 · 原型设计", C.primary],
    ["第二阶段", "核心功能开发", "用户管理 · 需求发布 · 接单处理 · 状态流转", C.primary2],
    ["第三阶段", "功能完善与联调", "消息留言 · 评价信誉 · 前后端联调", C.secondary],
    ["第四阶段", "测试与验收", "功能测试 · 集成测试 · 测试报告 · 答辩材料", C.accent2],
  ];
  stages.forEach((st, i) => {
    const y = 2.06 + i * 0.75;
    s.addShape(pres.shapes.OVAL, { x: 4.95, y: y + 0.05, w: 0.34, h: 0.34, fill: { color: st[3] } });
    s.addText(String(i + 1), { x: 4.95, y: y + 0.05, w: 0.34, h: 0.34, align: "center", valign: "middle", fontFace: F_BODY, fontSize: 12, bold: true, color: C.white, margin: 0 });
    s.addText(st[0] + " · " + st[1], { x: 5.45, y: y + 0.03, w: 3.9, h: 0.3, fontFace: F_BODY, fontSize: 11.5, bold: true, color: st[3], margin: 0 });
    s.addText(st[2], { x: 5.45, y: y + 0.33, w: 3.9, h: 0.32, fontFace: F_BODY, fontSize: 8.5, color: C.muted, margin: 0 });
  });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 5.08, w: 9, h: 0.42, fill: { color: C.light2 }, rectRadius: 0.08 });
  s.addText("协作机制：接口文档先行（Swagger/Apifox）→ 成员二定义接口 → 成员一补表结构 → 成员三按接口开发页面；每个阶段小组例会同步进度。", {
    x: 0.7, y: 5.08, w: 8.6, h: 0.42, fontFace: F_BODY, fontSize: 10, color: C.text, valign: "middle", margin: 0, autoFit: false, fit: "none",
  });
  s.render();
}

// ============================================================
// SLIDE 26: 8.9 总结 + 致谢
// ============================================================
{
  const s = pres.addSlide();
  s.background = { color: C.dark };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: 10, h: 0.1, fill: { color: C.accent } });
  s.addText("总结", { x: 0.9, y: 0.55, w: 3, h: 0.6, fontFace: F_TITLE, fontSize: 30, bold: true, color: C.white, margin: 0 });
  const sum = [
    ["需求明确", "5 项核心功能 + 4 项课程技术点全覆盖，范围聚焦可交付"],
    ["技术可行", "Spring Boot + MyBatis + MySQL + Vue3 + JWT，栈内技术风险低"],
    ["设计落地", "状态机、乐观锁、信誉机制等关键设计已细化到规则与字段级"],
    ["分工清晰", "3 人按数据库/后端/前端分工，四阶段迭代计划可控"],
  ];
  sum.forEach((t, i) => {
    const y = 1.4 + i * 0.72;
    s.addShape(pres.shapes.OVAL, { x: 0.95, y: y + 0.08, w: 0.45, h: 0.45, fill: { color: [C.accent, C.primary2, C.secondary, "C77D1F"][i] } });
    s.addText(String(i + 1), { x: 0.95, y: y + 0.08, w: 0.45, h: 0.45, align: "center", valign: "middle", fontFace: F_BODY, fontSize: 16, bold: true, color: C.white, margin: 0 });
    s.addText(t[0], { x: 1.6, y: y, w: 1.7, h: 0.4, fontFace: F_BODY, fontSize: 14, bold: true, color: C.accent, margin: 0 });
    s.addText(t[1], { x: 3.3, y: y + 0.02, w: 6.2, h: 0.5, fontFace: F_BODY, fontSize: 11, color: "D7E4EF", margin: 0, autoFit: false, fit: "none" });
  });
  s.addShape(pres.shapes.LINE, { x: 0.9, y: 4.4, w: 8.2, h: 0, line: { color: C.dark2, width: 1.5 } });
  s.addText("谢谢观看 · 恳请老师批评指正", { x: 0.9, y: 4.5, w: 8.2, h: 0.6, align: "center", fontFace: F_TITLE, fontSize: 24, bold: true, color: C.white, margin: 0, charSpacing: 1.5 });
  s.render();
}

// ============================================================
pres.writeFile({ fileName: "校园快递代取平台-需求分析报告-汇报PPT.pptx" }).then((f) => {
  console.log("已生成:", f);
});
