export const EVENT_TYPES = {
  parkour: { name: 'Parkour', icon: '🏃', color: '#55FF55' },
  tnttag: { name: 'TNT Tag', icon: '💣', color: '#FF5555' },
  tntrun: { name: 'TNT Run', icon: '🔥', color: '#FFAA00' },
  race: { name: 'Race', icon: '🏎️', color: '#FF55FF' },
  boatrace: { name: 'Bootrace', icon: '⛵', color: '#55FFFF' },
  bingo: { name: 'Bingo', icon: '🎯', color: '#FFFF55' },
  koth: { name: 'King of the Hill', icon: '👑', color: '#AA00AA' },
  floorislava: { name: 'Floor is Lava', icon: '🌋', color: '#FF5555' },
  treasurehunt: { name: 'Treasure Hunt', icon: '🗺️', color: '#FFAA00' },
  goldrush: { name: 'Gold Rush', icon: '⛏️', color: '#FFAA00' },
  hiddentarget: { name: 'Hidden Target', icon: '🎯', color: '#55FF55' },
  ctf: { name: 'Capture the Flag', icon: '🚩', color: '#5555FF' },
};

export const TEAM_COLORS = {
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

export function teamColorHex(name) {
  if (!name) return '#FFFFFF';
  return TEAM_COLORS[name.toLowerCase()] ?? '#FFFFFF';
}

export function eventMeta(type) {
  return EVENT_TYPES[type] ?? { name: type, icon: '⚡', color: '#FFAA00' };
}
