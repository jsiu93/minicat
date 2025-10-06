<template>
  <div>
    <!-- 选择数据库连接 -->
    <v-card class="mb-4">
      <v-card-title class="bg-primary">
        <v-icon class="mr-2">mdi-table-compare</v-icon>
        <span class="text-h5 text-white">表结构比对</span>
      </v-card-title>

      <v-card-text class="pt-6">
        <v-row>
          <v-col cols="12" md="5">
            <v-select
              v-model="sourceConnection"
              :items="connections"
              item-title="name"
              item-value="id"
              label="源数据库 *"
              prepend-inner-icon="mdi-database-arrow-right"
              variant="outlined"
              density="comfortable"
              hint="选择要作为比对源的数据库"
              persistent-hint
              @update:model-value="onSourceConnectionChange"
            >
              <template v-slot:item="{ props, item }">
                <v-list-item v-bind="props">
                  <template v-slot:prepend>
                    <v-icon :color="item.raw.type === 'mysql' ? 'blue' : 'green'">
                      {{ item.raw.type === 'mysql' ? 'mdi-database' : 'mdi-elephant' }}
                    </v-icon>
                  </template>
                  <template v-slot:subtitle>
                    {{ item.raw.host }}:{{ item.raw.port }}/{{ item.raw.database }}
                  </template>
                </v-list-item>
              </template>
            </v-select>
          </v-col>

          <v-col cols="12" md="2" class="d-flex align-center justify-center">
            <v-icon size="48" color="primary">mdi-arrow-right-bold</v-icon>
          </v-col>

          <v-col cols="12" md="5">
            <v-select
              v-model="targetConnection"
              :items="connections"
              item-title="name"
              item-value="id"
              label="目标数据库 *"
              prepend-inner-icon="mdi-database-arrow-left"
              variant="outlined"
              density="comfortable"
              hint="选择要作为比对目标的数据库"
              persistent-hint
              @update:model-value="onTargetConnectionChange"
            >
              <template v-slot:item="{ props, item }">
                <v-list-item v-bind="props">
                  <template v-slot:prepend>
                    <v-icon :color="item.raw.type === 'mysql' ? 'blue' : 'green'">
                      {{ item.raw.type === 'mysql' ? 'mdi-database' : 'mdi-elephant' }}
                    </v-icon>
                  </template>
                  <template v-slot:subtitle>
                    {{ item.raw.host }}:{{ item.raw.port }}/{{ item.raw.database }}
                  </template>
                </v-list-item>
              </template>
            </v-select>
          </v-col>
        </v-row>

        <!-- 表选择 -->
        <v-row v-if="sourceConnection && targetConnection" class="mt-4">
          <v-col cols="12">
            <v-card variant="outlined">
              <v-card-title class="text-subtitle-1">
                <v-icon class="mr-2">mdi-table-multiple</v-icon>
                选择要比对的表
              </v-card-title>
              <v-card-text>
                <v-row>
                  <v-col cols="12" md="6">
                    <div class="d-flex align-center mb-2">
                      <v-chip color="primary" size="small" class="mr-2">
                        源库表 ({{ sourceTables.length }})
                      </v-chip>
                      <v-spacer></v-spacer>
                      <v-btn
                        size="small"
                        variant="text"
                        @click="selectAllSourceTables"
                      >
                        全选
                      </v-btn>
                      <v-btn
                        size="small"
                        variant="text"
                        @click="clearSourceTables"
                      >
                        清空
                      </v-btn>
                    </div>
                    <v-sheet
                      class="pa-2"
                      border
                      rounded
                      max-height="300"
                      style="overflow-y: auto"
                    >
                      <v-progress-circular
                        v-if="loadingSourceTables"
                        indeterminate
                        color="primary"
                        class="d-block mx-auto"
                      ></v-progress-circular>
                      <v-checkbox
                        v-else
                        v-for="table in sourceTables"
                        :key="table"
                        v-model="selectedTables"
                        :value="table"
                        :label="table"
                        density="compact"
                        hide-details
                      ></v-checkbox>
                      <v-alert
                        v-if="!loadingSourceTables && sourceTables.length === 0"
                        type="info"
                        variant="tonal"
                        density="compact"
                      >
                        未找到表
                      </v-alert>
                    </v-sheet>
                  </v-col>

                  <v-col cols="12" md="6">
                    <div class="d-flex align-center mb-2">
                      <v-chip color="success" size="small">
                        目标库表 ({{ targetTables.length }})
                      </v-chip>
                    </div>
                    <v-sheet
                      class="pa-2"
                      border
                      rounded
                      max-height="300"
                      style="overflow-y: auto"
                    >
                      <v-progress-circular
                        v-if="loadingTargetTables"
                        indeterminate
                        color="success"
                        class="d-block mx-auto"
                      ></v-progress-circular>
                      <v-list v-else density="compact">
                        <v-list-item
                          v-for="table in targetTables"
                          :key="table"
                          :title="table"
                        >
                          <template v-slot:prepend>
                            <v-icon size="small">mdi-table</v-icon>
                          </template>
                        </v-list-item>
                      </v-list>
                      <v-alert
                        v-if="!loadingTargetTables && targetTables.length === 0"
                        type="info"
                        variant="tonal"
                        density="compact"
                      >
                        未找到表
                      </v-alert>
                    </v-sheet>
                  </v-col>
                </v-row>

                <v-alert
                  v-if="selectedTables.length > 0"
                  type="success"
                  variant="tonal"
                  density="compact"
                  class="mt-4"
                >
                  已选择 {{ selectedTables.length }} 个表进行比对
                </v-alert>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>

        <!-- 高级选项 -->
        <v-expansion-panels class="mt-4">
          <v-expansion-panel>
            <v-expansion-panel-title>
              <v-icon class="mr-2">mdi-cog</v-icon>
              比对选项
            </v-expansion-panel-title>
            <v-expansion-panel-text>
              <v-row>
                <v-col cols="12" md="4">
                  <v-switch
                    v-model="compareOptions.compareIndexes"
                    label="比对索引"
                    color="primary"
                    hide-details
                  ></v-switch>
                </v-col>
                <v-col cols="12" md="4">
                  <v-switch
                    v-model="compareOptions.compareForeignKeys"
                    label="比对外键"
                    color="primary"
                    hide-details
                  ></v-switch>
                </v-col>
                <v-col cols="12" md="4">
                  <v-switch
                    v-model="compareOptions.compareComments"
                    label="比对注释"
                    color="primary"
                    hide-details
                  ></v-switch>
                </v-col>
              </v-row>
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>

        <!-- 操作按钮 -->
        <v-row class="mt-4">
          <v-col cols="12">
            <v-btn
              color="primary"
              size="large"
              block
              :disabled="!sourceConnection || !targetConnection || selectedTables.length === 0 || comparing"
              :loading="comparing"
              @click="startCompare"
              prepend-icon="mdi-compare"
            >
              开始比对 {{ selectedTables.length > 0 ? `(${selectedTables.length} 个表)` : '' }}
            </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- 比对结果 -->
    <v-card v-if="diffResult" class="mt-4">
      <v-card-title class="bg-success">
        <v-icon class="mr-2">mdi-check-circle</v-icon>
        <span class="text-h5 text-white">比对结果</span>
        <v-spacer></v-spacer>
        <v-btn
          v-if="diffResult.statistics?.totalDiffCount > 0"
          color="white"
          variant="outlined"
          prepend-icon="mdi-sync"
          @click="generateSyncSql"
          :loading="generatingSql"
        >
          生成同步 SQL
        </v-btn>
      </v-card-title>

      <v-card-text class="pt-6">
        <!-- 统计信息 -->
        <v-row class="mb-4">
          <v-col cols="12" md="3">
            <v-card color="primary" dark>
              <v-card-text>
                <div class="text-h6">新增表</div>
                <div class="text-h4">{{ diffResult.statistics?.addedTableCount || 0 }}</div>
              </v-card-text>
            </v-card>
          </v-col>
          <v-col cols="12" md="3">
            <v-card color="warning" dark>
              <v-card-text>
                <div class="text-h6">修改表</div>
                <div class="text-h4">{{ diffResult.statistics?.modifiedTableCount || 0 }}</div>
              </v-card-text>
            </v-card>
          </v-col>
          <v-col cols="12" md="3">
            <v-card color="error" dark>
              <v-card-text>
                <div class="text-h6">删除表</div>
                <div class="text-h4">{{ diffResult.statistics?.deletedTableCount || 0 }}</div>
              </v-card-text>
            </v-card>
          </v-col>
          <v-col cols="12" md="3">
            <v-card color="success" dark>
              <v-card-text>
                <div class="text-h6">相同表</div>
                <div class="text-h4">{{ diffResult.statistics?.identicalTableCount || 0 }}</div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>

        <!-- 差异详情表格 -->
        <v-data-table
          :headers="tableHeaders"
          :items="filteredTableDiffs"
          :items-per-page="10"
          class="elevation-1"
        >
          <template v-slot:top>
            <v-tabs v-model="activeTab" class="mb-4">
              <v-tab value="all">全部 ({{ diffResult.tableDiffs?.length || 0 }})</v-tab>
              <v-tab value="ADD">新增 ({{ diffResult.statistics?.addedTableCount || 0 }})</v-tab>
              <v-tab value="MODIFY">修改 ({{ diffResult.statistics?.modifiedTableCount || 0 }})</v-tab>
              <v-tab value="DELETE">删除 ({{ diffResult.statistics?.deletedTableCount || 0 }})</v-tab>
            </v-tabs>
          </template>

          <template v-slot:item.diffType="{ item }">
            <v-chip
              :color="getDiffTypeColor(item.diffType)"
              size="small"
            >
              {{ getDiffTypeText(item.diffType) }}
            </v-chip>
          </template>

          <template v-slot:item.differences="{ item }">
            <div v-if="item.diffType === 'MODIFY'">
              <v-chip v-if="item.columnDiffs?.length" size="small" class="mr-1">
                列: {{ item.columnDiffs.length }}
              </v-chip>
              <v-chip v-if="item.indexDiffs?.length" size="small" class="mr-1">
                索引: {{ item.indexDiffs.length }}
              </v-chip>
              <v-chip v-if="item.foreignKeyDiffs?.length" size="small">
                外键: {{ item.foreignKeyDiffs.length }}
              </v-chip>
            </div>
            <span v-else>-</span>
          </template>

          <template v-slot:item.actions="{ item }">
            <v-btn
              v-if="item.diffType === 'MODIFY'"
              icon
              size="small"
              @click="showTableDetail(item)"
            >
              <v-icon>mdi-eye</v-icon>
            </v-btn>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <!-- 详细差异对话框 -->
    <v-dialog
      v-model="detailDialog.show"
      max-width="1200"
      scrollable
    >
      <v-card>
        <v-card-title class="bg-primary">
          <v-icon class="mr-2">mdi-table-edit</v-icon>
          <span class="text-h5 text-white">表结构差异详情: {{ detailDialog.tableName }}</span>
          <v-spacer></v-spacer>
          <v-btn
            icon
            variant="text"
            @click="detailDialog.show = false"
          >
            <v-icon color="white">mdi-close</v-icon>
          </v-btn>
        </v-card-title>

        <v-card-text class="pt-4">
          <!-- 差异统计 -->
          <v-row class="mb-4">
            <v-col cols="12" md="4">
              <v-card color="warning" variant="tonal">
                <v-card-text>
                  <div class="text-subtitle-2">列差异</div>
                  <div class="text-h5">{{ detailDialog.tableDiff?.columnDiffs?.length || 0 }}</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="4">
              <v-card color="info" variant="tonal">
                <v-card-text>
                  <div class="text-subtitle-2">索引差异</div>
                  <div class="text-h5">{{ detailDialog.tableDiff?.indexDiffs?.length || 0 }}</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="4">
              <v-card color="success" variant="tonal">
                <v-card-text>
                  <div class="text-subtitle-2">外键差异</div>
                  <div class="text-h5">{{ detailDialog.tableDiff?.foreignKeyDiffs?.length || 0 }}</div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>

          <!-- 列差异详情 -->
          <v-expansion-panels v-if="detailDialog.tableDiff?.columnDiffs?.length > 0" class="mb-4">
            <v-expansion-panel>
              <v-expansion-panel-title>
                <v-icon class="mr-2">mdi-table-column</v-icon>
                <span class="font-weight-bold">列差异 ({{ detailDialog.tableDiff.columnDiffs.length }})</span>
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <v-list>
                  <v-list-item
                    v-for="(diff, index) in detailDialog.tableDiff.columnDiffs"
                    :key="index"
                    class="mb-2"
                  >
                    <template v-slot:prepend>
                      <v-avatar :color="getColumnDiffColor(diff.diffType)">
                        <v-icon color="white">{{ getColumnDiffIcon(diff.diffType) }}</v-icon>
                      </v-avatar>
                    </template>

                    <v-list-item-title class="font-weight-bold">
                      {{ diff.columnName }}
                      <v-chip :color="getColumnDiffColor(diff.diffType)" size="small" class="ml-2">
                        {{ getColumnDiffText(diff.diffType) }}
                      </v-chip>
                    </v-list-item-title>

                    <v-list-item-subtitle v-if="diff.description">
                      {{ diff.description }}
                    </v-list-item-subtitle>

                    <!-- 源列信息 -->
                    <template v-if="diff.sourceColumn">
                      <v-card variant="outlined" class="mt-2 mb-2">
                        <v-card-subtitle class="text-primary font-weight-bold">
                          <v-icon size="small" class="mr-1">mdi-database-arrow-right</v-icon>
                          源库
                        </v-card-subtitle>
                        <v-card-text>
                          <v-row dense>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">数据类型</div>
                              <div class="font-weight-medium">{{ diff.sourceColumn.dataType }}</div>
                            </v-col>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">可空</div>
                              <div class="font-weight-medium">{{ diff.sourceColumn.nullable ? '是' : '否' }}</div>
                            </v-col>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">默认值</div>
                              <div class="font-weight-medium">{{ diff.sourceColumn.defaultValue || '-' }}</div>
                            </v-col>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">自增</div>
                              <div class="font-weight-medium">{{ diff.sourceColumn.autoIncrement ? '是' : '否' }}</div>
                            </v-col>
                          </v-row>
                        </v-card-text>
                      </v-card>
                    </template>

                    <!-- 目标列信息 -->
                    <template v-if="diff.targetColumn">
                      <v-card variant="outlined" class="mb-2">
                        <v-card-subtitle class="text-success font-weight-bold">
                          <v-icon size="small" class="mr-1">mdi-database-arrow-left</v-icon>
                          目标库
                        </v-card-subtitle>
                        <v-card-text>
                          <v-row dense>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">数据类型</div>
                              <div class="font-weight-medium">{{ diff.targetColumn.dataType }}</div>
                            </v-col>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">可空</div>
                              <div class="font-weight-medium">{{ diff.targetColumn.nullable ? '是' : '否' }}</div>
                            </v-col>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">默认值</div>
                              <div class="font-weight-medium">{{ diff.targetColumn.defaultValue || '-' }}</div>
                            </v-col>
                            <v-col cols="6" md="3">
                              <div class="text-caption text-grey">自增</div>
                              <div class="font-weight-medium">{{ diff.targetColumn.autoIncrement ? '是' : '否' }}</div>
                            </v-col>
                          </v-row>
                        </v-card-text>
                      </v-card>
                    </template>

                    <v-divider v-if="index < detailDialog.tableDiff.columnDiffs.length - 1" class="mt-4"></v-divider>
                  </v-list-item>
                </v-list>
              </v-expansion-panel-text>
            </v-expansion-panel>
          </v-expansion-panels>

          <!-- 索引差异详情 -->
          <v-expansion-panels v-if="detailDialog.tableDiff?.indexDiffs?.length > 0" class="mb-4">
            <v-expansion-panel>
              <v-expansion-panel-title>
                <v-icon class="mr-2">mdi-key</v-icon>
                <span class="font-weight-bold">索引差异 ({{ detailDialog.tableDiff.indexDiffs.length }})</span>
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <v-alert type="info" variant="tonal">
                  索引比对功能待完善
                </v-alert>
              </v-expansion-panel-text>
            </v-expansion-panel>
          </v-expansion-panels>

          <!-- 外键差异详情 -->
          <v-expansion-panels v-if="detailDialog.tableDiff?.foreignKeyDiffs?.length > 0" class="mb-4">
            <v-expansion-panel>
              <v-expansion-panel-title>
                <v-icon class="mr-2">mdi-link-variant</v-icon>
                <span class="font-weight-bold">外键差异 ({{ detailDialog.tableDiff.foreignKeyDiffs.length }})</span>
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <v-alert type="info" variant="tonal">
                  外键比对功能待完善
                </v-alert>
              </v-expansion-panel-text>
            </v-expansion-panel>
          </v-expansion-panels>

          <!-- 无差异提示 -->
          <v-alert
            v-if="!detailDialog.tableDiff?.columnDiffs?.length &&
                  !detailDialog.tableDiff?.indexDiffs?.length &&
                  !detailDialog.tableDiff?.foreignKeyDiffs?.length"
            type="success"
            variant="tonal"
          >
            该表结构完全相同，无差异
          </v-alert>
        </v-card-text>

        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            color="primary"
            variant="text"
            @click="detailDialog.show = false"
          >
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 同步 SQL 对话框 -->
    <v-dialog
      v-model="syncDialog.show"
      max-width="1000"
      scrollable
    >
      <v-card>
        <v-card-title class="bg-success">
          <v-icon class="mr-2">mdi-sync</v-icon>
          <span class="text-h5 text-white">结构同步</span>
          <v-spacer></v-spacer>
          <v-btn
            icon
            variant="text"
            @click="syncDialog.show = false"
          >
            <v-icon color="white">mdi-close</v-icon>
          </v-btn>
        </v-card-title>

        <v-card-text class="pt-4">
          <!-- 同步信息 -->
          <v-alert type="info" variant="tonal" class="mb-4">
            <div class="font-weight-bold mb-2">同步信息</div>
            <div>源数据库: {{ diffResult?.sourceConnectionName }}</div>
            <div>目标数据库: {{ diffResult?.targetConnectionName }}</div>
            <div>SQL 语句数量: {{ syncDialog.sqlStatements.length }}</div>
          </v-alert>

          <!-- SQL 预览 -->
          <v-card variant="outlined" class="mb-4">
            <v-card-title class="text-subtitle-1">
              <v-icon class="mr-2">mdi-code-tags</v-icon>
              SQL 语句预览
            </v-card-title>
            <v-card-text>
              <v-sheet
                class="pa-4"
                color="grey-lighten-4"
                rounded
                max-height="400"
                style="overflow-y: auto"
              >
                <pre class="text-body-2" style="white-space: pre-wrap; word-wrap: break-word;">{{ syncDialog.sqlStatements.join('\n\n') }}</pre>
              </v-sheet>
            </v-card-text>
          </v-card>

          <!-- 警告提示 -->
          <v-alert type="warning" variant="tonal" class="mb-4">
            <div class="font-weight-bold mb-2">⚠️ 重要提示</div>
            <ul class="ml-4">
              <li>执行同步将修改目标数据库的表结构</li>
              <li>建议先在测试环境验证</li>
              <li>执行前请确认已备份重要数据</li>
              <li>同步操作将在事务中执行，失败会自动回滚</li>
            </ul>
          </v-alert>
        </v-card-text>

        <v-card-actions>
          <v-btn
            color="primary"
            variant="outlined"
            prepend-icon="mdi-download"
            @click="downloadSql"
          >
            下载 SQL
          </v-btn>
          <v-spacer></v-spacer>
          <v-btn
            variant="text"
            @click="syncDialog.show = false"
          >
            取消
          </v-btn>
          <v-btn
            color="success"
            :loading="syncing"
            :disabled="syncing"
            prepend-icon="mdi-sync"
            @click="executeSync"
          >
            执行同步
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
        <v-btn variant="text" @click="snackbar.show = false">
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
const connections = ref([])
const sourceConnection = ref(null)
const targetConnection = ref(null)
const diffResult = ref(null)
const comparing = ref(false)
const activeTab = ref('all')

// 表列表
const sourceTables = ref([])
const targetTables = ref([])
const selectedTables = ref([])
const loadingSourceTables = ref(false)
const loadingTargetTables = ref(false)

// 比对选项
const compareOptions = ref({
  compareIndexes: true,
  compareForeignKeys: true,
  compareComments: false
})

// 消息提示
const snackbar = ref({
  show: false,
  message: '',
  color: 'success'
})

// 详细差异对话框
const detailDialog = ref({
  show: false,
  tableName: '',
  tableDiff: null
})

// 同步对话框
const syncDialog = ref({
  show: false,
  sqlStatements: []
})

// 同步状态
const generatingSql = ref(false)
const syncing = ref(false)

// 表格列定义
const tableHeaders = [
  { title: '表名', key: 'tableName', sortable: true },
  { title: '差异类型', key: 'diffType', sortable: true },
  { title: '差异详情', key: 'differences', sortable: false },
  { title: '操作', key: 'actions', sortable: false, align: 'center' }
]

// 计算属性
const filteredTableDiffs = computed(() => {
  if (!diffResult.value || !diffResult.value.tableDiffs) {
    return []
  }

  if (activeTab.value === 'all') {
    return diffResult.value.tableDiffs
  }

  return diffResult.value.tableDiffs.filter(t => t.diffType === activeTab.value)
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
  try {
    const data = await api.connections.getAll()
    connections.value = data || []
    console.log('加载连接列表成功:', connections.value.length)
  } catch (error) {
    console.error('加载连接列表失败:', error)
    showMessage('加载连接列表失败: ' + (error.response?.data?.message || error.message), 'error')
  }
}

const loadSourceTables = async () => {
  if (!sourceConnection.value) {
    sourceTables.value = []
    return
  }

  loadingSourceTables.value = true
  try {
    const tables = await api.schema.getTables(sourceConnection.value)
    sourceTables.value = tables || []
    console.log('加载源库表列表成功:', sourceTables.value.length)
  } catch (error) {
    console.error('加载源库表列表失败:', error)
    showMessage('加载源库表列表失败: ' + (error.response?.data?.message || error.message), 'error')
    sourceTables.value = []
  } finally {
    loadingSourceTables.value = false
  }
}

const loadTargetTables = async () => {
  if (!targetConnection.value) {
    targetTables.value = []
    return
  }

  loadingTargetTables.value = true
  try {
    const tables = await api.schema.getTables(targetConnection.value)
    targetTables.value = tables || []
    console.log('加载目标库表列表成功:', targetTables.value.length)
  } catch (error) {
    console.error('加载目标库表列表失败:', error)
    showMessage('加载目标库表列表失败: ' + (error.response?.data?.message || error.message), 'error')
    targetTables.value = []
  } finally {
    loadingTargetTables.value = false
  }
}

const onSourceConnectionChange = () => {
  selectedTables.value = []
  loadSourceTables()
  loadTargetTables()
}

const onTargetConnectionChange = () => {
  selectedTables.value = []
  loadSourceTables()
  loadTargetTables()
}

const selectAllSourceTables = () => {
  selectedTables.value = [...sourceTables.value]
}

const clearSourceTables = () => {
  selectedTables.value = []
}

const startCompare = async () => {
  // 验证是否选择了表
  if (selectedTables.value.length === 0) {
    showMessage('请至少选择一个表进行比对', 'warning')
    return
  }

  comparing.value = true
  diffResult.value = null

  try {
    const request = {
      sourceConnectionId: sourceConnection.value,
      targetConnectionId: targetConnection.value,
      tables: selectedTables.value,
      compareIndexes: compareOptions.value.compareIndexes,
      compareForeignKeys: compareOptions.value.compareForeignKeys,
      compareComments: compareOptions.value.compareComments
    }

    console.log('开始结构比对:', request)
    const result = await api.schema.compare(request)

    diffResult.value = result
    console.log('比对完成:', result)

    if (result.status === 'COMPLETED') {
      showMessage(`比对完成！共发现 ${result.statistics?.totalDiffCount || 0} 处差异`, 'success')
    } else if (result.status === 'FAILED') {
      showMessage('比对失败: ' + result.errorMessage, 'error')
    }
  } catch (error) {
    console.error('结构比对失败:', error)
    const errorMsg = error.response?.data?.message || error.message
    showMessage('结构比对失败: ' + errorMsg, 'error')
  } finally {
    comparing.value = false
  }
}

const getDiffTypeColor = (diffType) => {
  switch (diffType) {
    case 'ADD': return 'primary'
    case 'MODIFY': return 'warning'
    case 'DELETE': return 'error'
    case 'IDENTICAL': return 'success'
    default: return 'grey'
  }
}

const getDiffTypeText = (diffType) => {
  switch (diffType) {
    case 'ADD': return '新增'
    case 'MODIFY': return '修改'
    case 'DELETE': return '删除'
    case 'IDENTICAL': return '相同'
    default: return '未知'
  }
}

const showTableDetail = (tableDiff) => {
  console.log('查看表详情:', tableDiff)
  detailDialog.value = {
    show: true,
    tableName: tableDiff.tableName,
    tableDiff: tableDiff
  }
}

const getColumnDiffColor = (diffType) => {
  switch (diffType) {
    case 'ADD': return 'primary'
    case 'MODIFY': return 'warning'
    case 'DELETE': return 'error'
    default: return 'grey'
  }
}

const getColumnDiffText = (diffType) => {
  switch (diffType) {
    case 'ADD': return '新增'
    case 'MODIFY': return '修改'
    case 'DELETE': return '删除'
    default: return '未知'
  }
}

const getColumnDiffIcon = (diffType) => {
  switch (diffType) {
    case 'ADD': return 'mdi-plus'
    case 'MODIFY': return 'mdi-pencil'
    case 'DELETE': return 'mdi-minus'
    default: return 'mdi-help'
  }
}

const generateSyncSql = async () => {
  generatingSql.value = true

  try {
    console.log('生成同步 SQL:', diffResult.value)
    const response = await api.schema.generateSyncSql(diffResult.value)

    syncDialog.value = {
      show: true,
      sqlStatements: response.sqlStatements || []
    }

    showMessage(`成功生成 ${response.sqlCount} 条同步 SQL`, 'success')
  } catch (error) {
    console.error('生成同步 SQL 失败:', error)
    const errorMsg = error.response?.data?.message || error.message
    showMessage('生成同步 SQL 失败: ' + errorMsg, 'error')
  } finally {
    generatingSql.value = false
  }
}

const executeSync = async () => {
  syncing.value = true

  try {
    const request = {
      targetConnectionId: diffResult.value.targetConnectionId,
      sqlStatements: syncDialog.value.sqlStatements,
      previewOnly: false
    }

    console.log('执行同步:', request)
    const response = await api.schema.executeSync(request)

    syncDialog.value.show = false
    showMessage(`同步任务已创建，任务ID: ${response.taskId}`, 'success')

    // 可以跳转到任务页面查看进度
    // router.push({ name: 'tasks', query: { taskId: response.taskId } })
  } catch (error) {
    console.error('执行同步失败:', error)
    const errorMsg = error.response?.data?.message || error.message
    showMessage('执行同步失败: ' + errorMsg, 'error')
  } finally {
    syncing.value = false
  }
}

const downloadSql = () => {
  const sqlContent = syncDialog.value.sqlStatements.join('\n\n')
  const blob = new Blob([sqlContent], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `schema_sync_${new Date().getTime()}.sql`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)

  showMessage('SQL 文件已下载', 'success')
}

onMounted(() => {
  loadConnections()
})
</script>

<style scoped>
.v-card {
  transition: all 0.3s ease;
}
</style>
