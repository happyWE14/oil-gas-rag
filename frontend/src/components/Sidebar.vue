<template>
  <div class="sidebar-container">
    <div class="logo">
      <el-icon size="24" color="#fff"><Document /></el-icon>
      <span v-if="!collapse">论文分析系统</span>
    </div>

    <el-menu
        :default-active="activeMenu"
        :collapse="collapse"
        :collapse-transition="false"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
    >
      <el-menu-item index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <template #title>数据看板</template>
      </el-menu-item>

      <el-sub-menu index="/tasks">
        <template #title>
          <el-icon><List /></el-icon>
          <span>任务管理</span>
        </template>
        <el-menu-item index="/task-manager">任务中心</el-menu-item>
        <el-menu-item index="/tasks">任务列表</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="/search">
        <template #title>
          <el-icon><Search /></el-icon>
          <span>搜索管理</span>
        </template>
        <el-menu-item index="/search-configs">搜索配置</el-menu-item>
        <el-menu-item index="/keyword-search">关键词搜索</el-menu-item>
        <el-menu-item index="/deep-research">深度研究</el-menu-item>
      </el-sub-menu>

      <!-- 材料检索提升为一级菜单 -->
      <el-menu-item index="/material-search">
        <el-icon><Collection /></el-icon>
        <template #title>材料检索</template>
      </el-menu-item>

      <el-menu-item index="/papers">
        <el-icon><Document /></el-icon>
        <template #title>论文管理</template>
      </el-menu-item>

      <el-menu-item index="/custom-rag">
        <el-icon><ChatDotRound /></el-icon>
        <template #title>自定义 RAG</template>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Document, HomeFilled, List, Search, Collection, ChatDotRound } from '@element-plus/icons-vue'

const props = defineProps(['collapse'])
const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.sidebar-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 210px;
  height: 100%;
  background-color: #304156;
  transition: width 0.28s;
  z-index: 1001;
  overflow: hidden;
}

.logo {
  height: 50px;
  line-height: 50px;
  text-align: center;
  background: #2b3649;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  overflow: hidden;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.el-menu {
  height: calc(100% - 50px);
  border-right: none;
}
</style>
