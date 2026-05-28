import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { EventCard, LoadingScreen } from '../components/Ui';

export default function EventsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filter = searchParams.get('type') ?? '';
  const [events, setEvents] = useState([]);
  const [meta, setMeta] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.meta().then((d) => setMeta(d.events ?? []));
  }, []);

  useEffect(() => {
    setLoading(true);
    const params = filter ? { type: filter, limit: 60 } : { limit: 60 };
    api.events(params)
      .then((d) => {
        setEvents(d.events ?? []);
        setTotal(d.total ?? 0);
      })
      .finally(() => setLoading(false));
  }, [filter]);

  function setFilter(type) {
    if (type) setSearchParams({ type });
    else setSearchParams({});
  }

  return (
    <div className="page events-page">
      <McPanel title="Event Geschiedenis" icon="🎮">
        <p className="panel-desc">
          Alle afgelopen minigames — {total} event{total !== 1 ? 's' : ''} totaal.
        </p>

        <div className="filter-bar">
          <button
            type="button"
            className={`mc-btn filter-btn ${!filter ? 'active' : ''}`}
            onClick={() => setFilter('')}
          >
            Alles
          </button>
          {meta.map((e) => (
            <button
              key={e.id}
              type="button"
              className={`mc-btn filter-btn ${filter === e.id ? 'active' : ''}`}
              onClick={() => setFilter(e.id)}
              style={{ '--filter-accent': e.color }}
            >
              {e.icon} {e.name}
            </button>
          ))}
        </div>

        {loading ? (
          <LoadingScreen />
        ) : events.length === 0 ? (
          <p className="empty">Geen events gevonden voor dit filter.</p>
        ) : (
          <div className="event-grid">
            {events.map((e) => (
              <EventCard key={e.id} event={e} />
            ))}
          </div>
        )}
      </McPanel>
    </div>
  );
}
