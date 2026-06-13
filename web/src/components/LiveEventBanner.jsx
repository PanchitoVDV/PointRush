import { useEffect, useState } from 'react';
import { api } from '../api/client';

/**
 * Toont een strook "Nu live: <minigame>" wanneer er een PointRush-event loopt (gedeeld live_event
 * doc). Pollt elke 12s en rendert niets wanneer er geen event is.
 */
export default function LiveEventBanner() {
  const [live, setLive] = useState(null);

  useEffect(() => {
    let active = true;
    const load = () =>
      api
        .liveEvent()
        .then((d) => {
          if (active) setLive(d.live ?? null);
        })
        .catch(() => {});
    load();
    const timer = setInterval(load, 12000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  if (!live) return null;

  const status = live.phase === 'running' ? 'bezig' : 'start zo';

  return (
    <div className="live-event-banner" style={{ '--event-accent': live.accent }}>
      <span className="live-badge live-badge--large">
        <span className="live-badge__dot" />
        LIVE
      </span>
      <span className="live-event-banner__icon" aria-hidden="true">
        {live.icon}
      </span>
      <span className="live-event-banner__text">
        Nu live: <strong>{live.name}</strong>
        <span className="live-event-banner__status">· {status}</span>
      </span>
    </div>
  );
}
