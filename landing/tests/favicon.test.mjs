import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile, mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import sharp from 'sharp';
import {buildBrandAssets, brandSource} from '../scripts/brand-assets.mjs';

test('landing icons are generated from the app vector in every supported size', async () => {
  const out = await mkdtemp(path.join(tmpdir(), 'queuemate-brand-'));
  try {
    const version = await buildBrandAssets(out);
    assert.match(version, /^[a-f0-9]{12}$/);
    const source = await readFile(brandSource, 'utf8');
    assert.equal(await readFile(path.join(out, 'assets/queuemate-mark.svg'), 'utf8'), source);
    const favicon = await readFile(path.join(out, 'favicon.svg'), 'utf8');
    assert.equal(favicon.match(/ d="([^"]+)"/)[1], source.match(/ d="([^"]+)"/)[1]);
    const ico = await readFile(path.join(out, 'favicon.ico'));
    assert.equal(ico.readUInt16LE(0), 0);
    assert.equal(ico.readUInt16LE(2), 1);
    assert.equal(ico.readUInt16LE(4), 3);
    for (let i = 0; i < 3; i++) {
      const entry = 6 + i * 16;
      const size = [16, 32, 48][i];
      assert.equal(ico[entry], size);
      assert.equal(ico[entry + 1], size);
      const length = ico.readUInt32LE(entry + 8), start = ico.readUInt32LE(entry + 12);
      assert.ok(start >= 54 && length > 0 && start + length <= ico.length);
      const meta = await sharp(ico.subarray(start, start + length)).metadata();
      assert.equal(meta.width, size);
      assert.equal(meta.height, size);
    }
    const apple = await sharp(path.join(out, 'apple-touch-icon.png')).metadata();
    assert.equal(apple.width, 180);
    assert.equal(apple.height, 180);
    assert.equal(apple.hasAlpha, false);
  } finally {
    await rm(out, {recursive: true, force: true});
  }
});
