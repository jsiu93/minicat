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

                  <!-- 目标库表列表（只读） -->
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

        <!-- 表差异列表 -->
        <v-data-table
          :headers="tableHeaders"
          :items="diffResult.tableDiffs"
          :items-per-page="10"
          class="elevation-1 mt-4"
        >
          <template v-slot:item.tableName="{ item }">
            <v-icon class="mr-2">mdi-table</v-icon>
            {{ item.tableName }}
          </template>

          <template v-slot:item.status="{ item }">
            <v-chip :color="getStatusColor(item.status)" size="small">
              {{ getStatusText(item.status) }}
            </v-chip>
          </template>

          <template v-slot:item.hasDiff="{ item }">
            <v-icon v-if="item.insertCount > 0 || item.updateCount > 0 || item.deleteCount > 0" color="warning">
              mdi-alert-circle
            </v-icon>
            <v-icon v-else color="success">
              mdi-check-circle
            </v-icon>
          </template>

          <template v-slot:item.actions="{ item }">
            <v-btn
              v-if="item.sampleDiffs && item.sampleDiffs.length > 0"
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

          <!-- 差异类型筛选 -->
          <v-row class="mb-4">
            <v-col cols="12">
              <v-chip-group v-model="detailDialog.filterType" mandatory>
                <v-chip value="ALL" color="grey">
                  全部 ({{ detailDialog.data?.sampleDiffs?.length || 0 }})
                </v-chip>
                <v-chip value="INSERT" color="primary">
                  新增 ({{ countDiffType('INSERT') }})
                </v-chip>
                <v-chip value="UPDATE" color="warning">
                  更新 ({{ countDiffType('UPDATE') }})
                </v-chip>
                <v-chip value="DELETE" color="error">
                  删除 ({{ countDiffType('DELETE') }})
                </v-chip>
              </v-chip-group>
            </v-col>
          </v-row>

          <!-- 差异列表 - 卡片形式（横向布局） -->
          <div class="diff-list">
            <v-card
              v-for="(diff, index) in filteredDiffs"
              :key="index"
              :class="['diff-card', 'mb-4', getRowClass(diff.diffType)]"
              variant="outlined"
            >
              <!-- 卡片头部 -->
              <v-card-title class="d-flex align-center pa-3">
                <v-chip
                  :color="getDiffTypeColor(diff.diffType)"
                  size="small"
                  class="mr-2"
                >
                  {{ getDiffTypeText(diff.diffType) }}
                </v-chip>
                <span class="text-subtitle-2">主键: {{ diff.primaryKeyValue }}</span>
                <v-spacer></v-spacer>
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

              <!-- 卡片内容 - 横向对比 -->
              <v-card-text class="pa-0">
                <v-row no-gutters>
                  <!-- 源库数据 -->
                  <v-col cols="12" md="6" class="border-right">
                    <div class="pa-3">
                      <div class="text-subtitle-2 mb-2 d-flex align-center">
                        <v-icon size="small" class="mr-1" color="primary">mdi-database-arrow-right</v-icon>
                        源库数据
                      </div>
                      <div v-if="diff.sourceData" class="data-table-wrapper">
                        <table class="data-table">
                          <thead>
                            <tr>
                              <th
                                v-for="field in parseRowData(diff.sourceData)"
                                :key="field.name"
                                :class="isFieldDifferent(diff, field.name) ? 'bg-yellow-lighten-4' : ''"
                              >
                                <v-icon
                                  v-if="isFieldDifferent(diff, field.name)"
                                  size="x-small"
                                  color="warning"
                                  class="mr-1"
                                >
                                  mdi-alert-circle
                                </v-icon>
                                <span class="text-caption font-weight-bold">{{ field.name }}</span>
                              </th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr>
                              <td
                                v-for="field in parseRowData(diff.sourceData)"
                                :key="field.name"
                                :class="isFieldDifferent(diff, field.name) ? 'bg-yellow-lighten-4' : ''"
                              >
                                <span class="text-caption">{{ field.value }}</span>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                      <div v-else class="text-center text-grey pa-4">
                        <v-icon size="large">mdi-minus-circle-outline</v-icon>
                        <div class="text-caption mt-2">无数据</div>
                      </div>
                    </div>
                  </v-col>

                  <!-- 目标库数据 -->
                  <v-col cols="12" md="6">
                    <div class="pa-3">
                      <div class="text-subtitle-2 mb-2 d-flex align-center">
                        <v-icon size="small" class="mr-1" color="success">mdi-database-arrow-left</v-icon>
                        目标库数据
                      </div>
                      <div v-if="diff.targetData" class="data-table-wrapper">
                        <table class="data-table">
                          <thead>
                            <tr>
                              <th
                                v-for="field in parseRowData(diff.targetData)"
                                :key="field.name"
                                :class="isFieldDifferent(diff, field.name) ? 'bg-yellow-lighten-4' : ''"
                              >
                                <v-icon
                                  v-if="isFieldDifferent(diff, field.name)"
                                  size="x-small"
                                  color="warning"
                                  class="mr-1"
                                >
                                  mdi-alert-circle
                                </v-icon>
                                <span class="text-caption font-weight-bold">{{ field.name }}</span>
                              </th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr>
                              <td
                                v-for="field in parseRowData(diff.targetData)"
                                :key="field.name"
                                :class="isFieldDifferent(diff, field.name) ? 'bg-yellow-lighten-4' : ''"
                              >
                                <span class="text-caption">{{ field.value }}</span>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                      <div v-else class="text-center text-grey pa-4">
                        <v-icon size="large">mdi-minus-circle-outline</v-icon>
                        <div class="text-caption mt-2">无数据</div>
                      </div>
                    </div>
                  </v-col>
                </v-row>
              </v-card-text>
            </v-card>
          </div>

          <!-- 无差异提示 -->
          <v-alert v-if="filteredDiffs.length === 0" type="info" variant="tonal" class="mt-4">
            没有找到 {{ getDiffTypeText(detailDialog.filterType) }} 类型的差异
          </v-alert>

          <!-- 样本说明 -->
          <v-alert type="warning" variant="tonal" class="mt-4">
            <v-icon class="mr-2">mdi-information</v-icon>
            注意：这里只显示前 10 条差异样本，实际差异数量请查看统计信息
          </v-alert>
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
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'

// 连接列表
const connections = ref([])

// 选中的连接
const sourceConnectionId = ref(null)
const targetConnectionId = ref(null)

// 表列表
const sourceTables = ref([])
const targetTables = ref([])
const selectedTables = ref([])

// 加载状态
const loadingSourceTables = ref(false)
const loadingTargetTables = ref(false)
const comparing = ref(false)

// 比对选项
const compareOptions = ref({
  compareContent: true,
  batchSize: 1000,
  maxRows: 0,
  ignoreCase: false
})

// 比对结果
const diffResult = ref(null)

// 详细差异对话框
const detailDialog = ref({
  show: false,
  tableName: '',
  data: null,
  filterType: 'ALL'
})

// 同步相关
const showSyncDialog = ref(false)
const showSyncResultDialog = ref(false)
const syncing = ref(false)
const syncResult = ref(null)

// 同步选项
const syncOptions = ref({
  executeInsert: true,
  executeUpdate: true,
  executeDelete: false,
  batchSize: 1000,
  useTransaction: true,
  dryRun: false
})

// 表头
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

// 消息提示
const snackbar = ref({
  show: false,
  message: '',
  color: 'success'
})

// 计算属性
const canCompare = computed(() => {
  return sourceConnectionId.value &&
         targetConnectionId.value &&
         selectedTables.value.length > 0 &&
         !comparing.value
})

// 过滤后的差异列表
const filteredDiffs = computed(() => {
  if (!detailDialog.value.data?.sampleDiffs) {
    return []
  }

  if (detailDialog.value.filterType === 'ALL') {
    return detailDialog.value.data.sampleDiffs
  }

  return detailDialog.value.data.sampleDiffs.filter(
    diff => diff.diffType === detailDialog.value.filterType
  )
})

// 加载连接列表
const loadConnections = async () => {
  try {
    const response = await api.connections.getAll()
    connections.value = response
  } catch (error) {
    console.error('加载连接列表失败:', error)
    showMessage('加载连接列表失败: ' + error.message, 'error')
  }
}

// 源连接变化
const onSourceConnectionChange = async () => {
  selectedTables.value = []
  await loadSourceTables()
}

// 目标连接变化
const onTargetConnectionChange = async () => {
  await loadTargetTables()
}

// 加载源库表列表
const loadSourceTables = async () => {
  if (!sourceConnectionId.value) return

  loadingSourceTables.value = true
  try {
    const response = await api.schema.getTables(sourceConnectionId.value)
    sourceTables.value = response
  } catch (error) {
    console.error('加载源库表列表失败:', error)
    showMessage('加载源库表列表失败: ' + error.message, 'error')
  } finally {
    loadingSourceTables.value = false
  }
}

// 加载目标库表列表
const loadTargetTables = async () => {
  if (!targetConnectionId.value) return

  loadingTargetTables.value = true
  try {
    const response = await api.schema.getTables(targetConnectionId.value)
    targetTables.value = response
  } catch (error) {
    console.error('加载目标库表列表失败:', error)
    showMessage('加载目标库表列表失败: ' + error.message, 'error')
  } finally {
    loadingTargetTables.value = false
  }
}

// 全选源库表
const selectAllSourceTables = () => {
  selectedTables.value = [...sourceTables.value]
}

// 清空选择
const clearSourceTables = () => {
  selectedTables.value = []
}

// 开始比对
const startCompare = async () => {
  if (!canCompare.value) return

  comparing.value = true
  diffResult.value = null

  try {
    const request = {
      sourceConnectionId: sourceConnectionId.value,
      targetConnectionId: targetConnectionId.value,
      tableNames: selectedTables.value,
      options: compareOptions.value
    }

    console.log('开始数据比对:', request)
    const response = await api.data.compare(request)

    diffResult.value = response
    showMessage('数据比对完成', 'success')
  } catch (error) {
    console.error('数据比对失败:', error)
    const errorMsg = error.response?.data?.message || error.message
    showMessage('数据比对失败: ' + errorMsg, 'error')
  } finally {
    comparing.value = false
  }
}

// 显示表详情
const showTableDetail = (tableDiff) => {
  console.log('查看表详情:', tableDiff)

  detailDialog.value = {
    show: true,
    tableName: tableDiff.tableName,
    data: tableDiff,
    filterType: 'ALL'
  }
}

// 统计差异类型数量
const countDiffType = (type) => {
  if (!detailDialog.value.data?.sampleDiffs) {
    return 0
  }

  if (type === 'ALL') {
    return detailDialog.value.data.sampleDiffs.length
  }

  return detailDialog.value.data.sampleDiffs.filter(
    diff => diff.diffType === type
  ).length
}

// 获取差异类型颜色
const getDiffTypeColor = (type) => {
  switch (type) {
    case 'INSERT': return 'primary'
    case 'UPDATE': return 'warning'
    case 'DELETE': return 'error'
    default: return 'grey'
  }
}

// 获取差异类型文本
const getDiffTypeText = (type) => {
  switch (type) {
    case 'INSERT': return '新增'
    case 'UPDATE': return '更新'
    case 'DELETE': return '删除'
    case 'ALL': return '全部'
    default: return '未知'
  }
}

// 格式化 JSON
const formatJson = (jsonStr) => {
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return jsonStr
  }
}

// 获取字段差异
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
      const isDifferent = sourceValue !== targetValue

      fields.push({
        name: key,
        sourceValue: sourceValue !== undefined ? String(sourceValue) : '-',
        targetValue: targetValue !== undefined ? String(targetValue) : '-',
        isDifferent
      })
    }

    // 先显示不同的字段
    return fields.sort((a, b) => {
      if (a.isDifferent && !b.isDifferent) return -1
      if (!a.isDifferent && b.isDifferent) return 1
      return 0
    })
  } catch (e) {
    console.error('解析字段差异失败:', e)
    return []
  }
}

// 获取数据库类型图标
const getDbTypeIcon = (type) => {
  switch (type) {
    case 'mysql': return 'mdi-database'
    case 'postgresql': return 'mdi-elephant'
    default: return 'mdi-database'
  }
}

// 获取数据库类型颜色
const getDbTypeColor = (type) => {
  switch (type) {
    case 'mysql': return 'blue'
    case 'postgresql': return 'green'
    default: return 'grey'
  }
}

// 获取状态颜色
const getStatusColor = (status) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'error'
    case 'NO_PRIMARY_KEY': return 'warning'
    default: return 'grey'
  }
}

// 获取状态文本
const getStatusText = (status) => {
  switch (status) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'NO_PRIMARY_KEY': return '无主键'
    default: return '未知'
  }
}

// 显示消息
const showMessage = (message, color = 'success') => {
  snackbar.value = {
    show: true,
    message,
    color
  }
}

// 解析行数据为字段数组
const parseRowData = (jsonStr) => {
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return Object.entries(obj).map(([name, value]) => ({
      name,
      value: value !== null && value !== undefined ? String(value) : 'NULL'
    }))
  } catch (e) {
    console.error('解析行数据失败:', e)
    return []
  }
}

// 判断字段是否不同
const isFieldDifferent = (diff, fieldName) => {
  if (diff.diffType !== 'UPDATE') {
    return false
  }

  try {
    const sourceData = typeof diff.sourceData === 'string'
      ? JSON.parse(diff.sourceData)
      : diff.sourceData
    const targetData = typeof diff.targetData === 'string'
      ? JSON.parse(diff.targetData)
      : diff.targetData

    return sourceData?.[fieldName] !== targetData?.[fieldName]
  } catch (e) {
    return false
  }
}

// 获取行样式类
const getRowClass = (diffType) => {
  switch (diffType) {
    case 'INSERT': return 'bg-blue-lighten-5'
    case 'UPDATE': return 'bg-orange-lighten-5'
    case 'DELETE': return 'bg-red-lighten-5'
    default: return ''
  }
}

// 复制到剪贴板
const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    showMessage('已复制到剪贴板', 'success')
  } catch (e) {
    console.error('复制失败:', e)
    showMessage('复制失败', 'error')
  }
}

// 是否有数据需要同步
const hasDataToSync = computed(() => {
  if (!diffResult.value?.tableDiffs) return false

  return diffResult.value.tableDiffs.some(table =>
    (table.insertCount > 0 || table.updateCount > 0 || table.deleteCount > 0)
  )
})

// 计算总的新增数量
const totalInsertCount = computed(() => {
  if (!diffResult.value?.tableDiffs) return 0

  return diffResult.value.tableDiffs.reduce((sum, table) =>
    sum + (table.insertCount || 0), 0
  )
})

// 计算总的更新数量
const totalUpdateCount = computed(() => {
  if (!diffResult.value?.tableDiffs) return 0

  return diffResult.value.tableDiffs.reduce((sum, table) =>
    sum + (table.updateCount || 0), 0
  )
})

// 计算总的删除数量
const totalDeleteCount = computed(() => {
  if (!diffResult.value?.tableDiffs) return 0

  return diffResult.value.tableDiffs.reduce((sum, table) =>
    sum + (table.deleteCount || 0), 0
  )
})

// 计算总的同步数量
const totalSyncCount = computed(() => {
  let count = 0
  if (syncOptions.value.executeInsert) count += totalInsertCount.value
  if (syncOptions.value.executeUpdate) count += totalUpdateCount.value
  if (syncOptions.value.executeDelete) count += totalDeleteCount.value
  return count
})

// 是否可以同步
const canSync = computed(() => {
  return (syncOptions.value.executeInsert ||
          syncOptions.value.executeUpdate ||
          syncOptions.value.executeDelete) &&
         totalSyncCount.value > 0
})

// 执行数据同步
const executeSyncData = async () => {
  if (!canSync.value) return

  syncing.value = true

  try {
    const request = {
      sourceConnectionId: sourceConnectionId.value,
      targetConnectionId: targetConnectionId.value,
      tableNames: selectedTables.value,
      options: syncOptions.value
    }

    console.log('发送同步请求:', request)

    const result = await api.data.sync(request)

    console.log('同步结果:', result)

    syncResult.value = result
    showSyncDialog.value = false
    showSyncResultDialog.value = true

    if (result.status === 'COMPLETED') {
      showMessage('数据同步完成', 'success')

      // 重新比对数据
      if (!syncOptions.value.dryRun) {
        await startCompare()
      }
    } else {
      showMessage('数据同步失败: ' + result.errorMessage, 'error')
    }

  } catch (error) {
    console.error('数据同步失败:', error)
    const errorMsg = error.response?.data?.message || error.message
    showMessage('数据同步失败: ' + errorMsg, 'error')
  } finally {
    syncing.value = false
  }
}

// 复制 SQL 到剪贴板
const copySqlToClipboard = async () => {
  if (!syncResult.value?.generatedSqls) return

  const sql = syncResult.value.generatedSqls.join('\n\n')
  await copyToClipboard(sql)
}

// 下载 SQL 文件
const downloadSql = () => {
  if (!syncResult.value?.generatedSqls) return

  // 生成 SQL 内容
  const sql = syncResult.value.generatedSqls.join('\n\n')

  // 添加文件头注释
  const sourceConn = connections.value.find(c => c.id === sourceConnectionId.value)
  const targetConn = connections.value.find(c => c.id === targetConnectionId.value)

  const header = `-- 数据同步 SQL 脚本
-- 生成时间: ${new Date().toLocaleString('zh-CN')}
-- 源数据库: ${sourceConn?.name || ''} (${sourceConn?.host}:${sourceConn?.port}/${sourceConn?.database})
-- 目标数据库: ${targetConn?.name || ''} (${targetConn?.host}:${targetConn?.port}/${targetConn?.database})
-- 表: ${selectedTables.value.join(', ')}
--
-- 统计信息:
--   新增行数: ${syncResult.value.statistics?.insertedRows || 0}
--   更新行数: ${syncResult.value.statistics?.updatedRows || 0}
--   删除行数: ${syncResult.value.statistics?.deletedRows || 0}
--
-- 注意: 请在执行前仔细检查 SQL 语句！
-- ============================================================

`

  const content = header + sql

  // 创建 Blob
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })

  // 创建下载链接
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url

  // 生成文件名：sync_表名_时间戳.sql
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5)
  const tableNames = selectedTables.value.join('_')
  link.download = `sync_${tableNames}_${timestamp}.sql`

  // 触发下载
  document.body.appendChild(link)
  link.click()

  // 清理
  document.body.removeChild(link)
  URL.revokeObjectURL(url)

  showMessage('SQL 文件已下载', 'success')
}

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
}

.diff-card:hover {
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

/* 边框 */
.border-right {
  border-right: 1px solid rgba(0, 0, 0, 0.12);
}

/* 数据表格容器 */
.data-table-wrapper {
  overflow-x: auto;
  max-width: 100%;
}

/* 数据表格 */
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.data-table th,
.data-table td {
  padding: 8px 12px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  text-align: left;
  white-space: nowrap;
  min-width: 80px;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.data-table th {
  background-color: rgba(0, 0, 0, 0.03);
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.data-table td {
  background-color: white;
}

/* 差异行高亮 */
.bg-yellow-lighten-4 {
  background-color: #fff9c4 !important;
}

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

/* 响应式 */
@media (max-width: 960px) {
  .border-right {
    border-right: none;
    border-bottom: 1px solid rgba(0, 0, 0, 0.12);
  }
}
</style>


