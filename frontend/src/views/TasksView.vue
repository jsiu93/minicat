<template>
  <div>
    <v-card>
      <v-card-title class="text-h5">任务列表</v-card-title>
      <v-card-text>
        <v-data-table
          :headers="headers"
          :items="tasks"
          :loading="loading"
          class="elevation-1"
        >
          <template v-slot:item.status="{ item }">
            <v-chip :color="getStatusColor(item.status)">
              {{ getStatusText(item.status) }}
            </v-chip>
          </template>
          
          <template v-slot:item.progress="{ item }">
            <v-progress-linear
              :model-value="item.progress"
              :color="getProgressColor(item.status)"
              height="20"
            >
              <strong>{{ item.progress }}%</strong>
            </v-progress-linear>
          </template>
          
          <template v-slot:item.type="{ item }">
            {{ getTypeText(item.type) }}
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/services/api'

const loading = ref(false)
const tasks = ref([])

const headers = [
  { title: 'ID', key: 'id' },
  { title: '类型', key: 'type' },
  { title: '状态', key: 'status' },
  { title: '进度', key: 'progress' },
  { title: '消息', key: 'message' },
  { title: '开始时间', key: 'startTime' }
]

const loadTasks = async () => {
  loading.value = true
  try {
    tasks.value = await api.tasks.getAll()
  } catch (error) {
    console.error('Failed to load tasks:', error)
  } finally {
    loading.value = false
  }
}

const getStatusColor = (status) => {
  const colors = {
    PENDING: 'grey',
    RUNNING: 'blue',
    COMPLETED: 'green',
    FAILED: 'red'
  }
  return colors[status] || 'grey'
}

const getStatusText = (status) => {
  const texts = {
    PENDING: '待处理',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return texts[status] || status
}

const getProgressColor = (status) => {
  return status === 'FAILED' ? 'error' : 'primary'
}

const getTypeText = (type) => {
  const texts = {
    schema_compare: '结构比对',
    schema_sync: '结构同步',
    data_compare: '数据比对',
    data_sync: '数据同步'
  }
  return texts[type] || type
}

onMounted(() => {
  loadTasks()
})
</script>
