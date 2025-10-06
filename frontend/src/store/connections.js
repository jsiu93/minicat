import { defineStore } from 'pinia'
import api from '@/services/api'

export const useConnectionStore = defineStore('connections', {
  state: () => ({
    connections: [],
    loading: false,
    error: null
  }),

  getters: {
    // 获取所有连接
    allConnections: (state) => state.connections,

    // 按类型获取连接
    connectionsByType: (state) => (type) => {
      return state.connections.filter(conn => conn.type === type)
    },

    // 获取 MySQL 连接数量
    mysqlCount: (state) => {
      return state.connections.filter(conn => conn.type === 'mysql').length
    },

    // 获取 PostgreSQL 连接数量
    postgresqlCount: (state) => {
      return state.connections.filter(conn => conn.type === 'postgresql').length
    },

    // 获取最近使用的连接
    recentConnections: (state) => {
      const oneDayAgo = new Date()
      oneDayAgo.setDate(oneDayAgo.getDate() - 1)
      
      return state.connections.filter(conn => {
        if (!conn.lastUsed) return false
        return new Date(conn.lastUsed) > oneDayAgo
      })
    },

    // 根据 ID 获取连接
    getConnectionById: (state) => (id) => {
      return state.connections.find(conn => conn.id === id)
    }
  },

  actions: {
    // 加载所有连接
    async fetchConnections() {
      this.loading = true
      this.error = null
      
      try {
        const data = await api.connections.getAll()
        this.connections = data || []
        return data
      } catch (error) {
        this.error = error.response?.data?.message || error.message
        throw error
      } finally {
        this.loading = false
      }
    },

    // 创建连接
    async createConnection(connectionData) {
      try {
        const newConnection = await api.connections.create(connectionData)
        this.connections.push(newConnection)
        return newConnection
      } catch (error) {
        this.error = error.response?.data?.message || error.message
        throw error
      }
    },

    // 更新连接
    async updateConnection(id, connectionData) {
      try {
        const updatedConnection = await api.connections.update(id, connectionData)
        const index = this.connections.findIndex(conn => conn.id === id)
        if (index !== -1) {
          this.connections[index] = updatedConnection
        }
        return updatedConnection
      } catch (error) {
        this.error = error.response?.data?.message || error.message
        throw error
      }
    },

    // 删除连接
    async deleteConnection(id) {
      try {
        await api.connections.delete(id)
        this.connections = this.connections.filter(conn => conn.id !== id)
      } catch (error) {
        this.error = error.response?.data?.message || error.message
        throw error
      }
    },

    // 测试连接
    async testConnection(id) {
      try {
        const result = await api.connections.test(id)
        return result
      } catch (error) {
        this.error = error.response?.data?.message || error.message
        throw error
      }
    },

    // 清除错误
    clearError() {
      this.error = null
    }
  }
})

