import opentype from 'opentype.js';

const INTERNAL_SCALE = 10; // 10 work-units per mm for precision

export function parseFont(arrayBuffer) {
  return opentype.parse(arrayBuffer);
}

// Returns glyph path commands in mm, baseline at y = fontSizeMm
export function getGlyphPathMm(font, char, fontSizeMm, xOffsetMm = 0) {
  const fontSize = fontSizeMm * INTERNAL_SCALE;
  const xOffset = xOffsetMm * INTERNAL_SCALE;
  const glyph = font.charToGlyph(char);
  const path = glyph.getPath(xOffset, fontSize, fontSize);
  return path.commands.map(cmd => {
    const m = { type: cmd.type };
    if (cmd.x  !== undefined) m.x  = cmd.x  / INTERNAL_SCALE;
    if (cmd.y  !== undefined) m.y  = cmd.y  / INTERNAL_SCALE;
    if (cmd.x1 !== undefined) m.x1 = cmd.x1 / INTERNAL_SCALE;
    if (cmd.y1 !== undefined) m.y1 = cmd.y1 / INTERNAL_SCALE;
    if (cmd.x2 !== undefined) m.x2 = cmd.x2 / INTERNAL_SCALE;
    if (cmd.y2 !== undefined) m.y2 = cmd.y2 / INTERNAL_SCALE;
    return m;
  });
}

// Advance width in mm
export function getAdvanceWidth(font, char, fontSizeMm) {
  const glyph = font.charToGlyph(char);
  return glyph.advanceWidth * (fontSizeMm / font.unitsPerEm);
}

// Split path commands into separate closed contours
export function extractContours(commands) {
  const contours = [];
  let current = [];
  let startX = 0, startY = 0;
  for (const cmd of commands) {
    if (cmd.type === 'M') {
      if (current.length > 1) {
        contours.push([...current, { type: 'L', x: startX, y: startY }]);
      }
      current = [{ type: 'M', x: cmd.x, y: cmd.y }];
      startX = cmd.x; startY = cmd.y;
    } else if (cmd.type === 'Z') {
      if (current.length > 1) {
        contours.push([...current, { type: 'L', x: startX, y: startY }]);
      }
      current = [];
    } else {
      current.push(cmd);
    }
  }
  if (current.length > 1) contours.push([...current, { type: 'L', x: startX, y: startY }]);
  return contours;
}

// Flatten bezier contour to polyline points (in mm)
export function flattenContour(commands, segments = 18) {
  const pts = [];
  let cx = 0, cy = 0;
  for (const cmd of commands) {
    switch (cmd.type) {
      case 'M': case 'L':
        pts.push({ x: cmd.x, y: cmd.y });
        cx = cmd.x; cy = cmd.y;
        break;
      case 'C':
        for (let i = 1; i <= segments; i++) {
          const t = i / segments, mt = 1 - t;
          pts.push({
            x: mt**3*cx + 3*mt**2*t*cmd.x1 + 3*mt*t**2*cmd.x2 + t**3*cmd.x,
            y: mt**3*cy + 3*mt**2*t*cmd.y1 + 3*mt*t**2*cmd.y2 + t**3*cmd.y
          });
        }
        cx = cmd.x; cy = cmd.y;
        break;
      case 'Q':
        for (let i = 1; i <= segments; i++) {
          const t = i / segments, mt = 1 - t;
          pts.push({
            x: mt**2*cx + 2*mt*t*cmd.x1 + t**2*cmd.x,
            y: mt**2*cy + 2*mt*t*cmd.y1 + t**2*cmd.y
          });
        }
        cx = cmd.x; cy = cmd.y;
        break;
      default: break;
    }
  }
  return pts;
}

// Total arc length of a polyline in mm
export function computeArcLength(points) {
  let len = 0;
  for (let i = 1; i < points.length; i++) {
    const dx = points[i].x - points[i-1].x;
    const dy = points[i].y - points[i-1].y;
    len += Math.sqrt(dx*dx + dy*dy);
  }
  return len;
}

// Sample polyline at equal arc-length spacing
export function sampleAtInterval(points, spacing) {
  if (!points.length || spacing <= 0) return [];
  const result = [{ ...points[0] }];
  let accumulated = 0, nextTarget = spacing;
  for (let i = 1; i < points.length; i++) {
    const dx = points[i].x - points[i-1].x;
    const dy = points[i].y - points[i-1].y;
    const segLen = Math.sqrt(dx*dx + dy*dy);
    while (accumulated + segLen >= nextTarget) {
      const t = (nextTarget - accumulated) / segLen;
      result.push({ x: points[i-1].x + t*dx, y: points[i-1].y + t*dy });
      nextTarget += spacing;
    }
    accumulated += segLen;
  }
  return result;
}

// Sample polyline into exactly N equally-spaced points
export function sampleAtCount(points, count) {
  const totalLen = computeArcLength(points);
  if (totalLen <= 0 || count <= 0) return [];
  return sampleAtInterval(points, totalLen / count);
}
