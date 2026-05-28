import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { LoadingScreen, PlayerAvatar } from '../components/Ui';
import { formatPoints, rankMedal } from '../utils';

export default function PlayersPage() {
  const [players, setPlayers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.players(100)
      .then((d) => setPlayers(d.players ?? []))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingScreen />;

  return (
    <div className="page players-page">
      <McPanel title="Speler ranking" icon="👤">
        <p className="panel-desc">
          Statistieken per speler — wins, podiums en totale event punten.
        </p>

        {players.length === 0 ? (
          <p className="empty">Nog geen speler data beschikbaar.</p>
        ) : (
          <div className="player-table">
            <div className="player-table-head">
              <span>#</span>
              <span>Speler</span>
              <span>Events</span>
              <span>Wins</span>
              <span>Podiums</span>
              <span>Punten</span>
            </div>
            {players.map((p, i) => (
              <Link key={p.playerId} to={`/players/${p.playerId}`} className="player-table-row">
                <span className="pt-rank">{i < 3 ? rankMedal(i + 1) : i + 1}</span>
                <span className="pt-player">
                  <PlayerAvatar uuid={p.playerId} name={p.playerName} size={32} />
                  <strong>{p.playerName}</strong>
                </span>
                <span>{p.eventsPlayed}</span>
                <span className="gold-text">{p.wins}</span>
                <span>{p.podiums}</span>
                <span className="points-text">+{formatPoints(p.totalPoints)}</span>
              </Link>
            ))}
          </div>
        )}
      </McPanel>
    </div>
  );
}
