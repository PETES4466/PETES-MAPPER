import React, { useEffect, useRef, useCallback } from 'react';
import { PORT_COLORS } from '../utils/wireUtils';

const FIRST_COLOR = '#00ff88';
const LAST_COLOR  = '#ff6b6b';
const SEL_COLOR   = 'rgba(255,255,255,0.9)';
const WIRING_COLOR = 'rgba(255,200,0,0.6)';
const PENDING_COLOR = 'rgba(0,212,255,0.9)';
const GUIDE_FILL  = 'rgba(30,50,120,0.25)';
const GUIDE_STROKE = 'rgba(80,120,220,0.25)';
const AUTO_PIXEL_COLOR  = '#00d4ff';
const MANUAL_PIXEL_COLOR = '#ff9f43';

export default function LedCanvas({
  pixels, wiringOrder, guideCommands,
  selectedIds, activeTool, pixelOdMm,
  showNumbers, showWiring, showGuide,
  wiringMode, pendingWire,
  onPixelMove, onPixelSelect, onWireClick
}) {
  const containerRef = useRef(null);
  const canvasRef    = useRef(null);
  const scaleRef     = useRef(2);       // mm → screen px
  const offsetRef    = useRef({ x: 40, y: 40 });
  const dprRef       = useRef(1);

  // Interaction state refs (avoid re-renders during drag)
  const isDraggingRef  = useRef(false);
  const dragPixelRef   = useRef(null);
  const dragStartRef   = useRef({ mx: 0, my: 0, px: 0, py: 0 });
  const isPanningRef   = useRef(false);
  const panStartRef    = useRef({ mx: 0, my: 0, ox: 0, oy: 0 });
  const livePixelRef   = useRef({}); // id → {x,y} for live drag preview

  // Keep latest props accessible in event handlers without re-creating them
  const pixelsRef       = useRef(pixels);
  const wiringOrderRef  = useRef(wiringOrder);
  const guideRef        = useRef(guideCommands);
  const selectedRef     = useRef(selectedIds);
  const activeToolRef   = useRef(activeTool);
  const pendingWireRef  = useRef(pendingWire);
  const showNumbersRef  = useRef(showNumbers);
  const showWiringRef   = useRef(showWiring);
  const showGuideRef    = useRef(showGuide);
  const pixelOdRef      = useRef(pixelOdMm);

  pixelsRef.current      = pixels;
  wiringOrderRef.current = wiringOrder;
  guideRef.current       = guideCommands;
  selectedRef.current    = selectedIds;
  activeToolRef.current  = activeTool;
  pendingWireRef.current = pendingWire;
  showNumbersRef.current = showNumbers;
  showWiringRef.current  = showWiring;
  showGuideRef.current   = showGuide;
  pixelOdRef.current     = pixelOdMm;

  // ── Coordinate helpers ──────────────────────────────────────────────────
  const mmToScreen = (x, y) => ({
    sx: x * scaleRef.current + offsetRef.current.x,
    sy: y * scaleRef.current + offsetRef.current.y
  });
  const screenToMm = (sx, sy) => ({
    x: (sx - offsetRef.current.x) / scaleRef.current,
    y: (sy - offsetRef.current.y) / scaleRef.current
  });

  // Hit-test a canvas mouse position against pixels
  const hitPixel = (mx, my) => {
    const { x, y } = screenToMm(mx, my);
    const r = pixelOdRef.current / 2 + 1;
    // Use livePixelRef for dragged pixel
    for (const p of pixelsRef.current) {
      const px = livePixelRef.current[p.id]?.x ?? p.x;
      const py = livePixelRef.current[p.id]?.y ?? p.y;
      const dx = px - x, dy = py - y;
      if (dx*dx + dy*dy <= r*r) return p;
    }
    return null;
  };

  // ── Render ──────────────────────────────────────────────────────────────
  const render = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = dprRef.current;
    const W = canvas.width / dpr;
    const H = canvas.height / dpr;
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    // Background
    ctx.fillStyle = '#0d0e1a';
    ctx.fillRect(0, 0, W, H);

    // Grid
    drawGrid(ctx, W, H);

    const scale  = scaleRef.current;
    const offset = offsetRef.current;
    const pixels = pixelsRef.current;
    const wOrder = wiringOrderRef.current;
    const guides = guideRef.current;
    const selIds = selectedRef.current;
    const pending = pendingWireRef.current;
    const od     = pixelOdRef.current;
    const r      = (od / 2) * scale;

    // Letter guides
    if (showGuideRef.current) {
      ctx.save();
      ctx.fillStyle = GUIDE_FILL;
      ctx.strokeStyle = GUIDE_STROKE;
      ctx.lineWidth = 1;
      for (const g of guides) {
        ctx.beginPath();
        for (const c of g.commands) {
          const sx = c.x * scale + offset.x;
          const sy = c.y * scale + offset.y;
          switch (c.type) {
            case 'M': ctx.moveTo(sx, sy); break;
            case 'L': ctx.lineTo(sx, sy); break;
            case 'C':
              ctx.bezierCurveTo(
                c.x1*scale+offset.x, c.y1*scale+offset.y,
                c.x2*scale+offset.x, c.y2*scale+offset.y,
                sx, sy); break;
            case 'Q':
              ctx.quadraticCurveTo(c.x1*scale+offset.x, c.y1*scale+offset.y, sx, sy); break;
            case 'Z': ctx.closePath(); break;
            default: break;
          }
        }
        ctx.fill('evenodd');
        ctx.stroke();
      }
      ctx.restore();
    }

    // Wiring path
    if (showWiringRef.current && wOrder.length > 1) {
      const pixMap = {};
      for (const p of pixels) pixMap[p.id] = p;
      ctx.save();
      ctx.strokeStyle = WIRING_COLOR;
      ctx.lineWidth = Math.max(1, scale * 0.4);
      ctx.setLineDash([3 * scale * 0.4, 3 * scale * 0.4]);
      ctx.beginPath();
      let firstDrawn = false;
      for (const id of wOrder) {
        const p = pixMap[id];
        if (!p) continue;
        const px = livePixelRef.current[id]?.x ?? p.x;
        const py = livePixelRef.current[id]?.y ?? p.y;
        const sx = px * scale + offset.x;
        const sy = py * scale + offset.y;
        if (!firstDrawn) { ctx.moveTo(sx, sy); firstDrawn = true; }
        else ctx.lineTo(sx, sy);
      }
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.restore();
    }

    // Pending wire
    if (pending.length > 1) {
      const pixMap = {};
      for (const p of pixels) pixMap[p.id] = p;
      ctx.save();
      ctx.strokeStyle = PENDING_COLOR;
      ctx.lineWidth = 2;
      ctx.beginPath();
      let fp = true;
      for (const id of pending) {
        const p = pixMap[id];
        if (!p) continue;
        const sx = p.x * scale + offset.x;
        const sy = p.y * scale + offset.y;
        if (fp) { ctx.moveTo(sx, sy); fp = false; }
        else ctx.lineTo(sx, sy);
      }
      ctx.stroke();
      ctx.restore();
    }

    // Pixels
    for (const p of pixels) {
      const px = livePixelRef.current[p.id]?.x ?? p.x;
      const py = livePixelRef.current[p.id]?.y ?? p.y;
      const sx = px * scale + offset.x;
      const sy = py * scale + offset.y;
      const isSelected = selIds.has(p.id);
      const isPending  = pending.includes(p.id);
      const portColor  = PORT_COLORS[Math.max(0, Math.min(7, p.portIndex ?? 0))];

      ctx.save();

      // Outer glow for selected
      if (isSelected) {
        ctx.shadowColor = SEL_COLOR;
        ctx.shadowBlur = 8;
      } else if (isPending) {
        ctx.shadowColor = PENDING_COLOR;
        ctx.shadowBlur = 10;
      } else if (p.isFirst) {
        ctx.shadowColor = FIRST_COLOR;
        ctx.shadowBlur = 6;
      } else if (p.isLast) {
        ctx.shadowColor = LAST_COLOR;
        ctx.shadowBlur = 6;
      }

      // Circle fill
      ctx.beginPath();
      ctx.arc(sx, sy, r, 0, Math.PI * 2);
      const fillColor = isSelected ? SEL_COLOR : isPending ? PENDING_COLOR : portColor;
      ctx.fillStyle = fillColor + (isSelected || isPending ? '' : 'cc');
      ctx.fill();

      // Border outline
      ctx.strokeStyle = isSelected ? '#ffffff' : isPending ? '#00d4ff' : p.isAuto ? portColor : MANUAL_PIXEL_COLOR;
      ctx.lineWidth = isSelected ? 2 : 1.5;
      ctx.stroke();

      ctx.restore();

      // First/Last markers
      if (p.isFirst && r > 4) {
        ctx.save();
        ctx.fillStyle = FIRST_COLOR;
        ctx.font = `bold ${Math.max(7, r * 0.9)}px monospace`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('F', sx, sy);
        ctx.restore();
      } else if (p.isLast && r > 4) {
        ctx.save();
        ctx.fillStyle = LAST_COLOR;
        ctx.font = `bold ${Math.max(7, r * 0.9)}px monospace`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('L', sx, sy);
        ctx.restore();
      }

      // Manual marker dot
      if (!p.isAuto && r > 6) {
        ctx.save();
        ctx.fillStyle = MANUAL_PIXEL_COLOR;
        ctx.beginPath();
        ctx.arc(sx + r * 0.55, sy - r * 0.55, 2.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      }
    }

    // Pixel numbers
    if (showNumbersRef.current && r > 5) {
      ctx.save();
      ctx.fillStyle = '#ffffff';
      ctx.font = `${Math.max(8, Math.min(12, r * 0.85))}px monospace`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      for (const p of pixels) {
        if (p.wiringOrder < 0) continue;
        const px = livePixelRef.current[p.id]?.x ?? p.x;
        const py = livePixelRef.current[p.id]?.y ?? p.y;
        const sx = px * scale + offset.x;
        const sy = py * scale + offset.y;
        ctx.fillText(p.wiringOrder, sx, sy);
      }
      ctx.restore();
    }

    // Placeholder
    if (pixels.length === 0 && guides.length === 0) {
      ctx.save();
      ctx.fillStyle = 'rgba(107,114,128,0.5)';
      ctx.font = '16px system-ui';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('Upload a font, enter text, and click Generate', W/2, H/2 - 12);
      ctx.font = '12px system-ui';
      ctx.fillText('Scroll to zoom · Middle-drag or Pan tool to move', W/2, H/2 + 12);
      ctx.restore();
    }
  }, []); // stable - always reads from refs

  function drawGrid(ctx, W, H) {
    const scale = scaleRef.current;
    const { x: ox, y: oy } = offsetRef.current;
    const gridMm = scale >= 4 ? 10 : scale >= 1 ? 20 : 50;
    const gridPx = gridMm * scale;
    ctx.save();
    ctx.strokeStyle = 'rgba(42,45,74,0.6)';
    ctx.lineWidth = 0.5;
    const startX = ((ox % gridPx) + gridPx) % gridPx;
    const startY = ((oy % gridPx) + gridPx) % gridPx;
    for (let x = startX; x <= W; x += gridPx) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    }
    for (let y = startY; y <= H; y += gridPx) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
    ctx.restore();
  }

  // ── Resize observer ─────────────────────────────────────────────────────
  useEffect(() => {
    const container = containerRef.current;
    const canvas = canvasRef.current;
    if (!container || !canvas) return;

    const ro = new ResizeObserver(entries => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect;
        const dpr = window.devicePixelRatio || 1;
        dprRef.current = dpr;
        canvas.width  = width  * dpr;
        canvas.height = height * dpr;
        canvas.style.width  = width  + 'px';
        canvas.style.height = height + 'px';
        render();
      }
    });
    ro.observe(container);
    return () => ro.disconnect();
  }, [render]);

  // Re-render when any display data changes
  useEffect(() => { render(); }, [pixels, wiringOrder, guideCommands, selectedIds, showNumbers, showWiring, showGuide, pendingWire, pixelOdMm, render]);

  // ── Mouse handlers ──────────────────────────────────────────────────────
  const getCanvasPos = (e) => {
    const rect = canvasRef.current.getBoundingClientRect();
    return { mx: e.clientX - rect.left, my: e.clientY - rect.top };
  };

  const handleMouseDown = (e) => {
    e.preventDefault();
    const { mx, my } = getCanvasPos(e);
    const tool = activeToolRef.current;

    // Middle mouse → pan
    if (e.button === 1 || tool === 'pan') {
      isPanningRef.current = true;
      panStartRef.current = { mx, my, ox: offsetRef.current.x, oy: offsetRef.current.y };
      return;
    }

    if (tool === 'wire') {
      const hit = hitPixel(mx, my);
      if (hit) {
        onWireClick(hit.id);
        render();
      }
      return;
    }

    if (tool === 'select') {
      const hit = hitPixel(mx, my);
      if (hit) {
        // Select + start drag
        onPixelSelect([hit.id], e.shiftKey);
        isDraggingRef.current = true;
        const { x: hx, y: hy } = screenToMm(mx, my);
        dragStartRef.current = { mx, my, px: hit.x, py: hit.y };
        dragPixelRef.current = hit;
      } else {
        // Deselect
        onPixelSelect([], false);
      }
    }
  };

  const handleMouseMove = (e) => {
    const { mx, my } = getCanvasPos(e);

    if (isPanningRef.current) {
      const ps = panStartRef.current;
      offsetRef.current = { x: ps.ox + (mx - ps.mx), y: ps.oy + (my - ps.my) };
      render();
      return;
    }

    if (isDraggingRef.current && dragPixelRef.current) {
      const ds = dragStartRef.current;
      const dp = dragPixelRef.current;
      const dxScreen = mx - ds.mx;
      const dyScreen = my - ds.my;
      const newX = dp.x + dxScreen / scaleRef.current;
      const newY = dp.y + dyScreen / scaleRef.current;
      livePixelRef.current = { ...livePixelRef.current, [dp.id]: { x: newX, y: newY } };
      render();
    }

    // Cursor
    const canvas = canvasRef.current;
    if (!canvas) return;
    const tool = activeToolRef.current;
    if (tool === 'pan') { canvas.style.cursor = 'grab'; return; }
    if (tool === 'wire') { canvas.style.cursor = 'crosshair'; return; }
    const hit = hitPixel(mx, my);
    canvas.style.cursor = hit ? (isDraggingRef.current ? 'grabbing' : 'grab') : 'default';
  };

  const handleMouseUp = (e) => {
    if (isPanningRef.current) {
      isPanningRef.current = false;
      return;
    }
    if (isDraggingRef.current && dragPixelRef.current) {
      const dp = dragPixelRef.current;
      const live = livePixelRef.current[dp.id];
      if (live) {
        onPixelMove(dp.id, live.x, live.y);
        livePixelRef.current = {};
      }
      isDraggingRef.current = false;
      dragPixelRef.current = null;
    }
  };

  const handleWheel = (e) => {
    e.preventDefault();
    const { mx, my } = getCanvasPos(e);
    const zoomFactor = e.deltaY < 0 ? 1.12 : 0.89;
    const newScale = Math.min(20, Math.max(0.2, scaleRef.current * zoomFactor));
    // Zoom toward mouse position
    const ox = offsetRef.current.x;
    const oy = offsetRef.current.y;
    offsetRef.current = {
      x: mx - (mx - ox) * (newScale / scaleRef.current),
      y: my - (my - oy) * (newScale / scaleRef.current)
    };
    scaleRef.current = newScale;
    render();
  };

  const handleContextMenu = (e) => e.preventDefault();

  return (
    <div
      ref={containerRef}
      className="canvas-container"
      data-testid="led-canvas-container"
    >
      <canvas
        ref={canvasRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        onContextMenu={handleContextMenu}
        data-testid="led-canvas"
      />
    </div>
  );
}
