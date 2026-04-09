import { PORT_COUNT, PORT_PIXEL_LIMIT, assignPortsWithLetterMap } from './wireUtils';
const CLOSED_LETTERS = new Set(['O', 'D', 'P', 'Q', 'R', 'B', '0', '6', '8', '9']);

function dist(a, b) {
  return Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
}

function getTypicalSpacing(pixels) {
  if (pixels.length < 2) return 15;
  let total = 0;
  let count = 0;
  const sample = pixels.slice(0, Math.min(40, pixels.length));
  for (let i = 0; i < sample.length; i++) {
    let minD = Infinity;
    for (let j = 0; j < sample.length; j++) {
      if (i === j) continue;
      minD = Math.min(minD, dist(sample[i], sample[j]));
    }
    if (Number.isFinite(minD)) {
      total += minD;
      count += 1;
    }
  }
  return count > 0 ? total / count : 15;
}

function groupPixelsByLetterAndType(pixels) {
  const keys = new Set();
  for (const p of pixels) {
    const li = p.letterIndex ?? 0;
    const type = p.type === 'fill' ? 'fill' : 'border';
    keys.add(`${li}_${type}`);
  }
  return keys;
}

export function buildExportPixels(pixels, wiringOrder, letterPortMap) {
  const assigned = assignPortsWithLetterMap(pixels, wiringOrder, letterPortMap, PORT_COUNT, PORT_PIXEL_LIMIT);
  const orderIndexMap = new Map(wiringOrder.map((id, idx) => [id, idx + 1]));
  const firstByLetter = new Map();
  const lastByLetter = new Map();

  for (const id of wiringOrder) {
    const p = assigned.find(px => px.id === id);
    if (!p) continue;
    const letterKey = `${p.letterIndex ?? 0}_${p.letter ?? ''}`;
    if (!firstByLetter.has(letterKey)) firstByLetter.set(letterKey, id);
    lastByLetter.set(letterKey, id);
  }

  return assigned.map((p) => {
    const letterKey = `${p.letterIndex ?? 0}_${p.letter ?? ''}`;
    return {
      ...p,
      wiringOrder: orderIndexMap.get(p.id) ?? -1,
      isFirst: firstByLetter.get(letterKey) === p.id,
      isLast: lastByLetter.get(letterKey) === p.id,
      isAuto: true
    };
  });
}

export function verifyLedEditLayout(pixels, wiringOrder, letterPortMap) {
  const errors = [];
  const warnings = [];

  if (!pixels?.length) {
    errors.push('No pixels generated.');
    return { ok: false, errors, warnings, summary: 'No pixels to verify.' };
  }

  if (!Array.isArray(wiringOrder) || wiringOrder.length === 0) {
    errors.push('Wiring path is empty.');
  }

  const pixelIds = new Set(pixels.map(p => p.id));
  const orderIds = new Set(wiringOrder);

  if (wiringOrder.length !== pixels.length) {
    errors.push(`Wiring count mismatch: wiring has ${wiringOrder.length}, pixels has ${pixels.length}.`);
  }
  if (orderIds.size !== wiringOrder.length) {
    errors.push('Wiring path contains duplicate pixel IDs.');
  }
  for (const id of wiringOrder) {
    if (!pixelIds.has(id)) {
      errors.push(`Wiring references unknown pixel ID: ${id}`);
      break;
    }
  }
  for (const p of pixels) {
    if (!orderIds.has(p.id)) {
      errors.push(`Pixel missing in wiring path: ${p.id}`);
      break;
    }
  }

  const requiredKeys = groupPixelsByLetterAndType(pixels);
  for (const key of requiredKeys) {
    if (letterPortMap?.[key] === undefined) {
      errors.push(`Missing port assignment for ${key} (letterIndex_type).`);
    }
  }

  const exportPixels = buildExportPixels(pixels, wiringOrder, letterPortMap || {});
  const perPort = Array.from({ length: PORT_COUNT }, () => []);

  for (const p of exportPixels) {
    if (p.portIndex < 0 || p.portIndex >= PORT_COUNT) {
      errors.push(`Invalid port for pixel ${p.id}.`);
      continue;
    }
    perPort[p.portIndex].push(p);
  }

  for (let i = 0; i < PORT_COUNT; i++) {
    const items = perPort[i];
    if (items.length > PORT_PIXEL_LIMIT) {
      errors.push(`P${i + 1} overflow: ${items.length}/${PORT_PIXEL_LIMIT}.`);
    }
    if (items.length === 0) {
      warnings.push(`P${i + 1} has no assigned pixels.`);
    }

    const seqs = items.map(p => p.portPixelIndex).sort((a, b) => a - b);
    for (let s = 0; s < seqs.length; s++) {
      if (seqs[s] !== s + 1) {
        errors.push(`P${i + 1} sequence gap/duplicate near index ${s + 1}.`);
        break;
      }
    }

    const fillLetters = new Set(items.filter(p => p.type === 'fill').map(p => p.letterIndex ?? 0));
    if (fillLetters.size > 1) {
      errors.push(`P${i + 1} has fill pixels from multiple letters. Keep one letter fill per port to avoid carry-over.`);
    }
  }

  const seenPerPort = Array.from({ length: PORT_COUNT }, () => 0);
  for (const id of wiringOrder) {
    const p = exportPixels.find(px => px.id === id);
    if (!p || p.portIndex < 0) continue;
    seenPerPort[p.portIndex] += 1;
    if (p.portPixelIndex !== seenPerPort[p.portIndex]) {
      errors.push(
        `Wiring/port mismatch on P${p.portIndex + 1}: expected ${seenPerPort[p.portIndex]}, got ${p.portPixelIndex}.`
      );
      break;
    }
  }

  const pixelById = new Map(exportPixels.map(p => [p.id, p]));
  const closedFillPixels = exportPixels.filter(
    p => p.type === 'fill' && CLOSED_LETTERS.has(String(p.letter || '').toUpperCase())
  );
  const closedSpacing = getTypicalSpacing(closedFillPixels);
  const maxJump = Math.max(10, closedSpacing * 2.3);
  for (let i = 1; i < wiringOrder.length; i++) {
    const prev = pixelById.get(wiringOrder[i - 1]);
    const next = pixelById.get(wiringOrder[i]);
    if (!prev || !next) continue;
    if (
      prev.type === 'fill' &&
      next.type === 'fill' &&
      (prev.letterIndex ?? 0) === (next.letterIndex ?? 0) &&
      CLOSED_LETTERS.has(String(prev.letter || '').toUpperCase())
    ) {
      const d = dist(prev, next);
      if (d > maxJump) {
        errors.push(
          `Long jump detected in closed fill flow for letter "${prev.letter}" near node ${i}: ${d.toFixed(1)}mm.`
        );
        break;
      }
    }
  }

  const ok = errors.length === 0;
  const summary = ok
    ? `Verification passed (${pixels.length} pixels, ${PORT_COUNT} ports validated).`
    : `Verification failed with ${errors.length} issue(s).`;

  return { ok, errors, warnings, summary, exportPixels };
}
