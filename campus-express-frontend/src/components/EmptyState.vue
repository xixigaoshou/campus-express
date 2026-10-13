<script setup>
/**
 * 统一空状态占位组件
 * 列表无数据时展示统一风格与文案，可选带一个行动按钮（如「刷新」「去发布」），
 * 避免各页面空状态文案/样式不一致。
 */
defineProps({
  description: { type: String, default: '暂无数据' },
  actionText: { type: String, default: '' },
  imageSize: { type: Number, default: 90 }
})
defineEmits(['action'])
</script>

<template>
  <el-empty :description="description" :image-size="imageSize">
    <el-button v-if="actionText" type="primary" plain @click="$emit('action')">{{ actionText }}</el-button>
  </el-empty>
</template>