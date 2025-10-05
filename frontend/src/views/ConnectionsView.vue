<template>
  <div>
    <v-card>
      <v-card-title>
        <span class="text-h5">数据库连接管理</span>
        <v-spacer></v-spacer>
        <v-btn color="primary" @click="showDialog = true">
          <v-icon left>mdi-plus</v-icon>
          添加连接
        </v-btn>
      </v-card-title>
      
      <v-card-text>
        <v-data-table
          :headers="headers"
          :items="connections"
          :loading="loading"
          class="elevation-1"
        >
          <template v-slot:item.type="{ item }">
            <v-chip :color="item.type === 'mysql' ? 'blue' : 'green'">
              {{ item.type.toUpperCase() }}
            </v-chip>
          </template>
          
          <template v-slot:item.actions="{ item }">
            <v-btn icon size="small" @click="testConnection(item.id)">
              <v-icon>mdi-lan-connect</v-icon>
            </v-btn>
            <v-btn icon size="small" @click="editConnection(item)">
              <v-icon>mdi-pencil</v-icon>
            </v-btn>
            <v-btn icon size="small" @click="deleteConnection(item.id)">
              <v-icon>mdi-delete</v-icon>
            </v-btn>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <!-- Add/Edit Dialog -->
    <v-dialog v-model="showDialog" max-width="600px">
      <v-card>
        <v-card-title>
          <span class="text-h5">{{ isEdit ? '编辑' : '添加' }}连接</span>
        </v-card-title>
        <v-card-text>
          <v-form ref="form">
            <v-text-field
              v-model="currentConnection.name"
              label="连接名称"
              required
            ></v-text-field>
            
            <v-select
              v-model="currentConnection.type"
              :items="['mysql', 'postgresql']"
              label="数据库类型"
              required
            ></v-select>
            
            <v-text-field
              v-model="currentConnection.host"
              label="主机"
              required
            ></v-text-field>
            
            <v-text-field
              v-model.number="currentConnection.port"
              label="端口"
              type="number"
              required
            ></v-text-field>
            
            <v-text-field
              v-model="currentConnection.database"
              label="数据库名"
              required
            ></v-text-field>
            
            <v-text-field
              v-model="currentConnection.username"
              label="用户名"
              required
            ></v-text-field>
            
            <v-text-field
              v-model="currentConnection.password"
              label="密码"
              type="password"
            ></v-text-field>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn @click="showDialog = false">取消</v-btn>
          <v-btn color="primary" @click="saveConnection">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/services/api'

const loading = ref(false)
const showDialog = ref(false)
const isEdit = ref(false)
const connections = ref([])
const currentConnection = ref({
  name: '',
  type: 'mysql',
  host: 'localhost',
  port: 3306,
  database: '',
  username: '',
  password: ''
})

const headers = [
  { title: '名称', key: 'name' },
  { title: '类型', key: 'type' },
  { title: '主机', key: 'host' },
  { title: '端口', key: 'port' },
  { title: '数据库', key: 'database' },
  { title: '操作', key: 'actions', sortable: false }
]

const loadConnections = async () => {
  loading.value = true
  try {
    connections.value = await api.connections.getAll()
  } catch (error) {
    console.error('Failed to load connections:', error)
  } finally {
    loading.value = false
  }
}

const saveConnection = async () => {
  try {
    if (isEdit.value) {
      await api.connections.update(currentConnection.value.id, currentConnection.value)
    } else {
      await api.connections.create(currentConnection.value)
    }
    showDialog.value = false
    await loadConnections()
  } catch (error) {
    console.error('Failed to save connection:', error)
  }
}

const editConnection = (connection) => {
  currentConnection.value = { ...connection }
  isEdit.value = true
  showDialog.value = true
}

const deleteConnection = async (id) => {
  if (confirm('确定要删除此连接吗？')) {
    try {
      await api.connections.delete(id)
      await loadConnections()
    } catch (error) {
      console.error('Failed to delete connection:', error)
    }
  }
}

const testConnection = async (id) => {
  try {
    const result = await api.connections.test(id)
    alert(result ? '连接成功！' : '连接失败！')
  } catch (error) {
    console.error('Failed to test connection:', error)
    alert('连接测试失败！')
  }
}

onMounted(() => {
  loadConnections()
})
</script>
