<template>
  <div class="header-container">
    <div class="left-section">
      <el-icon class="hamburger" :size="20" @click="$emit('toggle-sidebar')">
        <Fold v-if="!collapse" />
        <Expand v-else />
      </el-icon>
      <!-- Element Plus 面包屑 -->
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>当前页面</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="right-section">
      <el-dropdown>
        <span class="user-info">
          管理员<el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item>个人中心</el-dropdown-item>
            <el-dropdown-item divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { Fold, Expand, ArrowDown } from '@element-plus/icons-vue'

defineProps(['collapse'])
defineEmits(['toggle-sidebar'])
</script>

<style scoped>
.header-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: 0 20px;
}

.hamburger {
  cursor: pointer;
  margin-right: 15px;
  vertical-align: middle;
  color: #606266;
}

.hamburger:hover {
  color: #409EFF;
}

.left-section {
  display: flex;
  align-items: center;
}

/* 面包屑样式调整 */
.breadcrumb {
  display: inline-flex;
  align-items: center;
  font-size: 14px;
}

.right-section {
  display: flex;
  align-items: center;
}

.user-info {
  cursor: pointer;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.user-info:hover {
  color: #409EFF;
}

/* 深度选择器定制面包屑样式 */
:deep(.el-breadcrumb__inner) {
  color: #606266;
}

:deep(.el-breadcrumb__inner.is-link:hover) {
  color: #409EFF;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #303133;
  font-weight: 600;
}
</style>
