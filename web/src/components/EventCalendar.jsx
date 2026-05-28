import { useState } from 'react';
import { Link } from 'react-router-dom';

const WEEKDAYS = ['Ma', 'Di', 'Wo', 'Do', 'Vr', 'Za', 'Zo'];

function parseDay(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function buildMonthGrid(year, month, dayMap) {
  const first = new Date(year, month, 1);
  const startOffset = (first.getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells = [];

  for (let i = 0; i < startOffset; i++) {
    cells.push({ empty: true, key: `e-${i}` });
  }
  for (let day = 1; day <= daysInMonth; day++) {
    const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    cells.push({
      empty: false,
      key: dateStr,
      dateStr,
      day,
      events: dayMap.get(dateStr) ?? [],
    });
  }
  return cells;
}

function formatDayLabel(dateStr) {
  const d = parseDay(dateStr);
  return d.toLocaleDateString('nl-NL', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export default function EventCalendar({ days, upcomingDate }) {
  const dayMap = new Map(days.map((d) => [d.date, d.events]));
  const now = new Date();
  const [viewYear, setViewYear] = useState(now.getFullYear());
  const [viewMonth, setViewMonth] = useState(now.getMonth());

  const cells = buildMonthGrid(viewYear, viewMonth, dayMap);
  const monthLabel = new Date(viewYear, viewMonth).toLocaleDateString('nl-NL', {
    month: 'long',
    year: 'numeric',
  });

  function prevMonth() {
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear((y) => y - 1);
    } else {
      setViewMonth((m) => m - 1);
    }
  }

  function nextMonth() {
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear((y) => y + 1);
    } else {
      setViewMonth((m) => m + 1);
    }
  }

  return (
    <div className="event-calendar">
      <div className="event-calendar__header">
        <button type="button" className="btn btn--ghost btn--sm" onClick={prevMonth} aria-label="Vorige maand">
          ‹
        </button>
        <h3>{monthLabel}</h3>
        <button type="button" className="btn btn--ghost btn--sm" onClick={nextMonth} aria-label="Volgende maand">
          ›
        </button>
      </div>

      <div className="event-calendar__weekdays">
        {WEEKDAYS.map((d) => (
          <span key={d}>{d}</span>
        ))}
      </div>

      <div className="event-calendar__grid">
        {cells.map((cell) => {
          if (cell.empty) {
            return <div key={cell.key} className="event-calendar__cell event-calendar__cell--empty" />;
          }
          const hasEvents = cell.events.length > 0;
          const isUpcoming = upcomingDate === cell.dateStr;
          const isToday = cell.dateStr === now.toLocaleDateString('sv-SE');
          return (
            <div
              key={cell.key}
              className={[
                'event-calendar__cell',
                hasEvents && 'has-events',
                isUpcoming && 'is-upcoming',
                isToday && 'is-today',
              ].filter(Boolean).join(' ')}
              title={hasEvents ? cell.events.map((e) => e.displayName).join(', ') : undefined}
            >
              <span className="event-calendar__day">{cell.day}</span>
              {hasEvents && (
                <div className="event-calendar__dots">
                  {cell.events.slice(0, 3).map((e) => (
                    <span key={e.id} className="event-calendar__dot" style={{ background: e.accent }} />
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {days.length > 0 && (
        <div className="event-calendar__history">
          <h4>Afgelopen events</h4>
          <ul className="event-calendar__list">
            {days.slice(0, 14).map(({ date, events }) => (
              <li key={date}>
                <time dateTime={date}>{formatDayLabel(date)}</time>
                <div className="event-calendar__day-events">
                  {events.map((e) => (
                    <Link key={e.id} to={`/events/${e.id}`} className="event-calendar__chip">
                      <span>{e.icon}</span> {e.displayName}
                    </Link>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
