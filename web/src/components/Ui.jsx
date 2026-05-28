import { Link } from 'react-router-dom';
import { playerHead, playerBody, teamColor, formatDate, rankMedal } from '../utils';

export function PlayerAvatar({ uuid, name, size = 40 }) {
  return (
    <img
      className="player-head"
      src={playerHead(uuid, size)}
      alt={name ?? 'Speler'}
      width={size}
      height={size}
      loading="lazy"
    />
  );
}

export function TeamBadge({ name, color }) {
  const hex = teamColor(color);
  return (
    <span className="team-badge" style={{ color: hex, borderColor: hex, textShadow: `0 0 8px ${hex}55` }}>
      {name}
    </span>
  );
}

export function PlacementRow({ p, linkPlayer = true }) {
  const content = (
    <>
      <span className="placement-rank">{rankMedal(p.rank)}</span>
      <PlayerAvatar uuid={p.playerId} name={p.playerName} size={36} />
      <div className="placement-info">
        <strong>{p.playerName}</strong>
        {p.teamName && <TeamBadge name={p.teamName} color={p.teamColor} />}
        {p.detail && <small className="detail">{p.detail}</small>}
      </div>
      <span className="placement-score">+{p.score} pts</span>
    </>
  );

  if (linkPlayer && p.playerId) {
    return (
      <Link to={`/players/${p.playerId}`} className="placement-row link-row">
        {content}
      </Link>
    );
  }
  return <div className="placement-row">{content}</div>;
}

export function EventCard({ event }) {
  return (
    <Link to={`/events/${event.id}`} className="event-card mc-panel">
      <div className="event-card-top" style={{ borderColor: event.accent }}>
        <span className="event-icon">{event.icon}</span>
        <div>
          <h3>{event.displayName}</h3>
          <time>{formatDate(event.endedAt)}</time>
        </div>
      </div>
      <div className="event-card-winners">
        {(event.placements ?? []).slice(0, 3).map((p) => (
          <span key={p.rank} title={p.playerName}>
            {rankMedal(p.rank)} {p.playerName}
          </span>
        ))}
      </div>
    </Link>
  );
}

/** Compact row for the events overview page */
export function EventListRow({ event }) {
  const winner = event.placements?.[0];
  const top3 = (event.placements ?? []).slice(0, 3);

  return (
    <Link to={`/events/${event.id}`} className="event-list-row">
      <span className="event-list-row__dot" style={{ background: event.accent }} />
      <div className="event-list-row__main">
        <span className="event-list-row__name">{event.displayName}</span>
        <time className="event-list-row__date">{formatDate(event.endedAt)}</time>
      </div>
      <div className="event-list-row__result">
        {top3.length > 0 ? (
          top3.map((p) => (
            <span key={p.rank} className="event-list-row__player">
              {p.rank === 1 ? p.playerName : `${p.rank}. ${p.playerName}`}
            </span>
          ))
        ) : (
          <span className="event-list-row__player event-list-row__player--muted">—</span>
        )}
      </div>
      {winner && (
        <span className="event-list-row__pts">+{winner.score}</span>
      )}
      <span className="event-list-row__arrow">→</span>
    </Link>
  );
}

export function HeroTopPlayers({ players }) {
  const top = (players ?? []).slice(0, 3);
  if (top.length === 0) return null;

  // Podium order: #2, #1, #3
  const ordered = [
    top[1] ? { ...top[1], rank: 2 } : null,
    top[0] ? { ...top[0], rank: 1 } : null,
    top[2] ? { ...top[2], rank: 3 } : null,
  ].filter(Boolean);

  return (
    <div className="hero-podium">
      {ordered.map((p) => (
        <Link
          key={p.playerId}
          to={`/players/${p.playerId}`}
          className={`hero-player hero-player--${p.rank}`}
          title={p.playerName}
        >
          <span className="hero-player__rank">{rankMedal(p.rank)}</span>
          <img
            className="hero-player__body"
            src={playerBody(p.playerId)}
            alt={p.playerName}
            loading="lazy"
          />
          <span className="hero-player__name">{p.playerName}</span>
          <span className="hero-player__pts">+{p.totalPoints} pts</span>
        </Link>
      ))}
    </div>
  );
}

export function StatBlock({ icon, label, value, highlight }) {
  return (
    <div className={`stat-block ${highlight ? 'highlight' : ''}`}>
      <span className="stat-icon">{icon}</span>
      <div>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

export function LoadingScreen() {
  return (
    <div className="loading-screen">
      <div className="loading-block" />
      <p>Laden...</p>
    </div>
  );
}
