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

export function playerHead(uuid, size = 32) {
  if (!uuid) return `https://minotar.net/avatar/Steve/${size}.png`;
  return `https://minotar.net/avatar/${uuid}/${size}.png`;
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
