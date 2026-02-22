import {
  getAdvanceWidth,
  extractContours,
  flattenContour,
  computeArcLength,
  sampleAtInterval,
  sampleAtCount
} from './fontParser';

// Adaptive canvas scale: cap at 1200px max dimension
function getFillScale(fontSizeMm) {
  return Math.min(5, Math.max(0.5, 1000 / fontSizeMm));
}
function getBorderScale(fontSizeMm) {
  return Math.min(10, Math.max(1, 2000 / fontSizeMm));
}

let _pixelCounter = 0;
function newId() { return `px_${++_pixelCounter}_${Date.now()}`; }

// ── Create glyph mask on offscreen canvas (returns ctx + metadata) ──────────
function createGlyphMask(font, char, fontSizeMm, xOffsetMm, scale) {
  const fontSizePx = fontSizeMm * scale;
  const glyph = font.charToGlyph(char);
  const path = glyph.getPath(xOffsetMm * scale, fontSizePx, fontSizePx);

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const c of path.commands) {
    if (c.x  !== undefined) { minX = Math.min(minX, c.x);  maxX = Math.max(maxX, c.x); }
    if (c.y  !== undefined) { minY = Math.min(minY, c.y);  maxY = Math.max(maxY, c.y); }
    if (c.x1 !== undefined) { minX = Math.min(minX, c.x1); maxX = Math.max(maxX, c.x1); }
    if (c.y1 !== undefined) { minY = Math.min(minY, c.y1); maxY = Math.max(maxY, c.y1); }
    if (c.x2 !== undefined) { minX = Math.min(minX, c.x2); maxX = Math.max(maxX, c.x2); }
    if (c.y2 !== undefined) { minY = Math.min(minY, c.y2); maxY = Math.max(maxY, c.y2); }
  }
  if (minX === Infinity) return null;

  const pad = Math.ceil(scale * 5) + 4;
  const w = Math.ceil(maxX - minX + pad * 2) + 2;
  const h = Math.ceil(maxY - minY + pad * 2) + 2;
  const tx = -minX + pad;
  const ty = -minY + pad;

  const offCanvas = document.createElement('canvas');
  offCanvas.width = w; offCanvas.height = h;
  const ctx = offCanvas.getContext('2d');

  ctx.fillStyle = 'white';
  ctx.beginPath();
  for (const c of path.commands) {
    switch (c.type) {
      case 'M': ctx.moveTo(c.x + tx, c.y + ty); break;
      case 'L': ctx.lineTo(c.x + tx, c.y + ty); break;
      case 'C': ctx.bezierCurveTo(c.x1+tx, c.y1+ty, c.x2+tx, c.y2+ty, c.x+tx, c.y+ty); break;
      case 'Q': ctx.quadraticCurveTo(c.x1+tx, c.y1+ty, c.x+tx, c.y+ty); break;
      case 'Z': ctx.closePath(); break;
      default: break;
    }
  }
  ctx.fill('evenodd');

  const imgData = ctx.getImageData(0, 0, w, h);
  return { imgData, w, h, tx, ty, scale, minX, minY, maxX, maxY };
}

// Check if a point (in canvas-scale pixels) is inside the glyph with margin
// Enhanced to ensure pixel circles don't bleed outside
function isInsideWithMargin(imgData, w, h, cx, cy, marginPx, pixelRadiusPx = 0) {
  if (cx < 0 || cx >= w || cy < 0 || cy >= h) return false;
  if (imgData.data[(cy * w + cx) * 4] <= 128) return false;
  
  // Check the actual pixel circle boundary, not just the center
  const checkRadius = marginPx + pixelRadiusPx;
  if (checkRadius <= 0) return true;
  
  // 24-point ring check at margin + pixel radius for more precision
  const points = 24;
  for (let i = 0; i < points; i++) {
    const angle = (i / points) * Math.PI * 2;
    const mx = Math.round(cx + Math.cos(angle) * checkRadius);
    const my = Math.round(cy + Math.sin(angle) * checkRadius);
    if (mx < 0 || mx >= w || my < 0 || my >= h || imgData.data[(my * w + mx) * 4] <= 128) {
      return false;
    }
  }
  return true;
}

// ── Border pixels ────────────────────────────────────────────────────────────
export function generateBorderPixels(font, char, fontSizeMm, xOffsetMm, spacingMm, countOverride, edgeMarginMm = 0) {
  const SCALE = getBorderScale(fontSizeMm);
  const fontSizePx = fontSizeMm * SCALE;
  const glyph = font.charToGlyph(char);
  const path = glyph.getPath(xOffsetMm * SCALE, fontSizePx, fontSizePx);

  const mmCmds = path.commands.map(c => {
    const m = { type: c.type };
    if (c.x  !== undefined) m.x  = c.x  / SCALE;
    if (c.y  !== undefined) m.y  = c.y  / SCALE;
    if (c.x1 !== undefined) m.x1 = c.x1 / SCALE;
    if (c.y1 !== undefined) m.y1 = c.y1 / SCALE;
    if (c.x2 !== undefined) m.x2 = c.x2 / SCALE;
    if (c.y2 !== undefined) m.y2 = c.y2 / SCALE;
    return m;
  });

  const contours = extractContours(mmCmds);
  const allPts = [];

  for (const contour of contours) {
    const poly = flattenContour(contour);
    const len = computeArcLength(poly);
    if (len < 1) continue;
    const pts = countOverride && countOverride > 0
      ? sampleAtCount(poly, countOverride)
      : sampleAtInterval(poly, spacingMm);
    allPts.push(...pts.map((pt, i) => ({ ...pt, _contourIdx: i, _polyLen: poly.length, _poly: poly })));
  }

  if (edgeMarginMm <= 0) {
    return allPts.map(pt => ({ x: pt.x, y: pt.y, type: 'border' }));
  }

  // Offset border points inward using fill mask
  const FILL_SC = getFillScale(fontSizeMm);
  const mask = createGlyphMask(font, char, fontSizeMm, xOffsetMm, FILL_SC);
  if (!mask) return allPts.map(pt => ({ x: pt.x, y: pt.y, type: 'border' }));
  const { imgData, w, h, tx, ty } = mask;

  return allPts.map((pt, idx) => {
    // Compute tangent from neighboring points in same contour
    const poly = pt._poly;
    const i = Math.min(pt._contourIdx, poly.length - 1);
    const prev = poly[Math.max(0, i - 1)];
    const next = poly[Math.min(poly.length - 1, i + 1)];
    const tdx = next.x - prev.x, tdy = next.y - prev.y;
    const tlen = Math.sqrt(tdx * tdx + tdy * tdy);
    if (tlen < 0.001) return { x: pt.x, y: pt.y, type: 'border' };

    const n1 = { x: -tdy / tlen, y: tdx / tlen };
    const n2 = { x: tdy / tlen, y: -tdx / tlen };

    function testNormal(nx, ny) {
      const testX = Math.round((pt.x + nx * (edgeMarginMm + 1)) * FILL_SC + tx);
      const testY = Math.round((pt.y + ny * (edgeMarginMm + 1)) * FILL_SC + ty);
      if (testX < 0 || testX >= w || testY < 0 || testY >= h) return false;
      return imgData.data[(testY * w + testX) * 4] > 128;
    }

    const use1 = testNormal(n1.x, n1.y);
    const use2 = testNormal(n2.x, n2.y);
    if (use1) return { x: pt.x + n1.x * edgeMarginMm, y: pt.y + n1.y * edgeMarginMm, type: 'border' };
    if (use2) return { x: pt.x + n2.x * edgeMarginMm, y: pt.y + n2.y * edgeMarginMm, type: 'border' };
    return { x: pt.x, y: pt.y, type: 'border' };
  });
}

// ── Fill pixels ──────────────────────────────────────────────────────────────
// Stricter placement: ensure entire pixel circle stays inside the glyph
export function generateFillPixels(font, char, fontSizeMm, xOffsetMm, spacingMm, edgeMarginMm = 0, pixelOdMm = 12) {
  const SCALE = getFillScale(fontSizeMm);
  const mask = createGlyphMask(font, char, fontSizeMm, xOffsetMm, SCALE);
  if (!mask) return [];
  const { imgData, w, h, tx, ty, minX, minY, maxX, maxY } = mask;

  const spacingPx = spacingMm * SCALE;
  const marginPx  = edgeMarginMm * SCALE;
  const pixelRadiusPx = (pixelOdMm / 2) * SCALE; // Half the pixel diameter
  const pixels = [];

  for (let yPx = minY; yPx <= maxY; yPx += spacingPx) {
    for (let xPx = minX; xPx <= maxX; xPx += spacingPx) {
      const cx = Math.round(xPx + tx);
      const cy = Math.round(yPx + ty);
      // Check if the entire pixel circle (center + radius + margin) fits inside
      if (isInsideWithMargin(imgData, w, h, cx, cy, marginPx, pixelRadiusPx)) {
        pixels.push({ x: xPx / SCALE, y: yPx / SCALE, type: 'fill' });
      }
    }
  }
  return pixels;
}

// ── Full text pixel generation ───────────────────────────────────────────────
export function generatePixelsForText(font, text, settings) {
  const { fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm, edgeMarginMm = 3 } = settings;
  const allRaw = [];
  let xOffset = 0;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (char === ' ') { xOffset += fontSizeMm * 0.28; continue; }

    const charPixels = [];
    if (mode === 'border' || mode === 'both') {
      const count = borderPixelCount === 'auto' ? null : parseInt(borderPixelCount, 10);
      charPixels.push(...generateBorderPixels(font, char, fontSizeMm, xOffset, borderSpacingMm, count, edgeMarginMm));
    }
    if (mode === 'fill' || mode === 'both') {
      charPixels.push(...generateFillPixels(font, char, fontSizeMm, xOffset, fillSpacingMm, edgeMarginMm));
    }

    charPixels.forEach(p => { p.letter = char; p.letterIndex = i; });
    allRaw.push(...charPixels);
    xOffset += getAdvanceWidth(font, char, fontSizeMm) + letterSpacingMm;
  }
  return allRaw;
}

export function buildPixelObjects(rawPixels) {
  _pixelCounter = 0;
  return rawPixels.map(p => ({
    id: newId(),
    x: p.x, y: p.y,
    type: p.type,
    letter: p.letter || '?',
    letterIndex: p.letterIndex ?? 0,
    portIndex: -1, portPixelIndex: -1,
    wiringOrder: -1,
    isFirst: false, isLast: false,
    isAuto: true,
    wiringBroken: false,
    selected: false
  }));
}

export function getPixelBounds(pixels) {
  if (!pixels.length) return { minX: 0, minY: 0, maxX: 200, maxY: 150 };
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const p of pixels) {
    minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
    maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
  }
  return { minX, minY, maxX, maxY };
}
