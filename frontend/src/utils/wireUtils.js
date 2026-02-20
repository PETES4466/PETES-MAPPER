export const PORT_COLORS = [
  '#FF6B6B', '#FFD93D', '#6BCB77', '#4D96FF',
  '#FF9F43', '#A29BFE', '#FD79A8', '#00CEC9'
];

export const LETTER_COLORS = [
  '#00d4ff', '#00ff88', '#ffd93d', '#ff6b6b',
  '#ff9f43', '#a29bfe', '#fd79a8', '#00cec9',
  '#6bcb77', '#ff6b6b'
];

export const PORT_PIXEL_LIMIT = 1024;
export const PORT_COUNT = 8;

function dist(a, b) {
  return Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
}

function getTypicalSpacing(pixels) {
  if (pixels.length < 2) return 15;
  let total = 0, count = 0;
  const sample = pixels.slice(0, Math.min(30, pixels.length));
  for (let i = 0; i < sample.length; i++) {
    let minD = Infinity;
    for (let j = 0; j < sample.length; j++) {
      if (i !== j) minD = Math.min(minD, dist(sample[i], sample[j]));
    }
    if (isFinite(minD)) { total += minD; count++; }
  }
  return count > 0 ? total / count : 15;
}

function clusterRows(pixels, threshold) {
  const rows = new Map();
  for (const p of pixels) {
    let assigned = false;
    for (const [ry] of rows) {
      if (Math.abs(p.y - ry) < threshold) { rows.get(ry).push(p); assigned = true; break; }
    }
    if (!assigned) rows.set(p.y, [p]);
  }
  return rows;
}

// Nearest-neighbour row transitions – minimises cross-hollow jumps
function smartSnakeFill(pixels, direction = 'ltr-ttb') {
  if (!pixels.length) return [];
  const spacing = getTypicalSpacing(pixels);
  const rows = clusterRows(pixels, spacing * 0.75);
  const rowKeys = [...rows.keys()].sort((a, b) => a - b);

  const result = [];
  let lastPx = null;

  for (const rk of rowKeys) {
    const row = [...rows.get(rk)];
    if (!row.length) continue;

    if (lastPx) {
      const lr = [...row].sort((a, b) => a.x - b.x);
      const rl = [...row].sort((a, b) => b.x - a.x);
      result.push(...(dist(lastPx, lr[0]) <= dist(lastPx, rl[0]) ? lr : rl));
    } else {
      row.sort((a, b) => direction === 'rtl-ttb' ? b.x - a.x : a.x - b.x);
      result.push(...row);
    }
    lastPx = result[result.length - 1];
  }
  return result;
}

export function autoSnakeWiring(pixels, direction = 'ltr-ttb') {
  if (!pixels.length) return [];
  const byLetter = {};
  for (const p of pixels) {
    const k = p.letterIndex ?? 0;
    if (!byLetter[k]) byLetter[k] = { border: [], fill: [] };
    byLetter[k][p.type === 'fill' ? 'fill' : 'border'].push(p);
  }

  const ordered = [];
  const letterKeys = Object.keys(byLetter).map(Number).sort((a, b) => a - b);

  for (const lk of letterKeys) {
    const { border, fill } = byLetter[lk];
    ordered.push(...border);
    ordered.push(...smartSnakeFill(fill, direction));
  }
  return ordered.map(p => p.id);
}

// ── Port assignment driven by letterPortMap ────────────────────────────────
// letterPortMap: { letterIndex → portIndex } explicit assignments
// disconnectedAfter: Set<letterIndex> – visual gap but numbering continues
export function assignPortsWithLetterMap(
  pixels, wiringOrder,
  letterPortMap = {}, disconnectedAfter = new Set(),
  portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT
) {
  const pixelMap = {};
  for (const p of pixels) pixelMap[p.id] = p;

  // Determine letter order from wiringOrder
  const letterOrder = [];
  const seenLetters = new Set();
  for (const id of wiringOrder) {
    const p = pixelMap[id];
    if (!p) continue;
    const li = p.letterIndex ?? 0;
    if (!seenLetters.has(li)) { seenLetters.add(li); letterOrder.push(li); }
  }

  // Resolve effective port for each letter
  let currentPort = letterPortMap[letterOrder[0]] ?? 0;
  const letterEffectivePort = {};
  for (const li of letterOrder) {
    if (letterPortMap[li] !== undefined) currentPort = letterPortMap[li];
    letterEffectivePort[li] = currentPort;
  }

  // Track per-port running counts (reset when letter has explicit new port assignment)
  const portCounts = Object.fromEntries(Array.from({length: portCount}, (_, i) => [i, 0]));
  let prevLetterIndex = -1;

  // First/last per letter
  const letterFL = {};
  wiringOrder.forEach((id, idx) => {
    const p = pixelMap[id]; if (!p) return;
    const lk = p.letterIndex ?? 0;
    if (!letterFL[lk]) letterFL[lk] = { first: idx, last: idx };
    else letterFL[lk].last = idx;
  });

  const updated = pixels.map(p => ({ ...p }));
  const updMap = {};
  for (const p of updated) updMap[p.id] = p;

  wiringOrder.forEach((id, seqIdx) => {
    const p = updMap[id]; if (!p) return;
    const li = p.letterIndex ?? 0;
    const pi = letterEffectivePort[li] ?? 0;

    // Reset portPixelIndex if this letter has an explicit new port assignment
    if (li !== prevLetterIndex && letterPortMap[li] !== undefined) {
      portCounts[pi] = 0;
    }
    prevLetterIndex = li;

    portCounts[pi]++;
    p.wiringOrder    = seqIdx + 1;
    p.portIndex      = pi;
    p.portPixelIndex = portCounts[pi];
    p.isFirst = letterFL[li]?.first === seqIdx;
    p.isLast  = letterFL[li]?.last  === seqIdx;
  });

  return updated;
}

// Fallback: classic count-based auto assign
export function assignPorts(pixels, wiringOrder, portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT) {
  return assignPortsWithLetterMap(pixels, wiringOrder, {}, new Set(), portCount, pixelsPerPort);
}

export function getPortStats(pixels, portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT) {
  const stats = Array.from({ length: portCount }, (_, i) => ({ port: i + 1, count: 0, overflow: false }));
  for (const p of pixels) {
    const pi = p.portIndex ?? 0;
    if (pi >= 0 && pi < portCount) stats[pi].count++;
  }
  stats.forEach(s => { s.overflow = s.count > pixelsPerPort; });
  return { stats, totalPixels: pixels.length, totalOverflow: pixels.length > portCount * pixelsPerPort };
}

export function computeLetterZoom(letterIndex, pixels, canvasW, canvasH, pad = 60) {
  const lp = pixels.filter(p => (p.letterIndex ?? 0) === letterIndex);
  if (!lp.length) return null;
  const xs = lp.map(p => p.x), ys = lp.map(p => p.y);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = Math.min(...ys), maxY = Math.max(...ys);
  const lw = maxX - minX || 10, lh = maxY - minY || 10;
  const scale = Math.min((canvasW - 2 * pad) / lw, (canvasH - 2 * pad) / lh, 8);
  const ox = (canvasW - lw * scale) / 2 - minX * scale;
  const oy = (canvasH - lh * scale) / 2 - minY * scale;
  return { scale, offset: { x: ox, y: oy } };
}

// Initial port node positions (off-canvas left edge)
export function buildInitialPortNodes() {
  return Array.from({ length: 8 }, (_, i) => ({
    id: `pn_${i}`,
    portIndex: i,
    x: -45,           // mm – left of design area
    y: i * 20 + 5,    // mm – evenly spaced
    connectedLetterIndex: null,
    label: `P${i + 1}`
  }));
}

// Letter colors for break-apart mode
export const LETTER_COLORS = [
  '#00d4ff', '#00ff88', '#ffd93d', '#ff6b6b',
  '#ff9f43', '#a29bfe', '#fd79a8', '#00cec9',
  '#ff6b6b', '#6bcb77'
];

export const PORT_PIXEL_LIMIT = 1024;
export const PORT_COUNT = 8;

function dist(a, b) {
  return Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
}

function getTypicalSpacing(pixels) {
  if (pixels.length < 2) return 15;
  let total = 0, count = 0;
  const sample = pixels.slice(0, Math.min(30, pixels.length));
  for (let i = 0; i < sample.length; i++) {
    let minD = Infinity;
    for (let j = 0; j < sample.length; j++) {
      if (i !== j) minD = Math.min(minD, dist(sample[i], sample[j]));
    }
    if (isFinite(minD)) { total += minD; count++; }
  }
  return count > 0 ? total / count : 15;
}

// Cluster pixels into rows by Y proximity
function clusterRows(pixels, threshold) {
  const rows = new Map(); // rowY → pixels[]
  for (const p of pixels) {
    let assigned = false;
    for (const [ry] of rows) {
      if (Math.abs(p.y - ry) < threshold) {
        rows.get(ry).push(p);
        assigned = true;
        break;
      }
    }
    if (!assigned) rows.set(p.y, [p]);
  }
  return rows;
}

// Smart snake: nearest-neighbor row transitions to minimize hollow jumps
function smartSnakeFill(pixels, direction = 'ltr-ttb') {
  if (!pixels.length) return [];
  const spacing = getTypicalSpacing(pixels);
  const rows = clusterRows(pixels, spacing * 0.75);
  const rowKeys = [...rows.keys()].sort((a, b) => a - b);

  const result = [];
  let lastPx = null;

  for (let ri = 0; ri < rowKeys.length; ri++) {
    const row = [...rows.get(rowKeys[ri])];
    if (!row.length) continue;

    // Determine row sort order to minimize jump from lastPx
    if (lastPx) {
      // Sort row by x both ways, pick direction that minimizes first-pixel distance
      const rowLR = [...row].sort((a, b) => a.x - b.x);
      const rowRL = [...row].sort((a, b) => b.x - a.x);
      const dLR = dist(lastPx, rowLR[0]);
      const dRL = dist(lastPx, rowRL[0]);
      result.push(...(dLR <= dRL ? rowLR : rowRL));
    } else {
      row.sort((a, b) => direction === 'rtl-ttb' ? b.x - a.x : a.x - b.x);
      result.push(...row);
    }
    lastPx = result[result.length - 1];
  }
  return result;
}

// ── Main auto-snake wiring ────────────────────────────────────────────────────
export function autoSnakeWiring(pixels, direction = 'ltr-ttb') {
  if (!pixels.length) return [];

  // Group by letter
  const byLetter = {};
  for (const p of pixels) {
    const k = p.letterIndex ?? 0;
    if (!byLetter[k]) byLetter[k] = { border: [], fill: [] };
    byLetter[k][p.type === 'fill' ? 'fill' : 'border'].push(p);
  }

  const ordered = [];
  const letterKeys = Object.keys(byLetter).map(Number).sort((a, b) => a - b);

  for (const lk of letterKeys) {
    const { border, fill } = byLetter[lk];
    // Border follows contour order (already ordered by placement algorithm)
    ordered.push(...border);
    // Fill: smart snake avoiding hollow jumps
    ordered.push(...smartSnakeFill(fill, direction));
  }

  return ordered.map(p => p.id);
}

// ── Port assignment ───────────────────────────────────────────────────────────
export function assignPorts(pixels, wiringOrder, portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT) {
  const pixelMap = {};
  for (const p of pixels) pixelMap[p.id] = p;

  // Track first/last per letter
  const letterFL = {};
  wiringOrder.forEach((id, idx) => {
    const p = pixelMap[id];
    if (!p) return;
    const lk = p.letterIndex ?? 0;
    if (!letterFL[lk]) letterFL[lk] = { first: idx, last: idx };
    else letterFL[lk].last = idx;
  });

  const updated = pixels.map(p => ({ ...p }));
  const updMap = {};
  for (const p of updated) updMap[p.id] = p;

  wiringOrder.forEach((id, seq) => {
    const p = updMap[id];
    if (!p) return;
    p.wiringOrder = seq + 1;
    p.portIndex = Math.floor(seq / pixelsPerPort);
    p.portPixelIndex = (seq % pixelsPerPort) + 1;
    const lk = p.letterIndex ?? 0;
    p.isFirst = letterFL[lk]?.first === seq;
    p.isLast  = letterFL[lk]?.last  === seq;
  });

  return updated;
}

// ── Port stats ────────────────────────────────────────────────────────────────
export function getPortStats(pixels, portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT) {
  const stats = Array.from({ length: portCount }, (_, i) => ({ port: i + 1, count: 0, overflow: false }));
  for (const p of pixels) {
    const pi = p.portIndex ?? 0;
    if (pi >= 0 && pi < portCount) stats[pi].count++;
  }
  stats.forEach(s => { s.overflow = s.count > pixelsPerPort; });
  return { stats, totalPixels: pixels.length, totalOverflow: pixels.length > portCount * pixelsPerPort };
}

// ── Zoom to letter bounds ─────────────────────────────────────────────────────
export function computeLetterZoom(letterIndex, pixels, canvasW, canvasH, pad = 60) {
  const lp = pixels.filter(p => (p.letterIndex ?? 0) === letterIndex);
  if (!lp.length) return null;
  const xs = lp.map(p => p.x), ys = lp.map(p => p.y);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = Math.min(...ys), maxY = Math.max(...ys);
  const lw = maxX - minX || 10, lh = maxY - minY || 10;
  const scale = Math.min((canvasW - 2 * pad) / lw, (canvasH - 2 * pad) / lh);
  const ox = (canvasW - lw * scale) / 2 - minX * scale;
  const oy = (canvasH - lh * scale) / 2 - minY * scale;
  return { scale, offset: { x: ox, y: oy } };
}
