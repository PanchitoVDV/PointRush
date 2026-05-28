import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { EventCard, HeroTopPlayers, LoadingScreen, StatBlock, TeamBadge } from '../components/Ui';
import { formatPoints, playerHead, teamColor } from '../utils';

export default function HomePage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.overview().then(setData).catch((e) => setError(e.message));
  }, []);

  if (error) return <McPanel title="Fout" icon="!">{error}</McPanel>;
  if (!data) return <LoadingScreen />;

  return (
    <div className="page home-page">
      <section className="hero">
        <div className="hero__content">
          <p className="hero__eyebrow">CoreSMP Minigames</p>
          <h1 className="hero__title">PointRush Stats</h1>
          <p className="hero__text">
            Team rankings, event geschiedenis en speler stats — live vanuit de server.
          </p>
          <div className="hero__actions">
            <Link to="/teams" className="btn btn--primary">Team ranking</Link>
            <Link to="/players" className="btn btn--ghost">Speler ranking</Link>
            <Link to="/events" className="btn btn--ghost">Events</Link>
          </div>
        </div>
        <div className="hero__scene">
          <HeroTopPlayers players={data.topPlayers} />
        </div>
      </section>

      <div className="stats-grid">
        <StatBlock icon="🎮" label="Events gespeeld" value={formatPoints(data.totalEvents)} highlight />
        <StatBlock icon="⚔" label="Teams" value={formatPoints(data.totalTeams)} />
        <StatBlock icon="👤" label="Spelers" value={formatPoints(data.totalPlayers)} />
      </div>

      {data.topTeam && (
        <McPanel title="Team ranking" icon="♛" accent={teamColor(data.topTeam.color)}>
          <p className="panel-desc">Hoogst scorende team op de server.</p>
          <div className="champion-row">
            <img src={playerHead(data.topTeam.leader, 64)} alt="" className="player-head big" />
            <div>
              <TeamBadge name={data.topTeam.name} color={data.topTeam.color} />
              <p className="champion-points">{formatPoints(data.topTeam.points)} punten</p>
              <Link to="/teams" className="text-link">Volledige ranking →</Link>
            </div>
          </div>
        </McPanel>
      )}

      <McPanel title="Recente events" icon="◷">
        <div className="event-grid">
          {(data.recentEvents ?? []).map((e) => (
            <EventCard key={e.id} event={e} />
          ))}
        </div>
        <Link to="/events" className="btn btn--ghost btn--block">Alle events bekijken</Link>
      </McPanel>
    </div>
  );
}
