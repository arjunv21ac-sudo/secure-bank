import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import Navbar from './components/Navbar'
import ProtectedRoute from './components/ProtectedRoute'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import AccountDetails from './pages/AccountDetails'
import Transfer from './pages/Transfer'
import Beneficiaries from './pages/Beneficiaries'
import AdminDashboard from './pages/AdminDashboard'
import NotFound from './pages/NotFound'

// "/" sends each person to the right home: login, their accounts, or the admin panel
function Home() {
  const { user, isAdmin } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={isAdmin ? '/admin' : '/dashboard'} replace />
}

export default function App() {
  return (
    <>
      <Navbar />
      <main className="container">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          {/* Customers */}
          <Route path="/dashboard" element={<ProtectedRoute role="CUSTOMER"><Dashboard /></ProtectedRoute>} />
          <Route path="/accounts/:id" element={<ProtectedRoute role="CUSTOMER"><AccountDetails /></ProtectedRoute>} />
          <Route path="/transfer" element={<ProtectedRoute role="CUSTOMER"><Transfer /></ProtectedRoute>} />
          <Route path="/beneficiaries" element={<ProtectedRoute role="CUSTOMER"><Beneficiaries /></ProtectedRoute>} />

          {/* Bank staff */}
          <Route path="/admin" element={<ProtectedRoute role="ADMIN"><AdminDashboard /></ProtectedRoute>} />

          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </>
  )
}
