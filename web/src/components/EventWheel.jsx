import { useEffect, useMemo, useRef, useState } from 'react';

const TICK_MS = 50;
const FULL_TURNS = 5;

function stepDelayTicks(remaining) {
  if (remaining > 8) return 2;
  if (remaining > 3) return 5;
  if (remaining > 1) return 10;
  return 18;
}

function spinDurationMs(sequenceLength) {
  if (!sequenceLength) return 6000;
  let totalTicks = 0;
  for (let step = 0; step < sequenceLength; step++) {
    totalTicks += stepDelayTicks(sequenceLength - step - 1);
  }
  return Math.max(totalTicks * TICK_MS, 4000);
}

function easeOutQuint(t) {
  return 1 - (1 - t) ** 5;
}

function targetRotation(winnerIndex, segmentCount) {
  if (segmentCount <= 0 || winnerIndex < 0) return 0;
  const segmentAngle = 360 / segmentCount;
  return -(winnerIndex * segmentAngle + segmentAngle / 2);
}

function indexAtPointer(rotationDeg, segmentCount) {
  const segmentAngle = 360 / segmentCount;
  const index = Math.round(-rotationDeg / segmentAngle - 0.5);
  return ((index % segmentCount) + segmentCount) % segmentCount;
}

function isSpinRecent(spin) {
  if (!spin?.startedAt || !spin.sequence?.length) return false;
  const duration = spinDurationMs(spin.sequence.length);
  return Date.now() - spin.startedAt < duration + 2500;
}

function segmentGradient(segments) {
  if (!segments.length) return undefined;
  const slice = 100 / segments.length;
  const stops = segments.flatMap((seg, i) => {
    const color = `${seg.accent ?? '#ffb020'}44`;
    return [`${color} ${i * slice}%`, `${color} ${(i + 1) * slice}%`];
  });
  return `conic-gradient(from -90deg, ${stops.join(', ')})`;
}

export default function EventWheel({ spin, upcoming, pool = [] }) {
  const [rotation, setRotation] = useState(0);
  const [spinDone, setSpinDone] = useState(true);
  const spinKeyRef = useRef(null);

  const segments = useMemo(() => {
    if (pool.length > 0) return pool;
    if (spin?.candidates?.length) return spin.candidates;
    return upcoming ? [upcoming] : [];
  }, [pool, spin?.candidates, upcoming]);

  const lookup = useMemo(() => {
    const map = new Map();
    for (const item of [...pool, ...(spin?.candidates ?? []), ...(upcoming ? [upcoming] : [])]) {
      if (item?.id) map.set(item.id, item);
    }
    return map;
  }, [pool, spin?.candidates, upcoming]);

  const winnerId = spin?.winnerId ?? upcoming?.eventId;
  const winnerIndex = Math.max(0, segments.findIndex((s) => s.id === winnerId));
  const settledRotation = targetRotation(winnerIndex, segments.length);
  const shouldAnimate = Boolean(
    segments.length > 0
    && spin?.startedAt
    && spin?.sequence?.length
    && (spin.active || isSpinRecent(spin))
  );

  useEffect(() => {
    if (!shouldAnimate || !segments.length) {
      setRotation(settledRotation);
      setSpinDone(true);
      spinKeyRef.current = null;
      return undefined;
    }

    const spinKey = `${spin.startedAt}-${spin.winnerId}-${segments.length}`;
    if (spinKeyRef.current === spinKey) {
      return undefined;
    }
    spinKeyRef.current = spinKey;

    const duration = spinDurationMs(spin.sequence.length);
    const endRotation = settledRotation - FULL_TURNS * 360;
    const startTime = spin.startedAt;

    const elapsed = Date.now() - startTime;
    if (elapsed >= duration) {
      setRotation(settledRotation);
      setSpinDone(true);
      return undefined;
    }

    setSpinDone(false);

    let frameId = 0;
    const tick = () => {
      const progress = Math.min(1, (Date.now() - startTime) / duration);
      const eased = easeOutQuint(progress);
      setRotation(endRotation * eased);

      if (progress < 1) {
        frameId = requestAnimationFrame(tick);
      } else {
        setRotation(settledRotation);
        setSpinDone(true);
      }
    };

    frameId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frameId);
  }, [shouldAnimate, spin?.startedAt, spin?.winnerId, spin?.sequence?.length, segments.length, settledRotation]);

  const spinning = shouldAnimate && !spinDone;
  const pointerIndex = segments.length
    ? indexAtPointer(rotation, segments.length)
    : 0;
  const current = spinning
    ? segments[pointerIndex]
    : (lookup.get(winnerId) ?? upcoming ?? segments[pointerIndex]);

  if (!segments.length) {
    return (
      <div className="event-wheel event-wheel--empty">
        <p>Nog geen event gekozen. Een admin draait het rad in-game met <code>/randomevent</code>.</p>
      </div>
    );
  }

  const segmentAngle = 360 / segments.length;
  const compact = segments.length >= 10;

  return (
    <div className={`event-wheel ${spinning ? 'is-spinning' : 'is-settled'} ${compact ? 'event-wheel--compact' : ''}`}>
      <div className="event-wheel__pointer" aria-hidden="true" />
      <div
        className="event-wheel__ring"
        style={{
          transform: `rotate(${rotation}deg)`,
          '--segment-count': segments.length,
          background: segmentGradient(segments),
        }}
      >
        {segments.map((seg, i) => (
          <div
            key={seg.id}
            className="event-wheel__icon-slot"
            style={{ '--slot-angle': `${i * segmentAngle}deg` }}
          >
            <span className="event-wheel__slot-icon" title={seg.name ?? seg.displayName}>
              {seg.icon}
            </span>
          </div>
        ))}
      </div>
      <div className="event-wheel__center">
        <span className="event-wheel__icon">{current?.icon ?? '⚡'}</span>
        <span className="event-wheel__name">{current?.displayName ?? current?.name ?? '…'}</span>
        <span className="event-wheel__status">
          {spinning ? 'Het rad draait…' : upcoming ? 'Morgen op de planning' : 'Event pool'}
        </span>
      </div>
    </div>
  );
}
