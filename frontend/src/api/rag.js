import axios from 'axios'

export const listCustomRagDocuments = () => axios.get('/api/rag/documents')
export const queryCustomRag = (data) => axios.post('/api/rag/query', data)
