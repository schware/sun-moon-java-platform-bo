import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './styles.css'

const root = document.getElementById('root')
if (!root) throw new Error('#root is missing from index.html')

ReactDOM.createRoot(root).render(
  <React.StrictMode>
    {/* BO lives under the /bo context-path, so the router is based there. */}
    <BrowserRouter basename="/bo">
      <App />
    </BrowserRouter>
  </React.StrictMode>
)
