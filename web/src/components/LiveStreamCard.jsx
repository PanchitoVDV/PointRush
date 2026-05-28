import { Link } from 'react-router-dom';
import { formatDate, playerBody } from '../utils';

const PLATFORM_ICONS = {
  twitch: 'T',
  youtube: '▶',
  tiktok: '♪',
};

export default function LiveStreamCard({ stream, compact = false }) {
  const {
    playerId,
    playerName,
    platform,
    platformName,
    platformColor,
    url,
    startedAt,
    embed,
  } = stream;

  return (
    <article
      className={`live-card ${compact ? 'live-card--compact' : ''}`}
      style={{ '--platform-color': platformColor }}
    >
      <header className="live-card__header">
        <Link to={`/players/${playerId}`} className="live-card__avatar" title={`Profiel ${playerName}`}>
          <img src={playerBody(playerId)} alt="" loading="lazy" />
        </Link>
        <div className="live-card__meta">
          <div className="live-card__top">
            <Link to={`/players/${playerId}`} className="live-card__name">
              {playerName}
            </Link>
            <span className="live-badge" aria-label="Live">
              <span className="live-badge__dot" />
              LIVE
            </span>
          </div>
          <div className="live-card__sub">
            <span className="live-card__platform">
              <span className="live-card__platform-icon">{PLATFORM_ICONS[platform] ?? '●'}</span>
              {platformName}
            </span>
            {startedAt > 0 && (
              <span className="live-card__since">sinds {formatDate(startedAt)}</span>
            )}
          </div>
        </div>
      </header>

      <div className="live-card__player">
        {embed?.type === 'iframe' && embed.src ? (
          <iframe
            title={`${playerName} op ${platformName}`}
            src={embed.src}
            allowFullScreen
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            loading="lazy"
          />
        ) : (
          <div className="live-card__fallback">
            <img src={playerBody(playerId)} alt="" className="live-card__fallback-skin" />
            <p>Open de live stream in de {platformName} app.</p>
            <a href={url} target="_blank" rel="noopener noreferrer" className="btn btn--primary">
              Bekijk op {platformName}
            </a>
          </div>
        )}
      </div>

      <footer className="live-card__footer">
        <a href={url} target="_blank" rel="noopener noreferrer" className="text-link">
          Open op {platformName} →
        </a>
      </footer>
    </article>
  );
}
