import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { EventCard, LoadingScreen, StatBlock, TeamBadge } from '../components/Ui';
import { formatPoints, playerHead, teamColor } from '../utils';

export default function HomePage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.overview().then(setData).catch((e) => setError(e.message));
  }, []);

  if (error) return <McPanel title="Fout" icon="❌">{error}</McPanel>;
  if (!data) return <LoadingScreen />;

  return (
    <div className="page home-page">
      <section className="hero mc-panel">
        <div className="hero-content">
          <p className="hero-tag">⚡ Minigame competitie</p>
          <h2 className="hero-title">Welkom bij PointRush!</h2>
          <p className="hero-text">
            Bekijk team rankings, event geschiedenis en speler stats — live vanuit de Minecraft server.
          </p>
          <div className="hero-actions">
            <Link to="/teams" className="mc-btn primary">🏆 Team Ranking</Link>
            <Link to="/events" className="mc-btn">🎮 Events</Link>
          </div>
        </div>
        <div className="hero-art">
          <div className="floating-block grass">🟩</div>
          <div className="floating-block diamond">💎</div>
          <div className="floating-block tnt">💣</div>
        </div>
      </section>

      <div className="stats-grid">
        <StatBlock icon="🎮" label="Events gespeeld" value={formatPoints(data.totalEvents)} highlight />
        <StatBlock icon="⚔️" label="Teams" value={formatPoints(data.totalTeams)} />
        <StatBlock icon="👤" label="Actieve spelers" value={formatPoints(data.totalPlayers)} />
      </div>

      {data.topTeam && (
        <McPanel title="Huidige kampioen" icon="👑" accent={teamColor(data.topTeam.color)}>
          <div className="champion-row">
            <img src={playerHead(data.topTeam.leader, 64)} alt="" className="player-head big" />
            <div>
              <TeamBadge name={data.topTeam.name} color={data.topTeam.color} />
              <p className="champion-points">{formatPoints(data.topTeam.points)} punten</p>
              <Link to="/teams" className="mc-link">Bekijk volledige ranking →</Link>
            </div>
          </div>
        </McPanel>
      )}

      <McPanel title="Recente events" icon="📜">
        <div className="event-grid">
          {(data.recentEvents ?? []).map((e) => (
            <EventCard key={e.id} event={e} />
          ))}
        </div>
        <Link to="/events" className="mc-btn block-btn">Alle events bekijken</Link>
      </McPanel>
    </div>
  );
}
