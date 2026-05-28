import { MongoClient } from 'mongodb';
import dotenv from 'dotenv';

dotenv.config();

const uri = process.env.MONGODB_URI ?? 'mongodb://localhost:27017';
const database = process.env.MONGODB_DATABASE ?? 'pointrush';

let client;
let db;
let connectFailed = false;

export async function connectDb() {
  if (db) return db;
  if (connectFailed) return null;
  try {
    client = new MongoClient(uri, {
      serverSelectionTimeoutMS: 2000,
      connectTimeoutMS: 2000,
    });
    await client.connect();
    db = client.db(database);
    return db;
  } catch {
    connectFailed = true;
    if (client) {
      await client.close().catch(() => {});
      client = null;
    }
    return null;
  }
}

export function collections() {
  return {
    teams: process.env.MONGODB_TEAMS_COLLECTION ?? 'teams',
    events: process.env.MONGODB_EVENTS_COLLECTION ?? 'events',
    playerCoins: process.env.MONGODB_PLAYER_COINS_COLLECTION ?? 'player_coins',
    liveStreams: process.env.MONGODB_LIVE_STREAMS_COLLECTION ?? 'live_streams',
    schedule: process.env.MONGODB_SCHEDULE_COLLECTION ?? 'event_schedule',
  };
}

export async function closeDb() {
  if (client) {
    await client.close();
    client = null;
    db = null;
  }
}
