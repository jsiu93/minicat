<template>
  <v-app>
    <v-app-bar color="primary" prominent>
      <v-app-bar-nav-icon @click="drawer = !drawer"></v-app-bar-nav-icon>
      <v-toolbar-title>Minicat</v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn icon>
        <v-icon>mdi-theme-light-dark</v-icon>
      </v-btn>
    </v-app-bar>

    <v-navigation-drawer v-model="drawer" app>
      <v-list nav>
        <v-list-item
          v-for="item in menuItems"
          :key="item.title"
          :to="item.to"
          :prepend-icon="item.icon"
          :title="item.title"
        >
        </v-list-item>
      </v-list>
    </v-navigation-drawer>

    <v-main>
      <v-container fluid>
        <router-view />
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup>
import { ref } from 'vue'

const drawer = ref(true)

const menuItems = [
  { title: '首页', icon: 'mdi-home', to: '/' },
  { title: '连接管理', icon: 'mdi-database', to: '/connections' },
  { title: '结构比对', icon: 'mdi-table-compare', to: '/schema-diff' },
  { title: '数据比对', icon: 'mdi-file-compare', to: '/data-diff' },
  { title: '任务列表', icon: 'mdi-format-list-bulleted', to: '/tasks' }
]
</script>

<style>
#app {
  font-family: 'Roboto', sans-serif;
}
</style>
