import { PORT_COUNT, PORT_PIXEL_LIMIT, assignPortsWithLetterMap } from './wireUtils';

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

  const ok = errors.length === 0;
  const summary = ok
    ? `Verification passed (${pixels.length} pixels, ${PORT_COUNT} ports validated).`
    : `Verification failed with ${errors.length} issue(s).`;

  return { ok, errors, warnings, summary, exportPixels };
}
