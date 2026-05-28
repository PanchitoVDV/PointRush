import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { LoadingScreen, PlacementRow } from '../components/Ui';
import { formatDate, formatDuration } from '../utils';

export default function EventDetailPage() {
  const { id } = useParams();
  const [event, setEvent] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.event(id)
      .then((d) => setEvent(d.event))
      .catch((e) => setError(e.message));
  }, [id]);

  if (error) {
    return (
      <McPanel title="Niet gevonden" icon="❌">
        <p>{error}</p>
        <Link to="/events" className="mc-btn">← Terug</Link>
      </McPanel>
    );
  }
  if (!event) return <LoadingScreen />;

  return (
    <div className="page event-detail-page">
      <Link to="/events" className="back-link">← Alle events</Link>

      <McPanel
        title={event.displayName}
        icon={event.icon}
        accent={event.accent}
        className="event-detail-panel"
      >
        <div className="event-meta-row">
          <span>📅 {formatDate(event.startedAt)}</span>
          <span>⏱️ {formatDuration(event.startedAt, event.endedAt)}</span>
          <span>👥 {(event.placements ?? []).length} spelers</span>
        </div>

        <h3 className="section-title">🏅 Standings</h3>
        <div className="placements-list">
          {(event.placements ?? []).map((p) => (
            <PlacementRow key={`${p.rank}-${p.playerId}`} p={p} />
          ))}
        </div>
      </McPanel>
    </div>
  );
}
