import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { EventCard, LoadingScreen, PlayerAvatar, StatBlock } from '../components/Ui';
import { formatPoints, rankMedal } from '../utils';

export default function PlayerDetailPage() {
  const { uuid } = useParams();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.player(uuid)
      .then(setData)
      .catch((e) => setError(e.message));
  }, [uuid]);

  if (error) {
    return (
      <McPanel title="Niet gevonden" icon="❌">
        <p>{error}</p>
        <Link to="/players" className="mc-btn">← Terug</Link>
      </McPanel>
    );
  }
  if (!data) return <LoadingScreen />;

  const { player, recentEvents, coins } = data;
  const coinTotal = coins
    ? Object.values(coins).reduce((a, b) => a + b, 0)
    : null;

  return (
    <div className="page player-detail-page">
      <Link to="/players" className="back-link">← Alle spelers</Link>

      <section className="player-hero mc-panel">
        <PlayerAvatar uuid={player.playerId} name={player.playerName} size={80} />
        <div>
          <h2>{player.playerName}</h2>
          <code className="uuid-tag">{player.playerId}</code>
        </div>
      </section>

      <div className="stats-grid">
        <StatBlock icon="🎮" label="Events" value={player.eventsPlayed} highlight />
        <StatBlock icon="🥇" label="Wins" value={player.wins} />
        <StatBlock icon="🏅" label="Podiums" value={player.podiums} />
        <StatBlock icon="⚡" label="Totaal punten" value={`+${formatPoints(player.totalPoints)}`} />
        {player.bestRank && (
          <StatBlock icon="📈" label="Beste rank" value={rankMedal(player.bestRank)} />
        )}
        {coinTotal !== null && (
          <StatBlock icon="🪙" label="Coins verzameld" value={formatPoints(coinTotal)} />
        )}
      </div>

      {player.eventTypes?.length > 0 && (
        <McPanel title="Gespeelde events" icon="🎯">
          <div className="tag-list">
            {player.eventTypes.map((t) => (
              <Link key={t} to={`/events?type=${t}`} className="mc-tag">
                {t}
              </Link>
            ))}
          </div>
        </McPanel>
      )}

      {recentEvents?.length > 0 && (
        <McPanel title="Recente events" icon="📜">
          <div className="event-grid compact">
            {recentEvents.map((e) => (
              <EventCard key={e.id} event={e} />
            ))}
          </div>
        </McPanel>
      )}
    </div>
  );
}
