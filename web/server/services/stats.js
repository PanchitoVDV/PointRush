import { eventMeta } from '../constants.js';

export function aggregatePlayerStats(events) {
  const players = new Map();

  for (const event of events) {
    for (const p of event.placements ?? []) {
      if (!p.playerId) continue;
      const key = p.playerId;
      let stats = players.get(key);
      if (!stats) {
        stats = {
          playerId: key,
          playerName: p.playerName ?? key.slice(0, 8),
          eventsPlayed: 0,
          wins: 0,
          podiums: 0,
          totalPoints: 0,
          bestRank: Infinity,
          eventTypes: new Set(),
        };
        players.set(key, stats);
      }
      if (p.playerName && p.playerName !== '?') {
        stats.playerName = p.playerName;
      }
      stats.eventsPlayed += 1;
      stats.totalPoints += p.score ?? 0;
      stats.eventTypes.add(event.type);
      if (p.rank === 1) stats.wins += 1;
      if (p.rank <= 3) stats.podiums += 1;
      if (p.rank > 0 && p.rank < stats.bestRank) stats.bestRank = p.rank;
    }
  }

  return [...players.values()]
    .map((s) => ({
      ...s,
      eventTypes: [...s.eventTypes],
      bestRank: s.bestRank === Infinity ? null : s.bestRank,
    }))
    .sort((a, b) => b.totalPoints - a.totalPoints || b.wins - a.wins);
}

export function formatEvent(doc) {
  const meta = eventMeta(doc.type);
  return {
    id: doc._id,
    type: doc.type,
    displayName: meta.name,
    icon: meta.icon,
    accent: meta.color,
    startedAt: doc.started ?? 0,
    endedAt: doc.ended ?? doc.started ?? 0,
    placements: (doc.placements ?? []).map((p) => ({
      rank: p.rank ?? 0,
      playerId: p.playerId ?? null,
      playerName: p.playerName ?? '?',
      teamId: p.teamId ?? null,
      teamName: p.teamName ?? null,
      teamColor: p.teamColor ?? null,
      score: p.score ?? 0,
      detail: p.detail ?? null,
    })),
  };
}

export function formatTeam(doc) {
  return {
    id: doc._id,
    name: doc.name,
    leader: doc.leader,
    color: doc.color ?? 'white',
    points: doc.points ?? 0,
    members: doc.members ?? [],
    memberCount: (doc.members ?? []).length,
  };
}

export function demoData() {
  const now = Date.now();
  return {
    teams: [
      { id: '1', name: 'Creepers', leader: '00000000-0000-0000-0000-000000000001', color: 'green', points: 420, members: ['00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002'], memberCount: 2 },
      { id: '2', name: 'Diamonds', leader: '00000000-0000-0000-0000-000000000003', color: 'aqua', points: 380, members: ['00000000-0000-0000-0000-000000000003'], memberCount: 1 },
      { id: '3', name: 'Netherites', leader: '00000000-0000-0000-0000-000000000004', color: 'dark_gray', points: 290, members: ['00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000006'], memberCount: 3 },
    ],
    events: [
      {
        id: 'demo-parkour-1',
        type: 'parkour',
        displayName: 'Parkour',
        icon: '🏃',
        accent: '#55FF55',
        startedAt: now - 7200000,
        endedAt: now - 5400000,
        placements: [
          { rank: 1, playerId: '00000000-0000-0000-0000-000000000001', playerName: 'Steve', teamId: '1', teamName: 'Creepers', teamColor: 'green', score: 100, detail: null },
          { rank: 2, playerId: '00000000-0000-0000-0000-000000000003', playerName: 'Alex', teamId: '2', teamName: 'Diamonds', teamColor: 'aqua', score: 80, detail: null },
        ],
      },
      {
        id: 'demo-tnttag-1',
        type: 'tnttag',
        displayName: 'TNT Tag',
        icon: '💣',
        accent: '#FF5555',
        startedAt: now - 86400000,
        endedAt: now - 82800000,
        placements: [
          { rank: 1, playerId: '00000000-0000-0000-0000-000000000004', playerName: 'Notch', teamId: '3', teamName: 'Netherites', teamColor: 'dark_gray', score: 120, detail: 'ronde 8 · 5 overleefd' },
        ],
      },
    ],
    players: [
      { playerId: '00000000-0000-0000-0000-000000000001', playerName: 'Steve', eventsPlayed: 12, wins: 4, podiums: 8, totalPoints: 680, bestRank: 1, eventTypes: ['parkour', 'race'] },
      { playerId: '00000000-0000-0000-0000-000000000003', playerName: 'Alex', eventsPlayed: 10, wins: 3, podiums: 6, totalPoints: 520, bestRank: 1, eventTypes: ['parkour', 'tnttag'] },
    ],
  };
}
