const TEAM_COLORS = {
  white: '#FFFFFF',
  yellow: '#FFFF55',
  gold: '#FFAA00',
  orange: '#FFAA00',
  red: '#FF5555',
  green: '#55FF55',
  aqua: '#55FFFF',
  blue: '#5555FF',
  light_purple: '#FF55FF',
  dark_purple: '#AA00AA',
  gray: '#AAAAAA',
  dark_gray: '#555555',
  black: '#000000',
};

export function teamColor(name) {
  if (!name) return '#FFFFFF';
  return TEAM_COLORS[name.toLowerCase()] ?? '#FFFFFF';
}

/** Softer accent for borders/bars on dark UI (avoids harsh white lines). */
export function teamAccent(name) {
  const key = (name ?? 'white').toLowerCase();
  const soft = {
    white: '#A8B8CC',
    yellow: '#C9B84A',
    gold: '#C9922E',
    gray: '#7A8494',
    aqua: '#3DAFB8',
    red: '#D85555',
    green: '#45B845',
    blue: '#5588DD',
    light_purple: '#CC66CC',
    dark_purple: '#9955AA',
    dark_gray: '#5A6270',
    black: '#4A5060',
  };
  return soft[key] ?? teamColor(name);
}

export function playerHead(uuid) {
  if (!uuid) return 'https://skins.mcstats.com/bust/MHF_Steve';
  return `https://skins.mcstats.com/bust/${uuid}`;
}

export function playerBody(uuid) {
  if (!uuid) return 'https://skins.mcstats.com/body/front/MHF_Steve';
  return `https://skins.mcstats.com/body/front/${uuid}`;
}

export function formatDate(ms) {
  if (!ms) return '—';
  return new Date(ms).toLocaleString('nl-NL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDuration(start, end) {
  const sec = Math.max(0, Math.floor((end - start) / 1000));
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

export function rankMedal(rank) {
  if (rank === 1) return '🥇';
  if (rank === 2) return '🥈';
  if (rank === 3) return '🥉';
  return `#${rank}`;
}

export function formatPoints(n) {
  return new Intl.NumberFormat('nl-NL').format(n ?? 0);
}
