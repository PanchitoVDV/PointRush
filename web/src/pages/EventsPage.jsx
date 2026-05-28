import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { EventListRow, LoadingScreen } from '../components/Ui';

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
      <McPanel title="Event geschiedenis">
        <div className="events-toolbar">
          <p className="events-count">
            {total} event{total !== 1 ? 's' : ''}
            {filter ? ` · ${meta.find((m) => m.id === filter)?.name ?? filter}` : ''}
          </p>
          <label className="events-filter">
            <span>Filter</span>
            <select
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            >
              <option value="">Alle minigames</option>
              {meta.map((e) => (
                <option key={e.id} value={e.id}>{e.name}</option>
              ))}
            </select>
          </label>
        </div>

        {loading ? (
          <LoadingScreen />
        ) : events.length === 0 ? (
          <p className="empty">Geen events gevonden.</p>
        ) : (
          <div className="event-list">
            <div className="event-list-head">
              <span>Event</span>
              <span>Top spelers</span>
              <span>Punten</span>
            </div>
            {events.map((e) => (
              <EventListRow key={e.id} event={e} />
            ))}
          </div>
        )}
      </McPanel>
    </div>
  );
}
