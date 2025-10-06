<template>
  <div>
    <!-- 顶部统计卡片 -->
    <v-row class="mb-4">
      <v-col cols="12" md="3">
        <v-card color="primary" dark>
          <v-card-text>
            <div class="text-h6">总连接数</div>
            <div class="text-h4">{{ connections.length }}</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="3">
        <v-card color="success" dark>
          <v-card-text>
            <div class="text-h6">MySQL</div>
            <div class="text-h4">{{ mysqlCount }}</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="3">
        <v-card color="info" dark>
          <v-card-text>
            <div class="text-h6">PostgreSQL</div>
            <div class="text-h4">{{ postgresqlCount }}</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="3">
        <v-card color="warning" dark>
          <v-card-text>
            <div class="text-h6">最近使用</div>
            <div class="text-h4">{{ recentlyUsedCount }}</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- 连接列表卡片 -->
    <v-card>
      <v-card-title>
        <span class="text-h5">数据库连接管理</span>
        <v-spacer></v-spacer>
        <v-btn color="primary" @click="openAddDialog" prepend-icon="mdi-plus">
          添加连接
        </v-btn>
      </v-card-title>

      <v-card-text>
        <!-- 搜索和筛选 -->
        <v-row class="mb-4">
          <v-col cols="12" md="6">
            <v-text-field
              v-model="search"
              prepend-inner-icon="mdi-magnify"
              label="搜索连接"
              single-line
              hide-details
              clearable
            ></v-text-field>
          </v-col>
          <v-col cols="12" md="3">
            <v-select
              v-model="filterType"
              :items="['全部', 'mysql', 'postgresql']"
              label="数据库类型"
              hide-details
            ></v-select>
          </v-col>
          <v-col cols="12" md="3">
            <v-btn
              color="primary"
              variant="outlined"
              block
              @click="loadConnections"
              prepend-icon="mdi-refresh"
            >
              刷新
            </v-btn>
          </v-col>
        </v-row>

        <!-- 数据表格 -->
        <v-data-table
          :headers="headers"
          :items="filteredConnections"
          :loading="loading"
          :search="search"
          class="elevation-1"
        >
          <template v-slot:item.type="{ item }">
            <v-chip
              :color="item.type === 'mysql' ? 'blue' : 'green'"
              size="small"
            >
              <v-icon start>{{ item.type === 'mysql' ? 'mdi-database' : 'mdi-elephant' }}</v-icon>
              {{ item.type.toUpperCase() }}
            </v-chip>
          </template>

          <template v-slot:item.status="{ item }">
            <v-chip
              :color="getStatusColor(item)"
              size="small"
            >
              {{ getStatusText(item) }}
            </v-chip>
          </template>

          <template v-slot:item.lastUsed="{ item }">
            <span v-if="item.lastUsed">{{ formatDate(item.lastUsed) }}</span>
            <span v-else class="text-grey">从未使用</span>
          </template>

          <template v-slot:item.actions="{ item }">
            <v-tooltip text="测试连接">
              <template v-slot:activator="{ props }">
                <v-btn
                  v-bind="props"
                  icon
                  size="small"
                  @click="testConnection(item)"
                  :loading="testingId === item.id"
                >
                  <v-icon>mdi-lan-connect</v-icon>
                </v-btn>
              </template>
            </v-tooltip>

            <v-tooltip text="编辑">
              <template v-slot:activator="{ props }">
                <v-btn
                  v-bind="props"
                  icon
                  size="small"
                  @click="editConnection(item)"
                >
                  <v-icon>mdi-pencil</v-icon>
                </v-btn>
              </template>
            </v-tooltip>

            <v-tooltip text="删除">
              <template v-slot:activator="{ props }">
                <v-btn
                  v-bind="props"
                  icon
                  size="small"
                  color="error"
                  @click="confirmDelete(item)"
                >
                  <v-icon>mdi-delete</v-icon>
                </v-btn>
              </template>
            </v-tooltip>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <!-- 添加/编辑对话框 -->
    <v-dialog v-model="showDialog" max-width="700px" persistent>
      <v-card>
        <v-card-title class="bg-primary">
          <span class="text-h5 text-white">
            <v-icon class="mr-2">{{ isEdit ? 'mdi-pencil' : 'mdi-plus' }}</v-icon>
            {{ isEdit ? '编辑' : '添加' }}连接
          </span>
        </v-card-title>

        <v-card-text class="pt-6">
          <v-form ref="formRef" v-model="formValid">
            <!-- 基本信息 -->
            <v-row>
              <v-col cols="12">
                <div class="text-subtitle-1 font-weight-bold mb-2">基本信息</div>
              </v-col>

              <v-col cols="12">
                <v-text-field
                  v-model="currentConnection.name"
                  label="连接名称 *"
                  :rules="[rules.required, rules.maxLength(100)]"
                  prepend-inner-icon="mdi-label"
                  variant="outlined"
                  density="comfortable"
                  hint="为此连接起一个易于识别的名称"
                  persistent-hint
                ></v-text-field>
              </v-col>

              <v-col cols="12" md="6">
                <v-select
                  v-model="currentConnection.type"
                  :items="databaseTypes"
                  label="数据库类型 *"
                  :rules="[rules.required]"
                  prepend-inner-icon="mdi-database"
                  variant="outlined"
                  density="comfortable"
                  @update:model-value="onTypeChange"
                >
                  <template v-slot:item="{ props, item }">
                    <v-list-item v-bind="props">
                      <template v-slot:prepend>
                        <v-icon>{{ item.raw.icon }}</v-icon>
                      </template>
                    </v-list-item>
                  </template>
                </v-select>
              </v-col>
            </v-row>

            <!-- 连接信息 -->
            <v-row>
              <v-col cols="12">
                <div class="text-subtitle-1 font-weight-bold mb-2">连接信息</div>
              </v-col>

              <v-col cols="12" md="8">
                <v-text-field
                  v-model="currentConnection.host"
                  label="主机地址 *"
                  :rules="[rules.required, rules.host]"
                  prepend-inner-icon="mdi-server"
                  variant="outlined"
                  density="comfortable"
                  hint="IP 地址、域名或 localhost"
                  persistent-hint
                ></v-text-field>
              </v-col>

              <v-col cols="12" md="4">
                <v-text-field
                  v-model.number="currentConnection.port"
                  label="端口 *"
                  type="number"
                  :rules="[rules.required, rules.port]"
                  prepend-inner-icon="mdi-ethernet"
                  variant="outlined"
                  density="comfortable"
                ></v-text-field>
              </v-col>

              <v-col cols="12">
                <v-text-field
                  v-model="currentConnection.database"
                  label="数据库名 *"
                  :rules="[rules.required, rules.database]"
                  prepend-inner-icon="mdi-database-outline"
                  variant="outlined"
                  density="comfortable"
                  hint="要连接的数据库名称"
                  persistent-hint
                ></v-text-field>
              </v-col>
            </v-row>

            <!-- 认证信息 -->
            <v-row>
              <v-col cols="12">
                <div class="text-subtitle-1 font-weight-bold mb-2">认证信息</div>
              </v-col>

              <v-col cols="12">
                <v-text-field
                  v-model="currentConnection.username"
                  label="用户名 *"
                  :rules="[rules.required, rules.username]"
                  prepend-inner-icon="mdi-account"
                  variant="outlined"
                  density="comfortable"
                ></v-text-field>
              </v-col>

              <v-col cols="12">
                <v-text-field
                  v-model="currentConnection.password"
                  :label="isEdit ? '密码（留空保持不变）' : '密码 *'"
                  :type="showPassword ? 'text' : 'password'"
                  :rules="isEdit ? [] : [rules.required]"
                  prepend-inner-icon="mdi-lock"
                  :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                  @click:append-inner="showPassword = !showPassword"
                  variant="outlined"
                  density="comfortable"
                  hint="密码将被加密存储"
                  persistent-hint
                ></v-text-field>
              </v-col>
            </v-row>

            <!-- 高级选项 -->
            <v-expansion-panels class="mt-4">
              <v-expansion-panel>
                <v-expansion-panel-title>
                  <v-icon class="mr-2">mdi-cog</v-icon>
                  高级选项
                </v-expansion-panel-title>
                <v-expansion-panel-text>
                  <v-row>
                    <v-col cols="12" v-if="currentConnection.type === 'mysql'">
                      <v-select
                        v-model="characterEncoding"
                        :items="['utf8', 'utf8mb4', 'latin1']"
                        label="字符编码"
                        variant="outlined"
                        density="comfortable"
                      ></v-select>
                    </v-col>

                    <v-col cols="12" v-if="currentConnection.type === 'postgresql'">
                      <v-text-field
                        v-model="schema"
                        label="Schema"
                        variant="outlined"
                        density="comfortable"
                        hint="默认为 public"
                        persistent-hint
                      ></v-text-field>
                    </v-col>

                    <v-col cols="12">
                      <v-switch
                        v-model="useSSL"
                        label="使用 SSL 连接"
                        color="primary"
                        hide-details
                      ></v-switch>
                    </v-col>
                  </v-row>
                </v-expansion-panel-text>
              </v-expansion-panel>
            </v-expansion-panels>
          </v-form>
        </v-card-text>

        <v-divider></v-divider>

        <v-card-actions class="pa-4">
          <v-btn
            variant="outlined"
            @click="closeDialog"
            prepend-icon="mdi-close"
          >
            取消
          </v-btn>
          <v-spacer></v-spacer>
          <v-btn
            color="primary"
            @click="saveConnection"
            :loading="saving"
            :disabled="!formValid"
            prepend-icon="mdi-content-save"
          >
            保存
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 删除确认对话框 -->
    <v-dialog v-model="showDeleteDialog" max-width="400px">
      <v-card>
        <v-card-title class="text-h6">
          <v-icon color="warning" class="mr-2">mdi-alert</v-icon>
          确认删除
        </v-card-title>
        <v-card-text>
          确定要删除连接 <strong>{{ connectionToDelete?.name }}</strong> 吗？
          <br>
          <span class="text-error">此操作无法撤销！</span>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn @click="showDeleteDialog = false">取消</v-btn>
          <v-btn
            color="error"
            @click="deleteConnection"
            :loading="deleting"
          >
            删除
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 消息提示 -->
    <v-snackbar
      v-model="snackbar.show"
      :color="snackbar.color"
      :timeout="3000"
      location="top"
    >
      {{ snackbar.message }}
      <template v-slot:actions>
        <v-btn
          variant="text"
          @click="snackbar.show = false"
        >
          关闭
        </v-btn>
      </template>
    </v-snackbar>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'

// 状态管理
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const testingId = ref(null)
const showDialog = ref(false)
const showDeleteDialog = ref(false)
const showPassword = ref(false)
const isEdit = ref(false)
const formValid = ref(false)
const formRef = ref(null)

// 数据
const connections = ref([])
const connectionToDelete = ref(null)
const search = ref('')
const filterType = ref('全部')

// 高级选项
const characterEncoding = ref('utf8mb4')
const schema = ref('public')
const useSSL = ref(false)

// 当前连接
const currentConnection = ref({
  name: '',
  type: 'mysql',
  host: 'localhost',
  port: 3306,
  database: '',
  username: '',
  password: '',
  options: {}
})

// 消息提示
const snackbar = ref({
  show: false,
  message: '',
  color: 'success'
})

// 数据库类型选项
const databaseTypes = [
  { title: 'MySQL', value: 'mysql', icon: 'mdi-database' },
  { title: 'PostgreSQL', value: 'postgresql', icon: 'mdi-elephant' }
]

// 表格列定义
const headers = [
  { title: '名称', key: 'name', sortable: true },
  { title: '类型', key: 'type', sortable: true },
  { title: '主机', key: 'host', sortable: true },
  { title: '端口', key: 'port', sortable: true },
  { title: '数据库', key: 'database', sortable: true },
  { title: '状态', key: 'status', sortable: false },
  { title: '最后使用', key: 'lastUsed', sortable: true },
  { title: '操作', key: 'actions', sortable: false, align: 'center' }
]

// 表单验证规则
const rules = {
  required: value => !!value || '此字段为必填项',
  maxLength: max => value => !value || value.length <= max || `最多 ${max} 个字符`,
  port: value => {
    if (!value) return '端口号为必填项'
    const port = parseInt(value)
    return (port >= 1 && port <= 65535) || '端口号必须在 1-65535 之间'
  },
  host: value => {
    if (!value) return '主机地址为必填项'
    // 简单的主机地址验证
    const hostPattern = /^[a-zA-Z0-9.-]+$/
    return hostPattern.test(value) || '主机地址格式无效'
  },
  database: value => {
    if (!value) return '数据库名为必填项'
    const dbPattern = /^[a-zA-Z0-9_-]+$/
    return dbPattern.test(value) || '数据库名只能包含字母、数字、下划线和连字符'
  },
  username: value => {
    if (!value) return '用户名为必填项'
    return !value.includes(' ') || '用户名不能包含空格'
  }
}

// 计算属性
const filteredConnections = computed(() => {
  let result = connections.value

  // 按类型筛选
  if (filterType.value !== '全部') {
    result = result.filter(conn => conn.type === filterType.value)
  }

  return result
})

const mysqlCount = computed(() => {
  return connections.value.filter(conn => conn.type === 'mysql').length
})

const postgresqlCount = computed(() => {
  return connections.value.filter(conn => conn.type === 'postgresql').length
})

const recentlyUsedCount = computed(() => {
  const oneDayAgo = new Date()
  oneDayAgo.setDate(oneDayAgo.getDate() - 1)

  return connections.value.filter(conn => {
    if (!conn.lastUsed) return false
    return new Date(conn.lastUsed) > oneDayAgo
  }).length
})

// 方法
const showMessage = (message, color = 'success') => {
  snackbar.value = {
    show: true,
    message,
    color
  }
}

const loadConnections = async () => {
  loading.value = true
  try {
    const data = await api.connections.getAll()
    connections.value = data || []
    console.log('加载连接列表成功:', connections.value.length)
  } catch (error) {
    console.error('加载连接列表失败:', error)
    showMessage('加载连接列表失败: ' + (error.response?.data?.message || error.message), 'error')
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  currentConnection.value = {
    name: '',
    type: 'mysql',
    host: 'localhost',
    port: 3306,
    database: '',
    username: '',
    password: '',
    options: {}
  }
  characterEncoding.value = 'utf8mb4'
  schema.value = 'public'
  useSSL.value = false
  isEdit.value = false
  showPassword.value = false
  showDialog.value = true

  // 重置表单验证
  if (formRef.value) {
    formRef.value.resetValidation()
  }
}

const closeDialog = () => {
  showDialog.value = false
  if (formRef.value) {
    formRef.value.reset()
  }
}

const onTypeChange = (type) => {
  // 根据数据库类型设置默认端口
  if (type === 'mysql') {
    currentConnection.value.port = 3306
  } else if (type === 'postgresql') {
    currentConnection.value.port = 5432
  }
}

const saveConnection = async () => {
  // 验证表单
  const { valid } = await formRef.value.validate()
  if (!valid) {
    showMessage('请检查表单填写是否正确', 'warning')
    return
  }

  saving.value = true
  try {
    // 构建选项对象
    const options = {}
    if (currentConnection.value.type === 'mysql') {
      options.characterEncoding = characterEncoding.value
      options.useSSL = useSSL.value
    } else if (currentConnection.value.type === 'postgresql') {
      if (schema.value) {
        options.schema = schema.value
      }
      options.useSSL = useSSL.value
    }

    const connectionData = {
      ...currentConnection.value,
      options
    }

    if (isEdit.value) {
      await api.connections.update(currentConnection.value.id, connectionData)
      showMessage('连接更新成功', 'success')
    } else {
      await api.connections.create(connectionData)
      showMessage('连接创建成功', 'success')
    }

    closeDialog()
    await loadConnections()
  } catch (error) {
    console.error('保存连接失败:', error)
    const errorMsg = error.response?.data?.message || error.message
    showMessage('保存连接失败: ' + errorMsg, 'error')
  } finally {
    saving.value = false
  }
}

const editConnection = (connection) => {
  currentConnection.value = { ...connection }

  // 加载高级选项
  if (connection.options) {
    characterEncoding.value = connection.options.characterEncoding || 'utf8mb4'
    schema.value = connection.options.schema || 'public'
    useSSL.value = connection.options.useSSL || false
  }

  isEdit.value = true
  showPassword.value = false
  showDialog.value = true

  // 重置表单验证
  if (formRef.value) {
    formRef.value.resetValidation()
  }
}

const confirmDelete = (connection) => {
  connectionToDelete.value = connection
  showDeleteDialog.value = true
}

const deleteConnection = async () => {
  if (!connectionToDelete.value) return

  deleting.value = true
  try {
    await api.connections.delete(connectionToDelete.value.id)
    showMessage('连接删除成功', 'success')
    showDeleteDialog.value = false
    connectionToDelete.value = null
    await loadConnections()
  } catch (error) {
    console.error('删除连接失败:', error)
    const errorMsg = error.response?.data?.message || error.message
    showMessage('删除连接失败: ' + errorMsg, 'error')
  } finally {
    deleting.value = false
  }
}

const testConnection = async (connection) => {
  testingId.value = connection.id
  try {
    const result = await api.connections.test(connection.id)
    if (result) {
      showMessage(`连接 "${connection.name}" 测试成功！`, 'success')
    } else {
      showMessage(`连接 "${connection.name}" 测试失败！`, 'error')
    }
  } catch (error) {
    console.error('测试连接失败:', error)
    const errorMsg = error.response?.data?.details || error.response?.data?.message || error.message
    showMessage(`连接测试失败: ${errorMsg}`, 'error')
  } finally {
    testingId.value = null
  }
}

const getStatusColor = (connection) => {
  // 这里可以根据实际情况判断连接状态
  // 暂时根据最后使用时间判断
  if (!connection.lastUsed) return 'grey'

  const lastUsed = new Date(connection.lastUsed)
  const now = new Date()
  const diffHours = (now - lastUsed) / (1000 * 60 * 60)

  if (diffHours < 1) return 'success'
  if (diffHours < 24) return 'info'
  return 'grey'
}

const getStatusText = (connection) => {
  if (!connection.lastUsed) return '未使用'

  const lastUsed = new Date(connection.lastUsed)
  const now = new Date()
  const diffHours = (now - lastUsed) / (1000 * 60 * 60)

  if (diffHours < 1) return '活跃'
  if (diffHours < 24) return '最近使用'
  return '闲置'
}

const formatDate = (dateString) => {
  if (!dateString) return ''

  const date = new Date(dateString)
  const now = new Date()
  const diff = now - date

  // 小于1分钟
  if (diff < 60000) {
    return '刚刚'
  }

  // 小于1小时
  if (diff < 3600000) {
    const minutes = Math.floor(diff / 60000)
    return `${minutes} 分钟前`
  }

  // 小于24小时
  if (diff < 86400000) {
    const hours = Math.floor(diff / 3600000)
    return `${hours} 小时前`
  }

  // 小于7天
  if (diff < 604800000) {
    const days = Math.floor(diff / 86400000)
    return `${days} 天前`
  }

  // 格式化日期
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadConnections()
})
</script>

<style scoped>
.v-card {
  transition: all 0.3s ease;
}

.v-data-table {
  font-size: 14px;
}

.text-grey {
  color: #9e9e9e;
}
</style>
