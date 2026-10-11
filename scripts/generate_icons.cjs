const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

function makePNG(w, h, isMaskable = false) {
  const rowLen = w * 4 + 1;
  const raw = Buffer.alloc(rowLen * h);

  const cx = w / 2;
  const cy = h / 2;
  const cardRadius = Math.min(w, h) * (isMaskable ? 0.40 : 0.46);

  for (let y = 0; y < h; y++) {
    const rowOffset = y * rowLen;
    raw[rowOffset] = 0; // Filter None

    for (let x = 0; x < w; x++) {
      const px = rowOffset + 1 + x * 4;
      const dx = x - cx;
      const dy = y - cy;

      // Base background: Crisp white with smooth card
      let r = 255, g = 255, b = 255, a = 255;

      // Background rounded container with subtle shadow or INTO navy header
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist > cardRadius && !isMaskable) {
        a = 0; // Transparent corners outside round icon
      } else {
        // INTO Academy Navy field: #0B3C68
        r = 11; g = 60; b = 104;

        // Inside center: White badge plate
        const innerW = cardRadius * 1.35;
        const innerH = cardRadius * 0.95;
        if (Math.abs(dx) <= innerW / 2 && Math.abs(dy) <= innerH / 2) {
          r = 255; g = 255; b = 255;

          // German flag pill on the right side
          const flagX = innerW * 0.22;
          const flagW = innerW * 0.24;
          const flagH = innerH * 0.38;
          if (dx >= flagX && dx <= flagX + flagW && Math.abs(dy) <= flagH / 2) {
            const relY = dy / (flagH / 2);
            if (relY < -0.33) {
              r = 24; g = 24; b = 24; // Black
            } else if (relY < 0.33) {
              r = 222; g = 34; b = 43; // Red
            } else {
              r = 255; g = 204; b = 0; // Gold
            }
          }

          // Red corner accent (matching INTO 'I') on left
          const accentX = -innerW * 0.38;
          const accentY = -innerH * 0.32;
          if (dx >= accentX && dx <= accentX + innerW * 0.12 && dy >= accentY && dy <= accentY + innerH * 0.18) {
            if ((dx - accentX) + (dy - accentY) <= innerW * 0.12) {
              r = 222; g = 34; b = 43;
            }
          }

          // Red ACADEMY bar at bottom
          if (dy >= innerH * 0.22 && dy <= innerH * 0.34 && Math.abs(dx) <= innerW * 0.36) {
            r = 222; g = 34; b = 43;
          }
        }
      }

      raw[px] = r;
      raw[px + 1] = g;
      raw[px + 2] = b;
      raw[px + 3] = a;
    }
  }

  const idatData = zlib.deflateSync(raw);

  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[i] = c;
  }

  function crc32(buf) {
    let crc = 0 ^ (-1);
    for (let i = 0; i < buf.length; i++) {
      crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xFF];
    }
    return (crc ^ (-1)) >>> 0;
  }

  function chunk(type, data) {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length, 0);
    const t = Buffer.from(type, 'binary');
    const body = Buffer.concat([t, data]);
    const crc = crc32(body);
    const c = Buffer.alloc(4);
    c.writeUInt32BE(crc, 0);
    return Buffer.concat([len, t, data, c]);
  }

  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  return Buffer.concat([
    sig,
    chunk('IHDR', ihdr),
    chunk('IDAT', idatData),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

const pubDir = path.join(__dirname, '..', 'public');
if (!fs.existsSync(pubDir)) fs.mkdirSync(pubDir, { recursive: true });

fs.writeFileSync(path.join(pubDir, 'icon-192.png'), makePNG(192, 192, false));
fs.writeFileSync(path.join(pubDir, 'icon-512.png'), makePNG(512, 512, false));
fs.writeFileSync(path.join(pubDir, 'icon-maskable-512.png'), makePNG(512, 512, true));
console.log('INTO Academy branded icons generated successfully in public/');
