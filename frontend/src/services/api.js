import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor
api.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// Response interceptor
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default {
  // Connection APIs
  connections: {
    getAll: () => api.get('/connections'),
    getById: (id) => api.get(`/connections/${id}`),
    create: (data) => api.post('/connections', data),
    update: (id, data) => api.put(`/connections/${id}`, data),
    delete: (id) => api.delete(`/connections/${id}`),
    test: (id) => api.post(`/connections/${id}/test`)
  },
  
  // Task APIs
  tasks: {
    getAll: (params) => api.get('/tasks', { params }),
    getById: (id) => api.get(`/tasks/${id}`),
    create: (type) => api.post('/tasks', null, { params: { type } })
  },

  // Schema APIs
  schema: {
    getTables: (connectionId) => api.get(`/schema/tables/${connectionId}`),
    compare: (data) => api.post('/schema/compare', data),
    generateSyncSql: (data) => api.post('/schema/generate-sync-sql', data),
    executeSync: (data) => api.post('/schema/sync', data)
  },

  // Data APIs
  data: {
    compare: (data) => api.post('/data/compare', data),
    sync: (data) => api.post('/data/sync', data)
  }
}
