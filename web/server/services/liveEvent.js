import { eventMeta } from '../constants.js';

/**
 * Formatteert het gedeelde `live_event` document tot wat de website toont, of `null` als er geen
 * event loopt. Doc-vorm (geschreven door de plugin): { _id: 'current', eventId, minigame, phase,
 * hostServer, startedAtMs }.
 */
export function formatLiveEvent(doc) {
  if (!doc || !doc.minigame) {
    return null;
  }
  const meta = eventMeta(doc.minigame);
  return {
    eventId: doc.eventId ?? null,
    minigame: doc.minigame,
    name: meta.name,
    icon: meta.icon,
    accent: meta.color,
    phase: (doc.phase ?? 'PENDING').toLowerCase(), // 'pending' | 'running'
    startedAt: doc.startedAtMs ?? 0,
  };
}

/** Geen live event in demo-modus. */
export function demoLiveEvent() {
  return null;
}
