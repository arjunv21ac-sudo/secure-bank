import { createContext, useContext, useState } from 'react'
import { AUTH_STORAGE_KEY, authApi } from '../api/client'

const AuthContext = createContext(null)

// Reads the "exp" claim from the JWT payload (the middle part of the token)
function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

function loadSavedUser() {
  try {
    const saved = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY))
    if (saved && !isTokenExpired(saved.token)) return saved
  } catch {
    // corrupted value: fall through and clear it
  }
  localStorage.removeItem(AUTH_STORAGE_KEY)
  return null
}

export function AuthProvider({ children }) {
  // user = { token, name, email, role } or null when logged out
  const [user, setUser] = useState(loadSavedUser)

  const saveSession = (authResponse) => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authResponse))
    setUser(authResponse)
    return authResponse
  }

  const login = async (credentials) => saveSession(await authApi.login(credentials))
  const register = async (form) => saveSession(await authApi.register(form))

  const logout = () => {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    setUser(null)
  }

  const value = { user, login, register, logout, isAdmin: user?.role === 'ADMIN' }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
