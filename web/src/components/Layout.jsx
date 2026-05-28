import { Link, NavLink, Outlet } from 'react-router-dom';

const links = [
  { to: '/', label: 'Home', icon: '🏠' },
  { to: '/teams', label: 'Teams', icon: '⚔️' },
  { to: '/events', label: 'Events', icon: '🎮' },
  { to: '/players', label: 'Spelers', icon: '👤' },
];

export default function Layout({ demo }) {
  return (
    <div className="app">
      <div className="sky" />
      <div className="clouds" />
      <div className="grass-strip" />

      <header className="mc-header">
        <div className="logo-block">
          <span className="logo-icon">⚡</span>
          <div>
            <h1 className="logo-title">PointRush</h1>
            <p className="logo-sub">cloudito.cloud</p>
          </div>
        </div>

        <nav className="mc-nav">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.to === '/'}
              className={({ isActive }) => `mc-btn nav-btn ${isActive ? 'active' : ''}`}
            >
              <span>{l.icon}</span> {l.label}
            </NavLink>
          ))}
        </nav>
      </header>

      {demo && (
        <div className="demo-banner mc-panel">
          <span>⚠️</span> Demo modus — verbind MongoDB voor live data van de server
        </div>
      )}

      <main className="main-content">
        <Outlet />
      </main>

      <footer className="mc-footer">
        <p>PointRush Minigame Stats · Gekoppeld aan de plugin via MongoDB</p>
        <Link to="/events" className="footer-link">Bekijk alle events →</Link>
      </footer>
    </div>
  );
}
