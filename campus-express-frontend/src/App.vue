<script setup>
// 根组件：仅渲染路由出口，业务页面统一由 / 主布局（Home）承载
</script>

<template>
  <router-view />
</template>

<style>
/* 全局基础样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
}

body {
  font-family: 'Helvetica Neue', Arial, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', sans-serif;
  background: #f5f7fa;
  color: #303133;
}
</style>
