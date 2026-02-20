import {
  getAdvanceWidth,
  extractContours,
  flattenContour,
  computeArcLength,
  sampleAtInterval,
  sampleAtCount
} from './fontParser';

const BORDER_INTERNAL = 10; // work units per mm for border tracing
const FILL_INTERNAL   = 5;  // work units per mm for fill detection (lower for speed)

let _pixelCounter = 0;
function newId() { return `px_${++_pixelCounter}_${Date.now()}`; }

// Border pixels for one glyph (positions in mm)
export function generateBorderPixels(font, char, fontSizeMm, xOffsetMm, spacingMm, countOverride) {
  const fontSizePx = fontSizeMm * BORDER_INTERNAL;
  const glyph = font.charToGlyph(char);
  const path = glyph.getPath(xOffsetMm * BORDER_INTERNAL, fontSizePx, fontSizePx);

  const mmCmds = path.commands.map(c => {
    const m = { type: c.type };
    if (c.x  !== undefined) m.x  = c.x  / BORDER_INTERNAL;
    if (c.y  !== undefined) m.y  = c.y  / BORDER_INTERNAL;
    if (c.x1 !== undefined) m.x1 = c.x1 / BORDER_INTERNAL;
    if (c.y1 !== undefined) m.y1 = c.y1 / BORDER_INTERNAL;
    if (c.x2 !== undefined) m.x2 = c.x2 / BORDER_INTERNAL;
    if (c.y2 !== undefined) m.y2 = c.y2 / BORDER_INTERNAL;
    return m;
  });

  const contours = extractContours(mmCmds);
  const allPts = [];

  for (const contour of contours) {
    const poly = flattenContour(contour);
    const len = computeArcLength(poly);
    if (len < 1) continue;

    let pts;
    if (countOverride && countOverride > 0) {
      // Distribute countOverride pixels proportionally to this contour
      pts = sampleAtCount(poly, countOverride);
    } else {
      pts = sampleAtInterval(poly, spacingMm);
    }
    allPts.push(...pts);
  }

  return allPts.map(pt => ({ x: pt.x, y: pt.y, type: 'border' }));
}

// Fill pixels for one glyph using offscreen canvas pixel detection
export function generateFillPixels(font, char, fontSizeMm, xOffsetMm, spacingMm) {
  const fontSizePx = fontSizeMm * FILL_INTERNAL;
  const xOffsetPx = xOffsetMm * FILL_INTERNAL;
  const glyph = font.charToGlyph(char);
  const path = glyph.getPath(xOffsetPx, fontSizePx, fontSizePx);

  // Compute bounding box
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const c of path.commands) {
    if (c.x !== undefined) { minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x); }
    if (c.y !== undefined) { minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y); }
    if (c.x1 !== undefined) { minX = Math.min(minX, c.x1); maxX = Math.max(maxX, c.x1); }
    if (c.y1 !== undefined) { minY = Math.min(minY, c.y1); maxY = Math.max(maxY, c.y1); }
    if (c.x2 !== undefined) { minX = Math.min(minX, c.x2); maxX = Math.max(maxX, c.x2); }
    if (c.y2 !== undefined) { minY = Math.min(minY, c.y2); maxY = Math.max(maxY, c.y2); }
  }
  if (minX === Infinity) return [];

  const pad = FILL_INTERNAL * 2;
  const w = Math.ceil(maxX - minX + pad * 2) + 4;
  const h = Math.ceil(maxY - minY + pad * 2) + 4;
  const tx = -minX + pad;
  const ty = -minY + pad;

  const offCanvas = document.createElement('canvas');
  offCanvas.width = w;
  offCanvas.height = h;
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
  const spacingPx = spacingMm * FILL_INTERNAL;
  const pixels = [];

  for (let yPx = minY; yPx <= maxY; yPx += spacingPx) {
    for (let xPx = minX; xPx <= maxX; xPx += spacingPx) {
      const canvasX = Math.round(xPx + tx);
      const canvasY = Math.round(yPx + ty);
      if (canvasX >= 0 && canvasX < w && canvasY >= 0 && canvasY < h) {
        const idx = (canvasY * w + canvasX) * 4;
        if (imgData.data[idx] > 128) {
          pixels.push({ x: xPx / FILL_INTERNAL, y: yPx / FILL_INTERNAL, type: 'fill' });
        }
      }
    }
  }
  return pixels;
}

// Generate pixels for full text string
export function generatePixelsForText(font, text, settings) {
  const { fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm } = settings;
  const allRaw = [];
  let xOffset = 0;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (char === ' ') {
      xOffset += fontSizeMm * 0.28;
      continue;
    }

    const charPixels = [];
    if (mode === 'border' || mode === 'both') {
      const count = borderPixelCount === 'auto' ? null : parseInt(borderPixelCount, 10);
      const bp = generateBorderPixels(font, char, fontSizeMm, xOffset, borderSpacingMm, count);
      charPixels.push(...bp);
    }
    if (mode === 'fill' || mode === 'both') {
      const fp = generateFillPixels(font, char, fontSizeMm, xOffset, fillSpacingMm);
      charPixels.push(...fp);
    }

    charPixels.forEach(p => { p.letter = char; p.letterIndex = i; });
    allRaw.push(...charPixels);

    xOffset += getAdvanceWidth(font, char, fontSizeMm) + letterSpacingMm;
  }

  return allRaw;
}

// Build full pixel objects with unique IDs
export function buildPixelObjects(rawPixels) {
  _pixelCounter = 0;
  return rawPixels.map(p => ({
    id: newId(),
    x: p.x,
    y: p.y,
    type: p.type,
    letter: p.letter || '?',
    letterIndex: p.letterIndex ?? 0,
    portIndex: -1,
    portPixelIndex: -1,
    wiringOrder: -1,
    isFirst: false,
    isLast: false,
    isAuto: true,
    selected: false
  }));
}

// Get bounding box for canvas auto-fit
export function getPixelBounds(pixels) {
  if (!pixels.length) return { minX: 0, minY: 0, maxX: 200, maxY: 150 };
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const p of pixels) {
    minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
    maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
  }
  return { minX, minY, maxX, maxY };
}
