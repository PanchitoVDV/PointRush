import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import LiveStreamCard from './LiveStreamCard';

export default function LiveNowBanner() {
  const [streams, setStreams] = useState([]);

  useEffect(() => {
    api.live().then((d) => setStreams(d.streams ?? [])).catch(() => {});
  }, []);

  if (!streams.length) return null;

  return (
    <section className="live-now">
      <div className="live-now__head">
        <div>
          <span className="live-badge live-badge--large">
            <span className="live-badge__dot" />
            LIVE
          </span>
          <h2 className="live-now__title">
            {streams.length} streamer{streams.length !== 1 ? 's' : ''} online
          </h2>
        </div>
        <Link to="/live" className="btn btn--ghost">
          Alle streams →
        </Link>
      </div>
      <div className="live-now__grid">
        {streams.slice(0, 2).map((stream) => (
          <LiveStreamCard key={stream.playerId} stream={stream} compact />
        ))}
      </div>
    </section>
  );
}
