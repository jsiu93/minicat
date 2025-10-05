<template>
  <div>
    <v-card>
      <v-card-title class="text-h5">表结构比对</v-card-title>
      <v-card-text>
        <v-row>
          <v-col cols="12" md="6">
            <v-select
              v-model="sourceConnection"
              :items="connections"
              item-title="name"
              item-value="id"
              label="源数据库"
              outlined
            ></v-select>
          </v-col>
          <v-col cols="12" md="6">
            <v-select
              v-model="targetConnection"
              :items="connections"
              item-title="name"
              item-value="id"
              label="目标数据库"
              outlined
            ></v-select>
          </v-col>
        </v-row>
        
        <v-btn 
          color="primary" 
          large 
          block
          :disabled="!sourceConnection || !targetConnection"
          @click="startCompare"
        >
          <v-icon left>mdi-compare</v-icon>
          开始比对
        </v-btn>
        
        <v-divider class="my-6"></v-divider>
        
        <div v-if="diffResult">
          <h3 class="mb-4">比对结果</h3>
          <v-alert type="info" variant="tonal">
            功能开发中...
          </v-alert>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/services/api'

const connections = ref([])
const sourceConnection = ref(null)
const targetConnection = ref(null)
const diffResult = ref(null)

const loadConnections = async () => {
  try {
    connections.value = await api.connections.getAll()
  } catch (error) {
    console.error('Failed to load connections:', error)
  }
}

const startCompare = async () => {
  try {
    const task = await api.tasks.create('schema_compare')
    console.log('Task created:', task)
    diffResult.value = { message: '任务已创建，功能开发中...' }
  } catch (error) {
    console.error('Failed to start comparison:', error)
  }
}

onMounted(() => {
  loadConnections()
})
</script>
