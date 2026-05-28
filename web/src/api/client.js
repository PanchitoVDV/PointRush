const BASE = '/api';

async function fetchJson(path) {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error ?? `HTTP ${res.status}`);
  }
  return res.json();
}

export const api = {
  overview: () => fetchJson('/stats/overview'),
  teams: () => fetchJson('/teams'),
  events: (params = {}) => {
    const q = new URLSearchParams(params).toString();
    return fetchJson(`/events${q ? `?${q}` : ''}`);
  },
  event: (id) => fetchJson(`/events/${id}`),
  players: (limit = 50) => fetchJson(`/players?limit=${limit}`),
  player: (uuid) => fetchJson(`/players/${uuid}`),
  live: () => fetchJson('/live'),
  meta: () => fetchJson('/meta'),
  schedule: () => fetchJson('/schedule'),
  scheduleCalendar: (limit = 120) => fetchJson(`/schedule/calendar?limit=${limit}`),
};
