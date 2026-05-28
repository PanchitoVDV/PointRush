import { useEffect, useMemo, useState } from 'react';

const TICK_MS = 50;

function stepDelayTicks(remaining) {
  if (remaining > 8) return 2;
  if (remaining > 3) return 5;
  if (remaining > 1) return 10;
  return 18;
}

function stepFromElapsed(startedAt, sequenceLength) {
  if (!startedAt || sequenceLength <= 0) return 0;
  const elapsedMs = Date.now() - startedAt;
  let totalTicks = 0;
  for (let step = 0; step < sequenceLength; step++) {
    const remaining = sequenceLength - step - 1;
    totalTicks += stepDelayTicks(remaining);
    if (elapsedMs < totalTicks * TICK_MS) return step;
  }
  return sequenceLength - 1;
}

function isSpinRecent(spin) {
  if (!spin?.startedAt || !spin.sequence?.length) return false;
  const totalTicks = spin.sequence.reduce((acc, _, step) => {
    const remaining = spin.sequence.length - step - 1;
    return acc + stepDelayTicks(remaining);
  }, 0);
  return Date.now() - spin.startedAt < totalTicks * TICK_MS + 4000;
}

export default function EventWheel({ spin, upcoming }) {
  const [step, setStep] = useState(0);
  const [spinDone, setSpinDone] = useState(false);

  const candidateMap = useMemo(() => {
    const map = new Map();
    for (const c of spin?.candidates ?? []) {
      map.set(c.id, c);
    }
    return map;
  }, [spin?.candidates]);

  const shouldAnimate = spin?.active || (spin && isSpinRecent(spin) && !spinDone);

  useEffect(() => {
    if (!shouldAnimate || !spin?.sequence?.length) {
      setSpinDone(true);
      return undefined;
    }

    setSpinDone(false);
    const interval = setInterval(() => {
      const current = stepFromElapsed(spin.startedAt, spin.sequence.length);
      setStep(current);
      const totalTicks = spin.sequence.reduce((acc, _, s) => {
        const remaining = spin.sequence.length - s - 1;
        return acc + stepDelayTicks(remaining);
      }, 0);
      if (Date.now() - spin.startedAt >= totalTicks * TICK_MS) {
        setSpinDone(true);
      }
    }, 40);

    return () => clearInterval(interval);
  }, [spin, shouldAnimate]);

  const winnerId = spin?.winnerId ?? upcoming?.eventId;
  const currentId = shouldAnimate && !spinDone
    ? spin.sequence[Math.min(step, spin.sequence.length - 1)]
    : winnerId;
  const current = candidateMap.get(currentId) ?? upcoming;
  const spinning = shouldAnimate && !spinDone;

  if (!current && !spin?.candidates?.length) {
    return (
      <div className="event-wheel event-wheel--empty">
        <p>Nog geen event gekozen. Een admin draait het rad in-game met <code>/randomevent</code>.</p>
      </div>
    );
  }

  const segments = spin?.candidates?.length ? spin.candidates : (upcoming ? [upcoming] : []);
  const segmentAngle = 360 / Math.max(segments.length, 1);

  return (
    <div className={`event-wheel ${spinning ? 'is-spinning' : 'is-settled'}`}>
      <div className="event-wheel__pointer" aria-hidden="true" />
      <div
        className="event-wheel__ring"
        style={{
          '--segment-count': segments.length,
          '--wheel-rotation': winnerId
            ? `${-segments.findIndex((s) => s.id === winnerId) * segmentAngle - segmentAngle / 2}deg`
            : '0deg',
        }}
      >
        {segments.map((seg, i) => (
          <div
            key={seg.id}
            className="event-wheel__segment"
            style={{
              '--i': i,
              '--accent': seg.accent ?? '#ffb020',
              transform: `rotate(${i * segmentAngle}deg) skewY(${90 - segmentAngle}deg)`,
            }}
          >
            <span className="event-wheel__segment-label">{seg.icon}</span>
          </div>
        ))}
      </div>
      <div className="event-wheel__center">
        <span className="event-wheel__icon">{current?.icon ?? '⚡'}</span>
        <span className="event-wheel__name">{current?.displayName ?? current?.name ?? '…'}</span>
        <span className="event-wheel__status">
          {spinning ? 'Het rad draait…' : upcoming ? 'Morgen op de planning' : 'Gekozen event'}
        </span>
      </div>
    </div>
  );
}
