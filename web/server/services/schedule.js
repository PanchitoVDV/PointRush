import { eventMeta } from '../constants.js';

export function formatSchedule(doc) {
  if (!doc) {
    return demoSchedule();
  }

  const pool = doc.pool ?? [];
  const upcomingRaw = doc.upcoming;
  const spinRaw = doc.spin;

  let upcoming = null;
  if (upcomingRaw?.eventId && upcomingRaw.status === 'SCHEDULED') {
    const meta = eventMeta(upcomingRaw.eventId);
    upcoming = {
      eventId: upcomingRaw.eventId,
      displayName: upcomingRaw.displayName ?? meta.name,
      icon: meta.icon,
      accent: meta.color,
      scheduledFor: upcomingRaw.scheduledFor,
      selectedAt: upcomingRaw.selectedAt ?? 0,
      status: upcomingRaw.status,
    };
  }

  let spin = null;
  if (spinRaw?.sequence?.length) {
    spin = {
      active: !!spinRaw.active,
      startedAt: spinRaw.startedAt ?? 0,
      candidates: (spinRaw.candidateIds ?? []).map((id, i) => {
        const meta = eventMeta(id);
        return {
          id,
          name: spinRaw.candidateNames?.[i] ?? meta.name,
          icon: meta.icon,
          accent: meta.color,
        };
      }),
      sequence: spinRaw.sequence ?? [],
      winnerId: spinRaw.winnerId ?? null,
    };
  }

  return {
    pool: pool.map((id) => {
      const meta = eventMeta(id);
      return { id, name: meta.name, icon: meta.icon, accent: meta.color };
    }),
    upcoming,
    spin,
  };
}

export function demoSchedule() {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const scheduledFor = tomorrow.toISOString().slice(0, 10);

  return {
    pool: [
      { id: 'parkour', name: 'Parkour', icon: '🏃', accent: '#55FF55' },
      { id: 'tnttag', name: 'TNT Tag', icon: '💣', accent: '#FF5555' },
      { id: 'race', name: 'Race', icon: '🏎️', accent: '#FF55FF' },
      { id: 'koth', name: 'King of the Hill', icon: '👑', accent: '#AA00AA' },
    ],
    upcoming: {
      eventId: 'koth',
      displayName: 'King of the Hill',
      icon: '👑',
      accent: '#AA00AA',
      scheduledFor,
      selectedAt: Date.now() - 3600000,
      status: 'SCHEDULED',
    },
    spin: null,
  };
}

/**
 * Groepeert events per kalenderdag (Europe/Amsterdam).
 */
export function groupEventsByDay(events) {
  const byDay = new Map();
  for (const event of events) {
    const ts = event.endedAt || event.startedAt;
    if (!ts) continue;
    const day = new Date(ts).toLocaleDateString('sv-SE', { timeZone: 'Europe/Amsterdam' });
    if (!byDay.has(day)) {
      byDay.set(day, []);
    }
    byDay.get(day).push(event);
  }
  return [...byDay.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([date, dayEvents]) => ({ date, events: dayEvents }));
}
