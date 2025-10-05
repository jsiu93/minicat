<template>
  <div>
    <v-card class="mb-6">
      <v-card-title class="text-h4">
        欢迎使用 Minicat
      </v-card-title>
      <v-card-subtitle>
        数据库结构与数据比对同步工具
      </v-card-subtitle>
      <v-card-text>
        <p class="text-body-1">
          Minicat 帮助您快速比对和同步不同数据库之间的结构和数据差异。
        </p>
      </v-card-text>
    </v-card>

    <v-row>
      <v-col cols="12" md="6" lg="3">
        <v-card color="primary" dark>
          <v-card-title>
            <v-icon large class="mr-2">mdi-database</v-icon>
            连接管理
          </v-card-title>
          <v-card-text>
            管理 MySQL、PostgreSQL 数据库连接
          </v-card-text>
          <v-card-actions>
            <v-btn variant="text" to="/connections">进入</v-btn>
          </v-card-actions>
        </v-card>
      </v-col>

      <v-col cols="12" md="6" lg="3">
        <v-card color="secondary" dark>
          <v-card-title>
            <v-icon large class="mr-2">mdi-table-compare</v-icon>
            结构比对
          </v-card-title>
          <v-card-text>
            比较两个数据库的表结构差异
          </v-card-text>
          <v-card-actions>
            <v-btn variant="text" to="/schema-diff">进入</v-btn>
          </v-card-actions>
        </v-card>
      </v-col>

      <v-col cols="12" md="6" lg="3">
        <v-card color="info" dark>
          <v-card-title>
            <v-icon large class="mr-2">mdi-file-compare</v-icon>
            数据比对
          </v-card-title>
          <v-card-text>
            比较两个数据库的数据内容差异
          </v-card-text>
          <v-card-actions>
            <v-btn variant="text" to="/data-diff">进入</v-btn>
          </v-card-actions>
        </v-card>
      </v-col>

      <v-col cols="12" md="6" lg="3">
        <v-card color="success" dark>
          <v-card-title>
            <v-icon large class="mr-2">mdi-format-list-bulleted</v-icon>
            任务列表
          </v-card-title>
          <v-card-text>
            查看历史任务和执行日志
          </v-card-text>
          <v-card-actions>
            <v-btn variant="text" to="/tasks">进入</v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script setup>
</script>
