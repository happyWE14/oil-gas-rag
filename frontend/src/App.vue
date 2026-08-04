<template>
  <div id="app" class="app-wrapper">
    <!-- 侧边栏 -->
    <Sidebar :collapse="isCollapse" />

    <!-- 主容器 -->
    <div class="main-container" :class="{ 'main-collapse': isCollapse }">
      <!-- 顶部导航 -->
      <div class="fixed-header">
        <Header @toggle-sidebar="toggleSidebar" :collapse="isCollapse" />
      </div>

      <!-- 主内容区 -->
      <div class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Header from '@/components/Header.vue'
import Sidebar from '@/components/Sidebar.vue'

const isCollapse = ref(false)
const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }

.app-wrapper {
  position: relative;
  height: 100%;
  width: 100%;
  background: #f0f2f5;
}

/* 关键修复：确保侧边栏在最上层 */
.sidebar-container {
  position: fixed !important;
  top: 0;
  left: 0;
  width: 210px;
  height: 100%;
  background-color: #304156;
  z-index: 1002; /* 提高层级，确保在 Header(z-index:1000) 之上 */
  transition: width 0.28s;
  box-shadow: 2px 0 6px rgba(0,21,41,.35);
}

/* 折叠状态 */
.sidebar-collapse .sidebar-container {
  width: 64px;
}

/* 主容器 */
.main-container {
  min-height: 100vh;
  margin-left: 210px;
  transition: margin-left 0.28s;
  position: relative;
  background: #f0f2f5;
}

.main-collapse {
  margin-left: 64px;
}

/* 修复 Header 定位 */
.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 1001; /* 低于 sidebar，高于 content */
  width: calc(100% - 210px);
  transition: width 0.28s;
  background: #fff;
  box-shadow: 0 1px 3px 0 rgba(0,0,0,.12);
}

.main-collapse .fixed-header {
  width: calc(100% - 64px);
}

/* 主内容区：确保有内容撑开高度 */
.app-main {
  position: relative;
  padding: 70px 20px 20px; /* 给 fixed header 留 50px + 20px padding */
  min-height: 100vh;
  background: #f0f2f5;
}
</style>
