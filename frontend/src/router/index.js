import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/connections',
      name: 'connections',
      component: () => import('../views/ConnectionsView.vue')
    },
    {
      path: '/schema-diff',
      name: 'schema-diff',
      component: () => import('../views/SchemaDiffView.vue')
    },
    {
      path: '/data-diff',
      name: 'data-diff',
      component: () => import('../views/DataDiffView.vue')
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('../views/TasksView.vue')
    }
  ]
})

export default router
