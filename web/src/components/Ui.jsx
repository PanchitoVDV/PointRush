import { Link } from 'react-router-dom';
import { playerHead, playerBody, teamColor, formatDate, formatPoints, rankMedal } from '../utils';

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
    <span className="team-badge" style={{ color: hex, borderColor: hex }}>
      {name}
    </span>
  );
}

export function TeamPodium({ teams, maxPoints }) {
  if (!teams?.length) return null;

  const ordered = [
    teams[1] ? { team: teams[1], rank: 2 } : null,
    teams[0] ? { team: teams[0], rank: 1 } : null,
    teams[2] ? { team: teams[2], rank: 3 } : null,
  ].filter(Boolean);

  const pct = (pts) => Math.max(8, Math.round((pts / (maxPoints || 1)) * 100));

  return (
    <section className="team-podium">
      {ordered.map(({ team, rank }, i) => {
        const hex = teamColor(team.color);
        return (
          <div
            key={team.id}
            className={`team-podium__slot team-podium__slot--${rank}`}
            style={{
              '--team-color': hex,
              '--bar-pct': `${pct(team.points)}%`,
              '--anim-delay': `${i * 0.12}s`,
            }}
          >
            <span className="team-podium__rank">{rankMedal(rank)}</span>
            <div className="team-podium__avatars">
              {(team.members ?? []).slice(0, 4).map((uuid) => (
                <Link key={uuid} to={`/players/${uuid}`}>
                  <PlayerAvatar uuid={uuid} size={rank === 1 ? 36 : 30} />
                </Link>
              ))}
            </div>
            <TeamBadge name={team.name} color={team.color} />
            <span className="team-podium__pts">{formatPoints(team.points)}</span>
            <div className="team-podium__bar">
              <div className="team-podium__bar-fill" />
            </div>
          </div>
        );
      })}
    </section>
  );
}

export function TeamRankRow({ team, rank, maxPoints }) {
  const hex = teamColor(team.color);
  const pct = Math.max(4, Math.round((team.points / (maxPoints || 1)) * 100));
  const isTop = rank <= 3;

  return (
    <div
      className={`team-row ${isTop ? 'team-row--top' : ''}`}
      style={{ '--team-color': hex, '--bar-pct': `${pct}%`, '--anim-delay': `${Math.min(rank * 0.05, 0.5)}s` }}
    >
      <span className="team-row__rank">{isTop ? rankMedal(rank) : rank}</span>
      <div className="team-row__info">
        <div className="team-row__head">
          <TeamBadge name={team.name} color={team.color} />
          <span className="team-row__members">{team.memberCount} speler{team.memberCount !== 1 ? 's' : ''}</span>
        </div>
        <div className="team-row__bar">
          <div className="team-row__bar-fill" />
        </div>
      </div>
      <div className="team-row__heads">
        {(team.members ?? []).slice(0, 4).map((uuid) => (
          <Link key={uuid} to={`/players/${uuid}`} title="Speler profiel">
            <PlayerAvatar uuid={uuid} size={28} />
          </Link>
        ))}
      </div>
      <span className="team-row__pts">{formatPoints(team.points)}</span>
    </div>
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
    <div className="hero-ranking">
      <div className="hero-ranking__head">
        <span className="hero-ranking__label">Speler ranking</span>
        <Link to="/players" className="hero-ranking__link">Volledig →</Link>
      </div>
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
