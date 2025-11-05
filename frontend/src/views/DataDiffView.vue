<template>
  <div>
    <!-- 选择数据库连接 -->
    <v-card class="mb-4">
      <v-card-title class="bg-primary">
        <v-icon class="mr-2">mdi-database-sync</v-icon>
        <span class="text-h5 text-white">数据比对</span>
      </v-card-title>

      <v-card-text class="pt-6">
        <v-row>
          <!-- 源数据库 -->
          <v-col cols="12" md="5">
            <v-select
              v-model="sourceConnectionId"
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
                    <v-icon :color="getDbTypeColor(item.raw.type)">
                      {{ getDbTypeIcon(item.raw.type) }}
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

          <!-- 目标数据库 -->
          <v-col cols="12" md="5">
            <v-select
              v-model="targetConnectionId"
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
                    <v-icon :color="getDbTypeColor(item.raw.type)">
                      {{ getDbTypeIcon(item.raw.type) }}
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
        <v-row v-if="sourceConnectionId && targetConnectionId" class="mt-4">
          <v-col cols="12">
            <v-card variant="outlined">
              <v-card-title class="text-subtitle-1">
                <v-icon class="mr-2">mdi-table-multiple</v-icon>
                选择要比对的表
              </v-card-title>
              <v-card-text>
                <v-row>
                  <!-- 源库表列表 -->
                  <v-col cols="12" md="6">
                    <div class="d-flex align-center mb-2">
                      <v-chip color="primary" size="small" class="mr-2">
                        源库表 ({{ sourceTables.length }})
                      </v-chip>
                      <v-chip
                        v-if="targetTableSet.size"
                        color="success"
                        size="small"
                        variant="tonal"
                        class="mr-2"
                      >
                        可同步 {{ validSourceTables.length }}
                      </v-chip>
                      <v-chip
                        v-if="sourceOnlyCount > 0"
                        color="warning"
                        size="small"
                        variant="tonal"
                        class="mr-2"
                      >
                        目标缺失 {{ sourceOnlyCount }}
                      </v-chip>
                      <v-spacer></v-spacer>
                      <v-btn
                        size="small"
                        variant="text"
                        icon
                        @click="refreshSourceTables"
                      >
                        <v-icon size="small">mdi-refresh</v-icon>
                      </v-btn>
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
                    <v-text-field
                      v-model="sourceTableSearch"
                      label="搜索表"
                      prepend-inner-icon="mdi-magnify"
                      density="compact"
                      variant="outlined"
                      hide-details
                      class="mb-2"
                    ></v-text-field>
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
                      <template v-else>
                        <v-checkbox
                          v-for="table in filteredSourceTables"
                          :key="table"
                          v-model="selectedTables"
                          :value="table"
                          density="compact"
                          hide-details
                          :disabled="targetTableSet.size && !targetTableSet.has(table)"
                        >
                          <template #label>
                            <div class="d-flex align-center">
                              <span>{{ table }}</span>
                              <v-chip
                                v-if="targetTableSet.size && !targetTableSet.has(table)"
                                size="x-small"
                                color="warning"
                                variant="text"
                                class="ml-2"
                              >
                                目标缺失
                              </v-chip>
                            </div>
                          </template>
                        </v-checkbox>
                        <v-alert
                          v-if="filteredSourceTables.length === 0"
                          type="info"
                          variant="tonal"
                          density="compact"
                        >
                          未找到表
                        </v-alert>
                      </template>
                    </v-sheet>
                    <v-alert
                      v-if="sourceOnlyCount > 0"
                      type="warning"
                      variant="tonal"
                      density="compact"
                      class="mt-2"
                    >
                      有 {{ sourceOnlyCount }} 个源库表在目标库不存在，将被跳过同步
                    </v-alert>
                  </v-col>

                  <!-- 目标库表列表（只读） -->
                  <v-col cols="12" md="6">
                    <div class="d-flex align-center mb-2">
                      <v-chip color="success" size="small" class="mr-2">
                        目标库表 ({{ targetTables.length }})
                      </v-chip>
                      <v-chip
                        v-if="targetOnlyCount > 0"
                        color="warning"
                        size="small"
                        variant="tonal"
                      >
                        源缺失 {{ targetOnlyCount }}
                      </v-chip>
                      <v-spacer></v-spacer>
                      <v-btn
                        size="small"
                        variant="text"
                        icon
                        @click="refreshTargetTables"
                      >
                        <v-icon size="small">mdi-refresh</v-icon>
                      </v-btn>
                    </div>
                    <v-text-field
                      v-model="targetTableSearch"
                      label="搜索表"
                      prepend-inner-icon="mdi-magnify"
                      density="compact"
                      variant="outlined"
                      hide-details
                      class="mb-2"
                    ></v-text-field>
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
                      <template v-else>
                        <v-list density="compact">
                          <v-list-item
                            v-for="table in filteredTargetTables"
                            :key="table"
                            :title="table"
                          >
                            <template v-slot:prepend>
                              <v-icon size="small">mdi-table</v-icon>
                            </template>
                            <template v-slot:append>
                              <v-chip
                                v-if="!sourceTableSet.has(table)"
                                size="x-small"
                                color="warning"
                                variant="text"
                              >
                                源缺失
                              </v-chip>
                            </template>
                          </v-list-item>
                        </v-list>
                        <v-alert
                          v-if="filteredTargetTables.length === 0"
                          type="info"
                          variant="tonal"
                          density="compact"
                        >
                          未找到表
                        </v-alert>
                      </template>
                    </v-sheet>
                    <v-alert
                      v-if="targetOnlyCount > 0"
                      type="warning"
                      variant="tonal"
                      density="compact"
                      class="mt-2"
                    >
                      有 {{ targetOnlyCount }} 个目标库表在源库不存在
                    </v-alert>
                  </v-col>
                </v-row>

                <v-alert
                  v-if="selectionStats.total > 0"
                  type="success"
                  variant="tonal"
                  density="compact"
                  class="mt-4"
                >
                  已选择 {{ selectionStats.total }} 个表，可同步 {{ selectionStats.valid }} 个
                </v-alert>
                <v-alert
                  v-else
                  type="warning"
                  variant="tonal"
                  density="compact"
                  class="mt-4"
                >
                  请至少选择一个表进行比对
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
              高级选项
            </v-expansion-panel-title>
            <v-expansion-panel-text>
              <v-row>
                <v-col cols="12" md="6">
                  <v-switch
                    v-model="compareOptions.compareContent"
                    label="比对数据内容"
                    color="primary"
                    hint="关闭后只统计行数"
                    persistent-hint
                  ></v-switch>
                </v-col>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model.number="compareOptions.batchSize"
                    label="批次大小"
                    type="number"
                    variant="outlined"
                    density="compact"
                    hint="每批次比对的行数"
                    persistent-hint
                  ></v-text-field>
                </v-col>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model.number="compareOptions.maxRows"
                    label="最大行数"
                    type="number"
                    variant="outlined"
                    density="compact"
                    hint="0 表示不限制"
                    persistent-hint
                  ></v-text-field>
                </v-col>
                <v-col cols="12" md="6">
                  <v-switch
                    v-model="compareOptions.ignoreCase"
                    label="忽略大小写"
                    color="primary"
                    hint="字符串比对时忽略大小写"
                    persistent-hint
                  ></v-switch>
                </v-col>
              </v-row>
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>

        <!-- 操作按钮 -->
        <v-row class="mt-4">
          <v-col cols="12" class="text-center">
            <v-btn
              color="primary"
              size="large"
              :loading="comparing"
              :disabled="!canCompare"
              prepend-icon="mdi-play"
              @click="startCompare"
              class="mr-2"
            >
              开始比对
            </v-btn>
            <v-btn
              v-if="diffResult"
              color="success"
              size="large"
              :disabled="!hasDataToSync"
              prepend-icon="mdi-sync"
              @click="showSyncDialog = true"
            >
              同步数据
            </v-btn>
            <div v-if="selectedTables.length > 0" class="text-caption text-grey mt-2">
              将比对 {{ selectedTables.length }} 个表
            </div>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- 比对结果 -->
    <v-card v-if="diffResult" class="mt-4">
      <v-card-title class="bg-success">
        <v-icon class="mr-2">mdi-check-circle</v-icon>
        <span class="text-h5 text-white">比对结果</span>
      </v-card-title>

      <v-card-text class="pt-4">
        <!-- 统计卡片 -->
        <v-row>
          <v-col cols="12" md="3">
            <v-card color="primary" variant="tonal">
              <v-card-text>
                <div class="text-subtitle-2">新增行数</div>
                <div class="text-h4">{{ diffResult.statistics?.insertRows || 0 }}</div>
                <div class="text-caption">源库有，目标库没有</div>
              </v-card-text>
            </v-card>
          </v-col>
          <v-col cols="12" md="3">
            <v-card color="warning" variant="tonal">
              <v-card-text>
                <div class="text-subtitle-2">更新行数</div>
                <div class="text-h4">{{ diffResult.statistics?.updateRows || 0 }}</div>
                <div class="text-caption">两边都有但内容不同</div>
              </v-card-text>
            </v-card>
          </v-col>
          <v-col cols="12" md="3">
            <v-card color="error" variant="tonal">
              <v-card-text>
                <div class="text-subtitle-2">删除行数</div>
                <div class="text-h4">{{ diffResult.statistics?.deleteRows || 0 }}</div>
                <div class="text-caption">目标库有，源库没有</div>
              </v-card-text>
            </v-card>
          </v-col>
          <v-col cols="12" md="3">
            <v-card color="success" variant="tonal">
              <v-card-text>
                <div class="text-subtitle-2">相同行数</div>
                <div class="text-h4">{{ diffResult.statistics?.identicalRows || 0 }}</div>
                <div class="text-caption">完全相同的数据</div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>

        <v-alert
          v-if="latestDiffTaskId"
          type="info"
          variant="tonal"
          density="compact"
          class="mt-4"
        >
          <div class="d-flex align-center">
            <span>任务 ID: {{ latestDiffTaskId }}</span>
            <v-spacer></v-spacer>
            <v-btn
              size="small"
              variant="text"
              color="primary"
              @click="openTaskCenter(latestDiffTaskId)"
            >
              查看任务
            </v-btn>
          </div>
        </v-alert>

        <!-- 表差异列表 -->
        <v-data-table
          :headers="tableHeaders"
          :items="diffResult.tableDiffs"
          :items-per-page="10"
          class="elevation-1 mt-4"
        >
          <template v-slot:item.tableName="{ item }">
            <div class="d-flex align-center">
              <v-icon class="mr-2" :color="item.insertCount > 0 || item.updateCount > 0 || item.deleteCount > 0 ? 'warning' : 'success'">
                mdi-table
              </v-icon>
              <span class="font-weight-medium">{{ item.tableName }}</span>
            </div>
          </template>

          <template v-slot:item.status="{ item }">
            <v-chip :color="getStatusColor(item.status)" size="small" variant="flat">
              {{ getStatusText(item.status) }}
            </v-chip>
          </template>

          <template v-slot:item.insertCount="{ item }">
            <v-chip
              v-if="item.insertCount > 0"
              color="primary"
              size="small"
              variant="tonal"
            >
              <v-icon start size="x-small">mdi-plus</v-icon>
              {{ item.insertCount }}
            </v-chip>
            <span v-else class="text-grey">-</span>
          </template>

          <template v-slot:item.updateCount="{ item }">
            <v-chip
              v-if="item.updateCount > 0"
              color="warning"
              size="small"
              variant="tonal"
            >
              <v-icon start size="x-small">mdi-pencil</v-icon>
              {{ item.updateCount }}
            </v-chip>
            <span v-else class="text-grey">-</span>
          </template>

          <template v-slot:item.deleteCount="{ item }">
            <v-chip
              v-if="item.deleteCount > 0"
              color="error"
              size="small"
              variant="tonal"
            >
              <v-icon start size="x-small">mdi-minus</v-icon>
              {{ item.deleteCount }}
            </v-chip>
            <span v-else class="text-grey">-</span>
          </template>

          <template v-slot:item.identicalCount="{ item }">
            <v-chip
              v-if="item.identicalCount > 0"
              color="success"
              size="small"
              variant="text"
            >
              {{ item.identicalCount }}
            </v-chip>
            <span v-else class="text-grey">-</span>
          </template>

          <template v-slot:item.hasDiff="{ item }">
            <v-tooltip :text="getDiffSummary(item)">
              <template v-slot:activator="{ props }">
                <v-icon
                  v-bind="props"
                  v-if="item.insertCount > 0 || item.updateCount > 0 || item.deleteCount > 0"
                  color="warning"
                  size="large"
                >
                  mdi-alert-circle
                </v-icon>
                <v-icon
                  v-bind="props"
                  v-else
                  color="success"
                  size="large"
                >
                  mdi-check-circle
                </v-icon>
              </template>
            </v-tooltip>
          </template>

          <template v-slot:item.actions="{ item }">
            <v-tooltip :text="getActionTooltip(item)">
              <template v-slot:activator="{ props }">
                <v-btn
                  v-bind="props"
                  v-if="hasDetailToShow(item)"
                  icon
                  size="small"
                  variant="tonal"
                  color="primary"
                  @click="showTableDetail(item)"
                >
                  <v-icon>mdi-eye</v-icon>
                </v-btn>
                <v-chip
                  v-else
                  v-bind="props"
                  size="small"
                  color="grey"
                  variant="text"
                >
                  无样本
                </v-chip>
              </template>
            </v-tooltip>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <!-- 详细差异对话框 -->
    <v-dialog v-model="detailDialog.show" max-width="1200" scrollable>
      <v-card>
        <v-card-title class="bg-primary">
          <v-icon class="mr-2">mdi-table-eye</v-icon>
          <span class="text-h5 text-white">表差异详情: {{ detailDialog.tableName }}</span>
          <v-spacer></v-spacer>
          <v-btn icon variant="text" @click="detailDialog.show = false">
            <v-icon color="white">mdi-close</v-icon>
          </v-btn>
        </v-card-title>

        <v-card-text class="pt-4">
          <!-- 统计信息 -->
          <v-row class="mb-4">
            <v-col cols="12" md="3">
              <v-card color="blue-lighten-4" variant="flat">
                <v-card-text class="text-center">
                  <div class="text-h6">{{ detailDialog.data?.sourceRowCount || 0 }}</div>
                  <div class="text-caption">源库行数</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="3">
              <v-card color="green-lighten-4" variant="flat">
                <v-card-text class="text-center">
                  <div class="text-h6">{{ detailDialog.data?.targetRowCount || 0 }}</div>
                  <div class="text-caption">目标库行数</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="3">
              <v-card color="orange-lighten-4" variant="flat">
                <v-card-text class="text-center">
                  <div class="text-h6">{{ detailDialog.data?.totalDiffCount || 0 }}</div>
                  <div class="text-caption">总差异数</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="3">
              <v-card color="purple-lighten-4" variant="flat">
                <v-card-text class="text-center">
                  <div class="text-h6">{{ detailDialog.data?.identicalCount || 0 }}</div>
                  <div class="text-caption">相同行数</div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>

          <!-- 主键信息 -->
          <v-alert type="info" variant="tonal" class="mb-4">
            <div class="text-subtitle-2">主键列</div>
            <v-chip
              v-for="pk in detailDialog.data?.primaryKeys"
              :key="pk"
              size="small"
              class="mr-2"
            >
              {{ pk }}
            </v-chip>
          </v-alert>

          <!-- 差异类型筛选和显示选项 -->
          <v-row class="mb-4">
            <v-col cols="12" md="8">
              <div class="text-subtitle-2 mb-2">筛选差异类型</div>
              <v-chip-group v-model="detailDialog.filterType" mandatory>
                <v-chip value="ALL" color="grey" variant="flat">
                  <v-icon start size="small">mdi-format-list-bulleted</v-icon>
                  全部 ({{ countDiffType('ALL') }})
                </v-chip>
                <v-chip value="INSERT" color="primary" variant="flat">
                  <v-icon start size="small">mdi-plus-circle</v-icon>
                  新增 ({{ countDiffType('INSERT') }})
                </v-chip>
                <v-chip value="UPDATE" color="warning" variant="flat">
                  <v-icon start size="small">mdi-pencil-circle</v-icon>
                  更新 ({{ countDiffType('UPDATE') }})
                </v-chip>
                <v-chip value="DELETE" color="error" variant="flat">
                  <v-icon start size="small">mdi-delete-circle</v-icon>
                  删除 ({{ countDiffType('DELETE') }})
                </v-chip>
              </v-chip-group>
            </v-col>
            <v-col cols="12" md="4" class="d-flex align-center justify-end">
              <v-switch
                v-model="globalShowOnlyDiff"
                label="只显示差异字段"
                color="warning"
                density="compact"
                hide-details
                @update:model-value="updateAllDiffViewOptions"
              >
                <template v-slot:prepend>
                  <v-icon>mdi-filter-variant</v-icon>
                </template>
              </v-switch>
            </v-col>
          </v-row>

          <!-- 差异列表 - 改进的详细视图 -->
          <div class="diff-list">
            <v-card
              v-for="(diff, index) in filteredDiffs"
              :key="index"
              :class="['diff-card', 'mb-4']"
              variant="outlined"
              :color="getDiffTypeColor(diff.diffType)"
            >
              <!-- 卡片头部 -->
              <v-card-title :class="['d-flex align-center pa-3', getDiffHeaderClass(diff.diffType)]">
                <v-chip
                  :color="getDiffTypeColor(diff.diffType)"
                  size="small"
                  class="mr-2"
                  variant="elevated"
                >
                  <v-icon start size="small">{{ getDiffTypeIcon(diff.diffType) }}</v-icon>
                  {{ getDiffTypeText(diff.diffType) }}
                </v-chip>
                <span class="text-subtitle-1 font-weight-bold">主键: {{ diff.primaryKeyValue }}</span>
                <v-spacer></v-spacer>
                <v-tooltip text="只显示差异字段">
                  <template v-slot:activator="{ props }">
                    <v-btn
                      v-bind="props"
                      icon
                      size="small"
                      variant="text"
                      @click="toggleShowOnlyDiff(index)"
                    >
                      <v-icon>{{ diffViewOptions[index]?.showOnlyDiff ? 'mdi-eye-off' : 'mdi-eye' }}</v-icon>
                    </v-btn>
                  </template>
                </v-tooltip>
                <v-tooltip text="复制主键值">
                  <template v-slot:activator="{ props }">
                    <v-btn
                      v-bind="props"
                      icon
                      size="small"
                      variant="text"
                      @click="copyToClipboard(diff.primaryKeyValue)"
                    >
                      <v-icon>mdi-content-copy</v-icon>
                    </v-btn>
                  </template>
                </v-tooltip>
              </v-card-title>

              <v-divider></v-divider>

              <!-- 卡片内容 - 左右对比布局 -->
              <v-card-text class="pa-0">
                <!-- INSERT 类型：左右对比 -->
                <div v-if="diff.diffType === 'INSERT' && diff.sourceData" class="pa-4">
                  <v-alert type="info" variant="tonal" density="compact" class="mb-3">
                    <v-icon start>mdi-plus-circle</v-icon>
                    此行将被新增到目标库
                  </v-alert>

                  <v-row dense>
                    <v-col cols="6">
                      <div class="text-caption text-grey mb-2">
                        <v-icon size="small" class="mr-1">mdi-database</v-icon>源库数据
                      </div>
                      <v-table density="compact" class="diff-table">
                        <tbody>
                          <tr v-for="field in parseRowData(diff.sourceData)" :key="field.name">
                            <td class="field-name-cell">{{ field.name }}</td>
                            <td class="field-value-cell new-value">{{ field.value }}</td>
                          </tr>
                        </tbody>
                      </v-table>
                    </v-col>
                    <v-col cols="6">
                      <div class="text-caption text-grey mb-2">
                        <v-icon size="small" class="mr-1">mdi-database-outline</v-icon>目标库数据
                      </div>
                      <div class="empty-placeholder">
                        <v-icon color="grey-lighten-2">mdi-database-off</v-icon>
                        <div class="text-caption text-grey">不存在</div>
                      </div>
                    </v-col>
                  </v-row>
                </div>

                <!-- DELETE 类型：左右对比 -->
                <div v-else-if="diff.diffType === 'DELETE' && diff.targetData" class="pa-4">
                  <v-alert type="error" variant="tonal" density="compact" class="mb-3">
                    <v-icon start>mdi-delete-circle</v-icon>
                    此行将从目标库删除
                  </v-alert>

                  <v-row dense>
                    <v-col cols="6">
                      <div class="text-caption text-grey mb-2">
                        <v-icon size="small" class="mr-1">mdi-database</v-icon>源库数据
                      </div>
                      <div class="empty-placeholder">
                        <v-icon color="grey-lighten-2">mdi-database-off</v-icon>
                        <div class="text-caption text-grey">不存在</div>
                      </div>
                    </v-col>
                    <v-col cols="6">
                      <div class="text-caption text-grey mb-2">
                        <v-icon size="small" class="mr-1">mdi-database-outline</v-icon>目标库数据
                      </div>
                      <v-table density="compact" class="diff-table">
                        <tbody>
                          <tr v-for="field in parseRowData(diff.targetData)" :key="field.name">
                            <td class="field-name-cell">{{ field.name }}</td>
                            <td class="field-value-cell old-value">{{ field.value }}</td>
                          </tr>
                        </tbody>
                      </v-table>
                    </v-col>
                  </v-row>
                </div>

                <!-- UPDATE 类型：左右对比 -->
                <div v-else-if="diff.diffType === 'UPDATE'" class="pa-4">
                  <v-alert type="warning" variant="tonal" density="compact" class="mb-3 d-flex align-center">
                    <v-icon start>mdi-pencil-circle</v-icon>
                    <span>此行存在字段差异，将更新目标库</span>
                    <v-spacer></v-spacer>
                    <v-chip size="small" color="warning" variant="flat">
                      {{ getFieldDiffs(diff).filter(f => f.isDifferent).length }} 个字段不同
                    </v-chip>
                    <v-switch
                      v-if="diffViewOptions[index]"
                      v-model="diffViewOptions[index].showOnlyDiff"
                      density="compact"
                      color="warning"
                      hide-details
                      class="ml-4"
                    >
                      <template v-slot:label>
                        <span class="text-caption">只显示差异</span>
                      </template>
                    </v-switch>
                  </v-alert>

                  <v-row dense>
                    <v-col cols="6">
                      <div class="text-caption text-grey mb-2">
                        <v-icon size="small" class="mr-1">mdi-database</v-icon>源库数据
                      </div>
                      <v-table density="compact" class="diff-table">
                        <tbody>
                          <tr
                            v-for="field in getFieldDiffs(diff)"
                            :key="field.name"
                            v-show="!diffViewOptions[index]?.showOnlyDiff || field.isDifferent"
                            :class="field.isDifferent ? 'row-different' : 'row-same'"
                          >
                            <td class="field-name-cell">
                              <v-icon
                                v-if="field.isDifferent"
                                size="x-small"
                                color="warning"
                                class="mr-1"
                              >
                                mdi-alert-circle
                              </v-icon>
                              {{ field.name }}
                            </td>
                            <td class="field-value-cell" :class="field.isDifferent ? 'old-value' : ''">
                              {{ field.sourceValue }}
                            </td>
                          </tr>
                        </tbody>
                      </v-table>
                    </v-col>
                    <v-col cols="6">
                      <div class="text-caption text-grey mb-2">
                        <v-icon size="small" class="mr-1">mdi-database-outline</v-icon>目标库数据
                      </div>
                      <v-table density="compact" class="diff-table">
                        <tbody>
                          <tr
                            v-for="field in getFieldDiffs(diff)"
                            :key="field.name"
                            v-show="!diffViewOptions[index]?.showOnlyDiff || field.isDifferent"
                            :class="field.isDifferent ? 'row-different' : 'row-same'"
                          >
                            <td class="field-name-cell">
                              <v-icon
                                v-if="field.isDifferent"
                                size="x-small"
                                color="warning"
                                class="mr-1"
                              >
                                mdi-alert-circle
                              </v-icon>
                              {{ field.name }}
                            </td>
                            <td class="field-value-cell" :class="field.isDifferent ? 'new-value' : ''">
                              {{ field.targetValue }}
                            </td>
                          </tr>
                        </tbody>
                      </v-table>
                    </v-col>
                  </v-row>
                </div>

                <!-- 无数据情况 -->
                <div v-else class="text-center text-grey pa-8">
                  <v-icon size="64" color="grey-lighten-2">mdi-database-off</v-icon>
                  <div class="text-body-2 mt-2">无数据</div>
                </div>
              </v-card-text>
            </v-card>
          </div>

          <!-- 无差异提示 -->
          <v-alert v-if="filteredDiffs.length === 0" type="info" variant="tonal" class="mt-4">
            <div class="d-flex align-center">
              <v-icon class="mr-2">mdi-information</v-icon>
              <div>
                <div class="font-weight-bold">没有找到 {{ getDiffTypeText(detailDialog.filterType) }} 类型的差异</div>
                <div class="text-caption mt-1">
                  总差异数: {{ countDiffType('ALL') }} |
                  新增: {{ countDiffType('INSERT') }} |
                  更新: {{ countDiffType('UPDATE') }} |
                  删除: {{ countDiffType('DELETE') }}
                </div>
              </div>
            </div>
          </v-alert>

          <!-- 样本说明 -->
          <v-alert v-if="filteredDiffs.length > 0" type="warning" variant="tonal" class="mt-4">
            <v-icon class="mr-2">mdi-information</v-icon>
            注意：这里只显示前 10 条差异样本，实际差异数量请查看统计信息
          </v-alert>

          <!-- 调试信息（开发模式） -->
          <v-expansion-panels v-if="filteredDiffs.length === 0 && detailDialog.data" class="mt-4">
            <v-expansion-panel>
              <v-expansion-panel-title>
                <v-icon class="mr-2">mdi-bug</v-icon>
                调试信息
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <pre class="text-caption">{{ JSON.stringify({
                  tableName: detailDialog.tableName,
                  filterType: detailDialog.filterType,
                  sampleDiffsCount: detailDialog.data?.sampleDiffs?.length || 0,
                  insertCount: detailDialog.data?.insertCount || 0,
                  updateCount: detailDialog.data?.updateCount || 0,
                  deleteCount: detailDialog.data?.deleteCount || 0,
                  sampleDiffs: detailDialog.data?.sampleDiffs
                }, null, 2) }}</pre>
              </v-expansion-panel-text>
            </v-expansion-panel>
          </v-expansion-panels>
        </v-card-text>

        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="primary" variant="text" @click="detailDialog.show = false">
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 数据同步对话框 -->
    <v-dialog v-model="showSyncDialog" max-width="800" persistent>
      <v-card>
        <v-card-title class="d-flex align-center">
          <v-icon class="mr-2" color="success">mdi-sync</v-icon>
          数据同步配置
        </v-card-title>

        <v-divider></v-divider>

        <v-card-text class="pa-6">
          <!-- 同步选项 -->
          <v-row>
            <v-col cols="12">
              <div class="text-subtitle-2 mb-2">同步操作类型</div>
              <v-checkbox
                v-model="syncOptions.executeInsert"
                label="执行 INSERT（新增）"
                color="primary"
                density="compact"
                hide-details
              ></v-checkbox>
              <v-checkbox
                v-model="syncOptions.executeUpdate"
                label="执行 UPDATE（更新）"
                color="primary"
                density="compact"
                hide-details
              ></v-checkbox>
              <v-checkbox
                v-model="syncOptions.executeDelete"
                label="执行 DELETE（删除）"
                color="error"
                density="compact"
                hide-details
              ></v-checkbox>
            </v-col>

            <v-col cols="12" md="6">
              <v-text-field
                v-model.number="syncOptions.batchSize"
                label="批次大小"
                type="number"
                variant="outlined"
                density="compact"
                hint="每批次处理的行数"
                persistent-hint
              ></v-text-field>
            </v-col>

            <v-col cols="12" md="6">
              <v-switch
                v-model="syncOptions.useTransaction"
                label="使用事务"
                color="primary"
                density="compact"
                hint="失败时自动回滚"
                persistent-hint
              ></v-switch>
            </v-col>

            <v-col cols="12">
              <v-switch
                v-model="syncOptions.dryRun"
                label="仅生成 SQL（不执行）"
                color="warning"
                density="compact"
                hint="只生成 SQL 语句，不实际执行同步"
                persistent-hint
              ></v-switch>
            </v-col>
          </v-row>

          <!-- 同步预览 -->
          <v-alert v-if="diffResult" type="info" variant="tonal" class="mt-4">
            <div class="text-subtitle-2 mb-2">将要同步的数据：</div>
            <div v-if="syncOptions.executeInsert">
              <v-icon size="small" class="mr-1">mdi-plus-circle</v-icon>
              新增：{{ totalInsertCount }} 行
            </div>
            <div v-if="syncOptions.executeUpdate">
              <v-icon size="small" class="mr-1">mdi-pencil-circle</v-icon>
              更新：{{ totalUpdateCount }} 行
            </div>
            <div v-if="syncOptions.executeDelete">
              <v-icon size="small" class="mr-1">mdi-delete-circle</v-icon>
              删除：{{ totalDeleteCount }} 行
            </div>
            <div class="mt-2 font-weight-bold">
              总计：{{ totalSyncCount }} 行
            </div>
          </v-alert>

          <!-- 警告提示 -->
          <v-alert v-if="syncOptions.executeDelete && !syncOptions.dryRun" type="error" variant="tonal" class="mt-4">
            <v-icon class="mr-2">mdi-alert</v-icon>
            警告：删除操作不可逆，请谨慎操作！
          </v-alert>
        </v-card-text>

        <v-divider></v-divider>

        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="grey" variant="text" @click="showSyncDialog = false">
            取消
          </v-btn>
          <v-btn
            color="success"
            variant="elevated"
            :loading="syncing"
            :disabled="!canSync"
            @click="executeSyncData"
          >
            <v-icon start>mdi-check</v-icon>
            {{ syncOptions.dryRun ? '生成 SQL' : '开始同步' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 同步结果对话框 -->
    <v-dialog v-model="showSyncResultDialog" max-width="900" scrollable>
      <v-card>
        <v-card-title class="d-flex align-center">
          <v-icon class="mr-2" :color="syncResult?.status === 'COMPLETED' ? 'success' : 'error'">
            {{ syncResult?.status === 'COMPLETED' ? 'mdi-check-circle' : 'mdi-alert-circle' }}
          </v-icon>
          同步结果
        </v-card-title>

        <v-divider></v-divider>

        <v-card-text class="pa-6">
          <!-- 统计信息 -->
          <v-row v-if="syncResult?.statistics">
            <v-col cols="6" md="3">
              <v-card variant="tonal" color="primary">
                <v-card-text class="text-center">
                  <div class="text-h4">{{ syncResult.statistics.tableCount }}</div>
                  <div class="text-caption">表数量</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="6" md="3">
              <v-card variant="tonal" color="success">
                <v-card-text class="text-center">
                  <div class="text-h4">{{ syncResult.statistics.totalInsertedRows }}</div>
                  <div class="text-caption">新增行数</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="6" md="3">
              <v-card variant="tonal" color="warning">
                <v-card-text class="text-center">
                  <div class="text-h4">{{ syncResult.statistics.totalUpdatedRows }}</div>
                  <div class="text-caption">更新行数</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="6" md="3">
              <v-card variant="tonal" color="error">
                <v-card-text class="text-center">
                  <div class="text-h4">{{ syncResult.statistics.totalDeletedRows }}</div>
                  <div class="text-caption">删除行数</div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>

          <!-- 表同步结果列表 -->
          <div v-if="syncResult?.tableSyncResults" class="mt-6">
            <div class="text-subtitle-1 mb-3">表同步详情</div>
            <v-table density="compact">
              <thead>
                <tr>
                  <th>表名</th>
                  <th>新增</th>
                  <th>更新</th>
                  <th>删除</th>
                  <th>耗时</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="result in syncResult.tableSyncResults" :key="result.tableName">
                  <td>{{ result.tableName }}</td>
                  <td>{{ result.insertedRows || 0 }}</td>
                  <td>{{ result.updatedRows || 0 }}</td>
                  <td>{{ result.deletedRows || 0 }}</td>
                  <td>{{ result.executionTime }}ms</td>
                  <td>
                    <v-chip
                      :color="result.status === 'SUCCESS' ? 'success' : result.status === 'FAILED' ? 'error' : 'grey'"
                      size="small"
                    >
                      {{ result.status }}
                    </v-chip>
                    <div v-if="result.errorMessage" class="text-caption text-error mt-1">
                      {{ result.errorMessage }}
                    </div>
                  </td>
                </tr>
              </tbody>
            </v-table>
          </div>

          <v-alert
            v-if="latestSyncTaskId"
            type="info"
            variant="tonal"
            density="compact"
            class="mt-4"
          >
            <div class="d-flex align-center">
              <span>任务 ID: {{ latestSyncTaskId }}</span>
              <v-spacer></v-spacer>
              <v-btn
                size="small"
                variant="text"
                color="primary"
                @click="openTaskCenter(latestSyncTaskId)"
              >
                查看任务
              </v-btn>
            </div>
          </v-alert>

          <!-- 生成的 SQL（dryRun 模式） -->
          <div v-if="syncResult?.generatedSqls && syncResult.generatedSqls.length > 0" class="mt-6">
            <div class="text-subtitle-1 mb-3">生成的 SQL 语句</div>
            <v-card variant="outlined">
              <v-card-text>
                <pre class="sql-preview">{{ syncResult.generatedSqls.join('\n\n') }}</pre>
              </v-card-text>
            </v-card>
            <div class="mt-2">
              <v-btn
                color="primary"
                variant="text"
                @click="copySqlToClipboard"
              >
                <v-icon start>mdi-content-copy</v-icon>
                复制 SQL
              </v-btn>
              <v-btn
                color="primary"
                variant="text"
                @click="downloadSql"
              >
                <v-icon start>mdi-download</v-icon>
                下载 SQL
              </v-btn>
            </div>
          </div>
        </v-card-text>

        <v-divider></v-divider>

        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="primary" variant="text" @click="showSyncResultDialog = false">
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 消息提示 -->
    <v-snackbar
      v-model="snackbar.show"
      :color="snackbar.color"
      :timeout="3000"
    >
      {{ snackbar.message }}
    </v-snackbar>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import api from '@/services/api'
import { useConnectionStore } from '@/store/connections'
import { useSchemaStore } from '@/store/schema'

const COMPARE_OPTION_KEY = 'minicat-data-compare-options'
const SYNC_OPTION_KEY = 'minicat-data-sync-options'

const loadPersistedOptions = (key, defaults) => {
  try {
    const stored = localStorage.getItem(key)
    if (!stored) {
      return { ...defaults }
    }
    return { ...defaults, ...JSON.parse(stored) }
  } catch (error) {
    return { ...defaults }
  }
}

const persistOptions = (key, options) => {
  try {
    localStorage.setItem(key, JSON.stringify(options))
  } catch (error) {
    console.error('持久化选项失败:', error)
  }
}

const filterTables = (tables = [], keyword = '') => {
  if (!Array.isArray(tables)) {
    return []
  }
  const normalized = keyword?.trim().toLowerCase()
  if (!normalized) {
    return tables
  }
  return tables.filter(table => table.toLowerCase().includes(normalized))
}

const buildTablePresence = (source = [], target = []) => {
  const targetSet = new Set(target || [])
  const sourceSet = new Set(source || [])
  const overlap = (source || []).filter(table => targetSet.has(table))
  const sourceOnly = (source || []).filter(table => !targetSet.has(table))
  const targetOnly = (target || []).filter(table => !sourceSet.has(table))
  return { overlap, sourceOnly, targetOnly }
}

const getErrorMessage = (error) => {
  return error?.response?.data?.message || error?.message || '未知错误'
}

const router = useRouter()
const connectionStore = useConnectionStore()
const schemaStore = useSchemaStore()
const { connections } = storeToRefs(connectionStore)

const sourceConnectionId = ref(null)
const targetConnectionId = ref(null)
const selectedTables = ref([])
const comparing = ref(false)
const diffResult = ref(null)
const latestDiffTaskId = ref('')
const detailDialog = ref({
  show: false,
  tableName: '',
  data: null,
  filterType: 'ALL'
})
const diffViewOptions = ref({})
const globalShowOnlyDiff = ref(false)
const showSyncDialog = ref(false)
const showSyncResultDialog = ref(false)
const syncing = ref(false)
const syncResult = ref(null)
const latestSyncTaskId = ref('')
const sourceTableSearch = ref('')
const targetTableSearch = ref('')
const snackbar = ref({
  show: false,
  message: '',
  color: 'success'
})
const compareOptions = ref(loadPersistedOptions(COMPARE_OPTION_KEY, {
  compareContent: true,
  batchSize: 1000,
  maxRows: 0,
  ignoreCase: false
}))
const syncOptions = ref(loadPersistedOptions(SYNC_OPTION_KEY, {
  executeInsert: true,
  executeUpdate: true,
  executeDelete: false,
  batchSize: 1000,
  useTransaction: true,
  dryRun: false
}))
const showMessage = (message, color = 'success') => {
  snackbar.value = {
    show: true,
    message,
    color
  }
}

const sourceTables = computed(() => schemaStore.getTables(sourceConnectionId.value))
const targetTables = computed(() => schemaStore.getTables(targetConnectionId.value))
const loadingSourceTables = computed(() => schemaStore.isLoading(sourceConnectionId.value))
const loadingTargetTables = computed(() => schemaStore.isLoading(targetConnectionId.value))
const sourceTableSet = computed(() => new Set(sourceTables.value || []))
const targetTableSet = computed(() => new Set(targetTables.value || []))
const filteredSourceTables = computed(() => filterTables(sourceTables.value, sourceTableSearch.value))
const filteredTargetTables = computed(() => filterTables(targetTables.value, targetTableSearch.value))
const tablePresenceSummary = computed(() => buildTablePresence(sourceTables.value, targetTables.value))
const validSourceTables = computed(() => tablePresenceSummary.value.overlap)
const sourceOnlyCount = computed(() => tablePresenceSummary.value.sourceOnly.length)
const targetOnlyCount = computed(() => tablePresenceSummary.value.targetOnly.length)
const selectionStats = computed(() => ({
  total: selectedTables.value.length,
  valid: targetTableSet.value.size === 0
    ? selectedTables.value.length
    : selectedTables.value.filter(table => targetTableSet.value.has(table)).length
}))
const canCompare = computed(() =>
  Boolean(
    sourceConnectionId.value &&
    targetConnectionId.value &&
    selectedTables.value.length > 0 &&
    !comparing.value
  )
)

const tableHeaders = [
  { title: '表名', key: 'tableName', sortable: true },
  { title: '状态', key: 'status', sortable: true },
  { title: '源行数', key: 'sourceRowCount', sortable: true },
  { title: '目标行数', key: 'targetRowCount', sortable: true },
  { title: '新增', key: 'insertCount', sortable: true },
  { title: '更新', key: 'updateCount', sortable: true },
  { title: '删除', key: 'deleteCount', sortable: true },
  { title: '相同', key: 'identicalCount', sortable: true },
  { title: '差异', key: 'hasDiff', sortable: false },
  { title: '操作', key: 'actions', sortable: false }
]

const loadConnections = async () => {
  try {
    await connectionStore.fetchConnections()
  } catch (error) {
    showMessage('加载连接列表失败: ' + getErrorMessage(error), 'error')
  }
}

const loadSourceTables = async (force = false) => {
  if (!sourceConnectionId.value) {
    return
  }
  try {
    await schemaStore.fetchTables(sourceConnectionId.value, { force })
  } catch (error) {
    showMessage('加载源库表列表失败: ' + getErrorMessage(error), 'error')
  }
}

const loadTargetTables = async (force = false) => {
  if (!targetConnectionId.value) {
    return
  }
  try {
    await schemaStore.fetchTables(targetConnectionId.value, { force })
  } catch (error) {
    showMessage('加载目标库表列表失败: ' + getErrorMessage(error), 'error')
  }
}

const refreshSourceTables = () => loadSourceTables(true)
const refreshTargetTables = () => loadTargetTables(true)

const onSourceConnectionChange = async () => {
  selectedTables.value = []
  sourceTableSearch.value = ''
  await loadSourceTables()
}

const onTargetConnectionChange = async () => {
  targetTableSearch.value = ''
  await loadTargetTables()
}

const selectAllSourceTables = () => {
  if (!sourceTables.value) {
    selectedTables.value = []
    return
  }
  if (targetTableSet.value.size === 0) {
    selectedTables.value = [...sourceTables.value]
    return
  }
  selectedTables.value = [...validSourceTables.value]
}

const clearSourceTables = () => {
  selectedTables.value = []
}

const startCompare = async () => {
  if (!canCompare.value) {
    return
  }

  comparing.value = true
  diffResult.value = null
  detailDialog.value = { show: false, tableName: '', data: null, filterType: 'ALL' }
  diffViewOptions.value = {}

  try {
    const request = {
      sourceConnectionId: sourceConnectionId.value,
      targetConnectionId: targetConnectionId.value,
      tableNames: selectedTables.value,
      options: compareOptions.value
    }
    const response = await api.data.compare(request)
    diffResult.value = response
    latestDiffTaskId.value = response.taskId || ''
    showMessage('数据比对完成', 'success')
  } catch (error) {
    showMessage('数据比对失败: ' + getErrorMessage(error), 'error')
  } finally {
    comparing.value = false
  }
}

const hasDetailToShow = (item) => {
  const sampleCount = item.sampleDiffs?.length || 0
  const fullCount = item.allDiffs?.length || 0
  return sampleCount > 0 || fullCount > 0
}

const getActionTooltip = (item) => {
  const sampleCount = item.sampleDiffs?.length || 0
  const fullCount = item.allDiffs?.length || 0
  const total = fullCount || sampleCount
  if (total > 0) {
    return `查看详细差异 (${total} 条记录)`
  }
  if (item.insertCount > 0 || item.updateCount > 0 || item.deleteCount > 0) {
    return '存在差异但未采集样本'
  }
  return '该表没有差异'
}

const resolveDetailDiffs = () => {
  const data = detailDialog.value.data
  if (!data) {
    return []
  }
  if (Array.isArray(data.allDiffs) && data.allDiffs.length > 0) {
    return data.allDiffs
  }
  return data.sampleDiffs || []
}

const filteredDiffs = computed(() => {
  const diffs = resolveDetailDiffs()
  if (!diffs.length) {
    return []
  }
  if (detailDialog.value.filterType === 'ALL') {
    return diffs
  }
  return diffs.filter(diff => diff.diffType === detailDialog.value.filterType)
})

const showTableDetail = (tableDiff) => {
  detailDialog.value = {
    show: true,
    tableName: tableDiff.tableName,
    data: tableDiff,
    filterType: 'ALL'
  }
  diffViewOptions.value = {}
  updateAllDiffViewOptions(globalShowOnlyDiff.value)
}

const countDiffType = (type) => {
  const diffs = resolveDetailDiffs()
  if (type === 'ALL') {
    return diffs.length
  }
  return diffs.filter(diff => diff.diffType === type).length
}

const getDiffTypeColor = (type) => {
  switch (type) {
    case 'INSERT': return 'primary'
    case 'UPDATE': return 'warning'
    case 'DELETE': return 'error'
    default: return 'grey'
  }
}

const getDiffTypeText = (type) => {
  switch (type) {
    case 'INSERT': return '新增'
    case 'UPDATE': return '更新'
    case 'DELETE': return '删除'
    case 'ALL': return '全部'
    default: return '未知'
  }
}

const getDiffTypeIcon = (type) => {
  switch (type) {
    case 'INSERT': return 'mdi-plus-circle'
    case 'UPDATE': return 'mdi-pencil-circle'
    case 'DELETE': return 'mdi-delete-circle'
    default: return 'mdi-help-circle'
  }
}

const getDiffHeaderClass = (type) => {
  switch (type) {
    case 'INSERT': return 'bg-blue-lighten-5'
    case 'UPDATE': return 'bg-orange-lighten-5'
    case 'DELETE': return 'bg-red-lighten-5'
    default: return ''
  }
}

const toggleShowOnlyDiff = (index) => {
  if (!diffViewOptions.value[index]) {
    diffViewOptions.value[index] = { showOnlyDiff: false }
  }
  diffViewOptions.value[index].showOnlyDiff = !diffViewOptions.value[index].showOnlyDiff
}

const getDiffSummary = (item) => {
  const parts = []
  if (item.insertCount > 0) parts.push(`新增 ${item.insertCount} 行`)
  if (item.updateCount > 0) parts.push(`更新 ${item.updateCount} 行`)
  if (item.deleteCount > 0) parts.push(`删除 ${item.deleteCount} 行`)
  if (item.identicalCount > 0) parts.push(`相同 ${item.identicalCount} 行`)
  return parts.length > 0 ? parts.join(', ') : '无差异'
}

const updateAllDiffViewOptions = (value) => {
  const diffs = resolveDetailDiffs()
  diffs.forEach((_, index) => {
    diffViewOptions.value[index] = { showOnlyDiff: value }
  })
}

const formatJson = (jsonStr) => {
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch (error) {
    return jsonStr
  }
}

const parseRowData = (jsonStr) => {
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return Object.entries(obj || {}).map(([name, value]) => ({
      name,
      value: value !== null && value !== undefined ? String(value) : 'NULL'
    }))
  } catch (error) {
    return []
  }
}

const getFieldDiffs = (diff) => {
  try {
    const sourceData = typeof diff.sourceData === 'string'
      ? JSON.parse(diff.sourceData)
      : diff.sourceData
    const targetData = typeof diff.targetData === 'string'
      ? JSON.parse(diff.targetData)
      : diff.targetData

    const fields = []
    const allKeys = new Set([
      ...Object.keys(sourceData || {}),
      ...Object.keys(targetData || {})
    ])

    for (const key of allKeys) {
      const sourceValue = sourceData?.[key]
      const targetValue = targetData?.[key]
      fields.push({
        name: key,
        sourceValue: sourceValue !== undefined ? String(sourceValue) : '-',
        targetValue: targetValue !== undefined ? String(targetValue) : '-',
        isDifferent: sourceValue !== targetValue
      })
    }

    return fields
  } catch (error) {
    return []
  }
}

const getRowDiffTitle = (diffType) => {
  switch (diffType) {
    case 'INSERT': return '需要插入的行'
    case 'UPDATE': return '需要更新的行'
    case 'DELETE': return '需要删除的行'
    default: return '未知操作'
  }
}

const getRowDiffStatus = (diffType) => {
  switch (diffType) {
    case 'INSERT': return '源库有但目标库没有'
    case 'UPDATE': return '两边都有但内容不同'
    case 'DELETE': return '目标库多余的行'
    default: return '未知状态'
  }
}

const isFieldDiff = (diff, fieldName) => {
  try {
    const sourceData = typeof diff.sourceData === 'string'
      ? JSON.parse(diff.sourceData)
      : diff.sourceData
    const targetData = typeof diff.targetData === 'string'
      ? JSON.parse(diff.targetData)
      : diff.targetData
    return sourceData?.[fieldName] !== targetData?.[fieldName]
  } catch (error) {
    return false
  }
}

const getRowClass = (diffType) => {
  switch (diffType) {
    case 'INSERT': return 'bg-blue-lighten-5'
    case 'UPDATE': return 'bg-orange-lighten-5'
    case 'DELETE': return 'bg-red-lighten-5'
    default: return ''
  }
}

const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    showMessage('已复制到剪贴板', 'success')
  } catch (error) {
    showMessage('复制失败', 'error')
  }
}

const hasDataToSync = computed(() => {
  if (!diffResult.value?.tableDiffs) {
    return false
  }
  return diffResult.value.tableDiffs.some(table =>
    table.insertCount > 0 || table.updateCount > 0 || table.deleteCount > 0
  )
})

const totalInsertCount = computed(() => {
  if (!diffResult.value?.tableDiffs) {
    return 0
  }
  return diffResult.value.tableDiffs.reduce((sum, table) =>
    sum + (table.insertCount || 0), 0
  )
})

const totalUpdateCount = computed(() => {
  if (!diffResult.value?.tableDiffs) {
    return 0
  }
  return diffResult.value.tableDiffs.reduce((sum, table) =>
    sum + (table.updateCount || 0), 0
  )
})

const totalDeleteCount = computed(() => {
  if (!diffResult.value?.tableDiffs) {
    return 0
  }
  return diffResult.value.tableDiffs.reduce((sum, table) =>
    sum + (table.deleteCount || 0), 0
  )
})

const totalSyncCount = computed(() => {
  let count = 0
  if (syncOptions.value.executeInsert) count += totalInsertCount.value
  if (syncOptions.value.executeUpdate) count += totalUpdateCount.value
  if (syncOptions.value.executeDelete) count += totalDeleteCount.value
  return count
})

const canSync = computed(() => {
  return (syncOptions.value.executeInsert ||
          syncOptions.value.executeUpdate ||
          syncOptions.value.executeDelete) &&
         totalSyncCount.value > 0
})

const executeSyncData = async () => {
  if (!canSync.value) {
    return
  }

  syncing.value = true

  try {
    const request = {
      sourceConnectionId: sourceConnectionId.value,
      targetConnectionId: targetConnectionId.value,
      tableNames: selectedTables.value,
      options: syncOptions.value
    }
    const result = await api.data.sync(request)
    syncResult.value = result
    latestSyncTaskId.value = result.taskId || ''
    showSyncDialog.value = false
    showSyncResultDialog.value = true

    if (result.status === 'COMPLETED') {
      showMessage('数据同步完成', 'success')
      if (!syncOptions.value.dryRun) {
        await startCompare()
      }
    } else {
      showMessage('数据同步失败: ' + (result.errorMessage || '未知错误'), 'error')
    }
  } catch (error) {
    showMessage('数据同步失败: ' + getErrorMessage(error), 'error')
  } finally {
    syncing.value = false
  }
}

const copySqlToClipboard = async () => {
  if (!syncResult.value?.generatedSqls?.length) {
    return
  }
  const sql = syncResult.value.generatedSqls.join('\n\n')
  await copyToClipboard(sql)
}

const downloadSql = () => {
  if (!syncResult.value?.generatedSqls?.length) {
    return
  }
  const sql = syncResult.value.generatedSqls.join('\n\n')
  const sourceConn = connections.value.find(conn => conn.id === sourceConnectionId.value)
  const targetConn = connections.value.find(conn => conn.id === targetConnectionId.value)
  const stats = syncResult.value.statistics || {}
  const header = `-- 数据同步 SQL 脚本
-- 生成时间: ${new Date().toLocaleString('zh-CN')}
-- 源数据库: ${sourceConn?.name || ''} (${sourceConn?.host}:${sourceConn?.port}/${sourceConn?.database})
-- 目标数据库: ${targetConn?.name || ''} (${targetConn?.host}:${targetConn?.port}/${targetConn?.database})
-- 表: ${selectedTables.value.join(', ')}
--
-- 统计信息:
--   新增行数: ${stats.totalInsertedRows || 0}
--   更新行数: ${stats.totalUpdatedRows || 0}
--   删除行数: ${stats.totalDeletedRows || 0}
--
-- 注意: 请在执行前仔细检查 SQL 语句！
-- ============================================================

`
  const content = header + sql
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5)
  const tableNames = selectedTables.value.join('_')
  link.download = `sync_${tableNames || 'tables'}_${timestamp}.sql`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  showMessage('SQL 文件已下载', 'success')
}

const getDbTypeIcon = (type) => {
  switch (type) {
    case 'mysql': return 'mdi-database'
    case 'postgresql': return 'mdi-elephant'
    default: return 'mdi-database'
  }
}

const getDbTypeColor = (type) => {
  switch (type) {
    case 'mysql': return 'blue'
    case 'postgresql': return 'green'
    default: return 'grey'
  }
}

const getStatusColor = (status) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'error'
    case 'NO_PRIMARY_KEY': return 'warning'
    default: return 'grey'
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'NO_PRIMARY_KEY': return '无主键'
    default: return '未知'
  }
}

const openTaskCenter = (taskId) => {
  if (!taskId) {
    return
  }
  router.push({ name: 'tasks', query: { taskId } })
}

watch(compareOptions, (value) => persistOptions(COMPARE_OPTION_KEY, value), { deep: true })
watch(syncOptions, (value) => persistOptions(SYNC_OPTION_KEY, value), { deep: true })
watch(sourceConnectionId, async () => {
  await loadSourceTables()
})
watch(targetConnectionId, async () => {
  await loadTargetTables()
})
watch(selectedTables, (tables) => {
  const unique = Array.from(new Set(tables))
  if (unique.length !== tables.length) {
    selectedTables.value = unique
  }
}, { deep: true })

let trimmingSelection = false
watch(
  () => [selectedTables.value, targetTableSet.value],
  () => {
    if (trimmingSelection || targetTableSet.value.size === 0 || !selectedTables.value.length) {
      return
    }
    const filtered = selectedTables.value.filter(table => targetTableSet.value.has(table))
    if (filtered.length !== selectedTables.value.length) {
      trimmingSelection = true
      selectedTables.value = filtered
      trimmingSelection = false
      showMessage('已移除目标库不存在的表', 'warning')
    }
  },
  { deep: true }
)

watch(
  () => [sourceConnectionId.value, targetConnectionId.value],
  () => {
    diffResult.value = null
    latestDiffTaskId.value = ''
  }
)

onMounted(() => {
  loadConnections()
})
</script>

<style scoped>
/* 差异列表 */
.diff-list {
  max-height: 600px;
  overflow-y: auto;
}

/* 差异卡片 */
.diff-card {
  transition: all 0.3s;
  border-width: 2px !important;
}

.diff-card:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

/* 字段列表 */
.field-list {
  background-color: transparent;
}

.field-item {
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  padding: 12px 16px;
  transition: background-color 0.2s;
}

.field-item:last-child {
  border-bottom: none;
}

.field-item:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

/* 字段不同/相同样式 */
.field-different {
  background-color: #fff9e6 !important;
}

.field-different:hover {
  background-color: #fff3cc !important;
}

.field-same {
  opacity: 0.7;
}

/* 字段对比 */
.field-comparison {
  width: 100%;
}

.field-name-row {
  display: flex;
  align-items: center;
}

.field-name {
  font-size: 14px;
  color: #1976d2;
  font-family: 'Courier New', monospace;
}

/* 值对比 */
.value-comparison {
  position: relative;
  padding-left: 24px;
}

.value-row {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.source-row {
  background-color: #e3f2fd;
}

.target-row {
  background-color: #e8f5e9;
}

.value-text {
  flex: 1;
  word-break: break-all;
}

.old-value {
  color: #d32f2f;
  text-decoration: line-through;
  opacity: 0.8;
}

.new-value {
  color: #388e3c;
  font-weight: 500;
}

.diff-arrow {
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
}

.value-same {
  padding-left: 24px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #666;
}

/* 差异行高亮 */
.bg-blue-lighten-5 {
  background-color: #e3f2fd !important;
}

.bg-orange-lighten-5 {
  background-color: #fff3e0 !important;
}

.bg-red-lighten-5 {
  background-color: #ffebee !important;
}

/* SQL 预览 */
.sql-preview {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  max-height: 400px;
  overflow-y: auto;
  background-color: #f5f5f5;
  padding: 16px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* 滚动条美化 */
.diff-list::-webkit-scrollbar {
  width: 8px;
}

.diff-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.diff-list::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 4px;
}

.diff-list::-webkit-scrollbar-thumb:hover {
  background: #555;
}

/* 动画效果 */
@keyframes highlight {
  0% {
    background-color: #fff9c4;
  }
  100% {
    background-color: transparent;
  }
}

.field-different {
  animation: highlight 2s ease-in-out;
}

/* 左右对比表格样式 */
.diff-table {
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
}

.diff-table tbody tr {
  transition: background-color 0.2s;
}

.diff-table tbody tr.row-different {
  background-color: #fff9e6;
}

.diff-table tbody tr.row-same {
  opacity: 0.7;
}

.diff-table tbody tr:hover {
  background-color: #f5f5f5;
}

.field-name-cell {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #1976d2;
  font-weight: 500;
  padding: 8px 12px !important;
  border-bottom: 1px solid #f0f0f0;
  width: 35%;
  white-space: nowrap;
}

.field-value-cell {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  padding: 8px 12px !important;
  border-bottom: 1px solid #f0f0f0;
  word-break: break-all;
}

.field-value-cell.old-value {
  background-color: #ffebee;
  color: #d32f2f;
}

.field-value-cell.new-value {
  background-color: #e8f5e9;
  color: #388e3c;
  font-weight: 500;
}

.empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  border: 2px dashed #e0e0e0;
  border-radius: 4px;
  background-color: #fafafa;
  min-height: 200px;
}

/* 响应式 */
@media (max-width: 960px) {
  .value-comparison {
    padding-left: 12px;
  }

  .value-row {
    font-size: 12px;
    padding: 4px 8px;
  }

  .field-name {
    font-size: 13px;
  }

  .field-name-cell,
  .field-value-cell {
    font-size: 12px;
    padding: 6px 8px !important;
  }
}
</style>
