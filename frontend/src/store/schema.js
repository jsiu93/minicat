import { defineStore } from 'pinia'
import api from '@/services/api'

const CACHE_TTL = 60 * 1000

export const useSchemaStore = defineStore('schema', {
  state: () => ({
    tableCache: {},
    loadingState: {},
    errorState: {}
  }),

  getters: {
    getTables: (state) => (connectionId) => {
      return state.tableCache[connectionId]?.tables || []
    },
    isLoading: (state) => (connectionId) => {
      return Boolean(state.loadingState[connectionId])
    },
    getError: (state) => (connectionId) => {
      return state.errorState[connectionId] || null
    }
  },

  actions: {
    async fetchTables(connectionId, { force = false } = {}) {
      if (!connectionId) {
        return []
      }

      const cached = this.tableCache[connectionId]
      if (!force && cached && Date.now() - cached.fetchedAt < CACHE_TTL) {
        return cached.tables
      }

      this.loadingState[connectionId] = true
      this.errorState[connectionId] = null

      try {
        const tables = await api.schema.getTables(connectionId)
        this.tableCache[connectionId] = {
          tables: tables || [],
          fetchedAt: Date.now()
        }
        return this.tableCache[connectionId].tables
      } catch (error) {
        this.errorState[connectionId] = error.response?.data?.message || error.message
        throw error
      } finally {
        this.loadingState[connectionId] = false
      }
    },

    invalidate(connectionId) {
      if (connectionId) {
        delete this.tableCache[connectionId]
      }
    },

    clear() {
      this.tableCache = {}
      this.loadingState = {}
      this.errorState = {}
    }
  }
})
