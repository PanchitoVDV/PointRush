import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { LoadingScreen } from '../components/Ui';
import LiveStreamCard from '../components/LiveStreamCard';

export default function LivePage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.live().then(setData).catch((e) => setError(e.message));
  }, []);

  if (error) {
    return (
      <McPanel title="Fout" icon="!">
        {error}
      </McPanel>
    );
  }

  if (!data) return <LoadingScreen />;

  const { streams, count } = data;

  return (
    <div className="page live-page">
      <header className="live-page__header">
        <div>
          <p className="live-page__eyebrow">CoreSMP streamers</p>
          <h1 className="live-page__title">
            Live nu
            {count > 0 && <span className="live-page__count">{count}</span>}
          </h1>
          <p className="live-page__desc">
            Streamers met <code>pointrush.streamer</code> verschijnen hier via{' '}
            <code>/live twitch|youtube|tiktok &lt;link&gt;</code>
          </p>
        </div>
      </header>

      {streams.length === 0 ? (
        <McPanel title="Niemand live" icon="◌">
          <p className="panel-desc">
            Er zijn momenteel geen actieve streams. Streamers kunnen zichzelf zichtbaar maken met{' '}
            <code>/live off</code> om te stoppen.
          </p>
        </McPanel>
      ) : (
        <div className="live-grid">
          {streams.map((stream) => (
            <LiveStreamCard key={stream.playerId} stream={stream} />
          ))}
        </div>
      )}

      <p className="live-page__hint">
        Bekijk ook de{' '}
        <Link to="/players" className="text-link">
          speler ranking
        </Link>
        .
      </p>
    </div>
  );
}
