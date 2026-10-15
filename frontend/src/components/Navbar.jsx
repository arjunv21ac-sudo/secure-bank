import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Navbar() {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Link to="/" className="brand">🏦 SecureBank</Link>

        {user && (
          <nav className="nav-links">
            {isAdmin ? (
              <NavLink to="/admin">Admin panel</NavLink>
            ) : (
              <>
                <NavLink to="/dashboard">Accounts</NavLink>
                <NavLink to="/transfer">Transfer</NavLink>
                <NavLink to="/beneficiaries">Beneficiaries</NavLink>
              </>
            )}
          </nav>
        )}

        <div className="nav-user">
          {user ? (
            <>
              <span className="nav-name">
                {user.name}
                {isAdmin && <span className="badge badge-info">Staff</span>}
              </span>
              <button className="btn btn-ghost btn-sm" onClick={handleLogout}>Logout</button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-ghost btn-sm">Login</Link>
              <Link to="/register" className="btn btn-primary btn-sm">Open an account</Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
