import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
    { path: '/', redirect: '/dashboard' },
    {
        path: '/dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据看板' }
    },
    {
        path: '/task-manager',
        component: () => import('@/views/TaskManager.vue'),
        meta: { title: '任务中心' }
    },
    {
        path: '/tasks',
        component: () => import('@/views/TaskList.vue'),
        meta: { title: '任务列表' }
    },
    {
        path: '/tasks/:id',
        name: 'TaskDetail',
        component: () => import('@/views/TaskDetail.vue'),
        props: true  // 自动将 route.params.id 作为 taskId prop
    },
    {
        path: '/search-configs',
        component: () => import('@/views/SearchConfigList.vue'),
        meta: { title: '搜索配置' }
    },
    {
        path: '/search-configs/create',
        component: () => import('@/views/SearchConfigForm.vue'),
        meta: { title: '新建配置' }
    },
    {
        path: '/search-configs/:id/edit',
        component: () => import('@/views/SearchConfigForm.vue'),
        meta: { title: '编辑配置' }
    },
    {
        path: '/keyword-search',
        component: () => import('@/views/KeyWordSearch.vue'),
        meta: { title: '关键词搜索' }
    },
    {
        path: '/deep-research',
        component: () => import('@/views/DeepResearch.vue'),
        meta: { title: '深度研究' }
    },
    {
        path: '/papers',
        component: () => import('@/views/PaperList.vue'),
        meta: { title: '论文管理' }
    },
    {
        path: '/papers/:id',
        component: () => import('@/views/PaperDetail.vue'),
        meta: { title: '论文详情' }
    },
    {
        path: '/custom-rag',
        component: () => import('@/views/CustomRag.vue'),
        meta: { title: '自定义 RAG' }
    },
    // 404
    {
        path: '/:pathMatch(.*)*',
        component: () => import('@/views/NotFound.vue'),
        meta: { title: '404' }
    }, {
        path: '/material-search',
        component: () => import('@/views/MaterialSearch.vue'),
        meta: { title: '材料检索' }
    },

]

const router = createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior() {
        return { top: 0 }
    }
})

// 路由守卫
router.beforeEach((to, from, next) => {
    document.title = to.meta.title ? `${to.meta.title} - 论文分析系统` : '论文分析系统'
    next()
})

// 路由错误处理
router.onError((error) => {
    console.error('路由错误:', error)
    ElMessage.error('页面加载失败')
})

export default router
