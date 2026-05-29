<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

const themeConfig = {
  token: {
    colorPrimary: '#1677ff',
    colorInfo: '#1677ff',
    borderRadius: 6,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
  },
}
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="themeConfig">
    <router-view />
  </ConfigProvider>
</template>

<style>
* { box-sizing: border-box; }
html, body, #app {
  margin: 0;
  padding: 0;
  min-height: 100%;
}
body {
  background: #f0f2f5;
  color: rgba(0, 0, 0, 0.88);
  -webkit-font-smoothing: antialiased;
}
</style>
