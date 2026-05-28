import { Link, NavLink, Outlet } from 'react-router-dom';

const links = [
  { to: '/', label: 'Home' },
  { to: '/live', label: 'Live' },
  { to: '/teams', label: 'Teams' },
  { to: '/events', label: 'Events' },
  { to: '/players', label: 'Spelers' },
];

export default function Layout({ demo }) {
  return (
    <div className="app">
      <div className="world-bg" aria-hidden="true">
        <div className="world-bg__sky" />
        <div className="world-bg__stars" />
        <div className="world-bg__sun" />
        <div className="world-bg__mountains world-bg__mountains--far" />
        <div className="world-bg__mountains world-bg__mountains--near" />
        <div className="world-bg__terrain" />
        <div className="world-bg__vignette" />
      </div>

      <header className="site-header">
        <div className="site-header__inner">
          <Link to="/" className="brand">
            <span className="brand__icon" />
            <div>
              <span className="brand__title">PointRush</span>
              <span className="brand__sub">pointrush.coresmp.nl</span>
            </div>
          </Link>

          <nav className="site-nav">
            {links.map((l) => (
              <NavLink
                key={l.to}
                to={l.to}
                end={l.to === '/'}
                className={({ isActive }) => `site-nav__link ${isActive ? 'is-active' : ''}`}
              >
                {l.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      {demo && (
        <div className="demo-banner">
          Demo modus — verbind MongoDB voor live serverdata
        </div>
      )}

      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
