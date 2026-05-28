import { Router } from 'express';
import { connectDb, collections } from '../db.js';
import { EVENT_TYPES } from '../constants.js';
import {
  aggregatePlayerStats,
  demoData,
  formatEvent,
  formatTeam,
} from '../services/stats.js';

const router = Router();

let useDemo = false;

async function getDb() {
  return connectDb();
}

router.get('/meta', async (_req, res) => {
  const db = await getDb();
  res.json({
    events: Object.entries(EVENT_TYPES).map(([id, meta]) => ({ id, ...meta })),
    demo: !db || useDemo,
  });
});

router.get('/teams', async (_req, res) => {
  const db = await getDb();
  if (!db) {
    return res.json({ teams: demoData().teams, demo: true });
  }
  try {
    const docs = await db
      .collection(collections().teams)
      .find({})
      .sort({ points: -1 })
      .toArray();
    res.json({ teams: docs.map(formatTeam), demo: false });
  } catch {
    res.json({ teams: demoData().teams, demo: true });
  }
});

router.get('/events', async (req, res) => {
  const type = req.query.type?.toString().toLowerCase();
  const limit = Math.min(parseInt(req.query.limit ?? '50', 10) || 50, 200);
  const skip = Math.max(parseInt(req.query.skip ?? '0', 10) || 0, 0);

  const db = await getDb();
  if (!db) {
    let events = demoData().events;
    if (type) events = events.filter((e) => e.type === type);
    return res.json({ events, total: events.length, demo: true });
  }

  try {
    const filter = type ? { type } : {};
    const col = db.collection(collections().events);
    const [docs, total] = await Promise.all([
      col.find(filter).sort({ ended: -1 }).skip(skip).limit(limit).toArray(),
      col.countDocuments(filter),
    ]);
    res.json({ events: docs.map(formatEvent), total, demo: false });
  } catch {
    res.json({ events: demoData().events, total: 2, demo: true });
  }
});

router.get('/events/:id', async (req, res) => {
  const db = await getDb();
  if (!db) {
    const event = demoData().events.find((e) => e.id === req.params.id);
    return event ? res.json({ event, demo: true }) : res.status(404).json({ error: 'Event niet gevonden' });
  }

  try {
    const doc = await db.collection(collections().events).findOne({ _id: req.params.id });
    if (!doc) return res.status(404).json({ error: 'Event niet gevonden' });
    res.json({ event: formatEvent(doc), demo: false });
  } catch {
    res.status(500).json({ error: 'Kon event niet laden' });
  }
});

router.get('/players', async (req, res) => {
  const limit = Math.min(parseInt(req.query.limit ?? '50', 10) || 50, 200);
  const db = await getDb();

  if (!db) {
    return res.json({ players: demoData().players.slice(0, limit), demo: true });
  }

  try {
    const docs = await db
      .collection(collections().events)
      .find({})
      .sort({ ended: -1 })
      .limit(500)
      .toArray();
    const events = docs.map(formatEvent);
    const players = aggregatePlayerStats(events).slice(0, limit);
    res.json({ players, demo: false });
  } catch {
    res.json({ players: demoData().players, demo: true });
  }
});

router.get('/players/:uuid', async (req, res) => {
  const uuid = req.params.uuid.toLowerCase();
  const db = await getDb();

  if (!db) {
    const player = demoData().players.find((p) => p.playerId.includes(uuid.slice(0, 8)));
    const events = demoData().events.filter((e) =>
      e.placements.some((p) => p.playerId?.includes(uuid.slice(0, 8)))
    );
    return player
      ? res.json({ player, recentEvents: events, demo: true })
      : res.status(404).json({ error: 'Speler niet gevonden' });
  }

  try {
    const docs = await db
      .collection(collections().events)
      .find({ 'placements.playerId': uuid })
      .sort({ ended: -1 })
      .limit(100)
      .toArray();
    const events = docs.map(formatEvent);
    const all = aggregatePlayerStats(events);
    const player = all.find((p) => p.playerId.toLowerCase() === uuid);
    if (!player) return res.status(404).json({ error: 'Speler niet gevonden' });

    let coins = null;
    try {
      const coinDoc = await db.collection(collections().playerCoins).findOne({ _id: uuid });
      if (coinDoc?.coins) {
        coins = coinDoc.coins;
      }
    } catch {
      /* optional */
    }

    res.json({ player, recentEvents: events.slice(0, 20), coins, demo: false });
  } catch {
    res.status(500).json({ error: 'Kon speler niet laden' });
  }
});

router.get('/stats/overview', async (_req, res) => {
  const db = await getDb();
  if (!db) {
    const demo = demoData();
    return res.json({
      totalEvents: demo.events.length,
      totalTeams: demo.teams.length,
      totalPlayers: demo.players.length,
      topTeam: demo.teams[0],
      recentEvents: demo.events,
      demo: true,
    });
  }

  try {
    const cols = collections();
    const [totalEvents, totalTeams, recentDocs, topTeamDoc] = await Promise.all([
      db.collection(cols.events).countDocuments(),
      db.collection(cols.teams).countDocuments(),
      db.collection(cols.events).find({}).sort({ ended: -1 }).limit(6).toArray(),
      db.collection(cols.teams).find({}).sort({ points: -1 }).limit(1).next(),
    ]);
    const recentEvents = recentDocs.map(formatEvent);
    const players = aggregatePlayerStats(recentEvents);

    res.json({
      totalEvents,
      totalTeams,
      totalPlayers: players.length,
      topTeam: topTeamDoc ? formatTeam(topTeamDoc) : null,
      recentEvents,
      demo: false,
    });
  } catch {
    const demo = demoData();
    res.json({
      totalEvents: demo.events.length,
      totalTeams: demo.teams.length,
      totalPlayers: demo.players.length,
      topTeam: demo.teams[0],
      recentEvents: demo.events,
      demo: true,
    });
  }
});

export default router;
