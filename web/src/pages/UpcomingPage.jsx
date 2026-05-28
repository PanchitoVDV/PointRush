import { useEffect, useState } from 'react';
import { api } from '../api/client';
import EventCalendar from '../components/EventCalendar';
import EventWheel from '../components/EventWheel';
import McPanel from '../components/McPanel';
import { LoadingScreen } from '../components/Ui';

function formatScheduledDate(dateStr) {
  if (!dateStr) return '—';
  const [y, m, d] = dateStr.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('nl-NL', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

export default function UpcomingPage() {
  const [schedule, setSchedule] = useState(null);
  const [calendarDays, setCalendarDays] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [sched, cal] = await Promise.all([
          api.schedule(),
          api.scheduleCalendar(),
        ]);
        if (!cancelled) {
          setSchedule(sched);
          setCalendarDays(cal.days ?? []);
        }
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
    }

    load();
    const poll = setInterval(load, 2000);
    return () => {
      cancelled = true;
      clearInterval(poll);
    };
  }, []);

  if (error) {
    return (
      <McPanel title="Fout" icon="!">{error}</McPanel>
    );
  }

  if (!schedule) return <LoadingScreen />;

  const { upcoming, spin, pool } = schedule;

  return (
    <div className="page upcoming-page">
      <section className="upcoming-hero">
        <p className="hero__eyebrow">Event planning</p>
        <h1 className="hero__title">Upcoming events</h1>
        <p className="hero__text">
          Het rad wordt in-game gedraaid met <code>/randomevent</code>.
          Het gekozen event start de volgende dag met <code>/event start</code>.
        </p>
      </section>

      <div className="upcoming-grid">
        <McPanel title="Event rad" icon="◎" accent={upcoming?.accent}>
          <EventWheel spin={spin} upcoming={upcoming} pool={pool ?? []} />
          {upcoming && (
            <div className="upcoming-banner">
              <span className="upcoming-banner__icon">{upcoming.icon}</span>
              <div>
                <p className="upcoming-banner__label">Volgende event</p>
                <p className="upcoming-banner__title">{upcoming.displayName}</p>
                <p className="upcoming-banner__date">{formatScheduledDate(upcoming.scheduledFor)}</p>
              </div>
            </div>
          )}
        </McPanel>

        <McPanel title="Event pool" icon="◈">
          <p className="panel-desc">
            Minigames die nog gekozen kunnen worden. Na <code>/event start</code> wordt het event uit de pool gehaald.
          </p>
          {pool?.length ? (
            <ul className="pool-list">
              {pool.map((item) => (
                <li key={item.id} style={{ '--pool-accent': item.accent }}>
                  <span>{item.icon}</span>
                  <span>{item.name}</span>
                  {upcoming?.eventId === item.id && (
                    <span className="pool-list__tag">gepland</span>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <p className="empty">Pool is leeg — wordt automatisch opnieuw gevuld.</p>
          )}
        </McPanel>
      </div>

      <McPanel title="Event kalender" icon="▦">
        <p className="panel-desc">
          Overzicht van wanneer events zijn gespeeld. Geplande dagen zijn gemarkeerd.
        </p>
        <EventCalendar days={calendarDays} upcomingDate={upcoming?.scheduledFor} />
      </McPanel>
    </div>
  );
}
