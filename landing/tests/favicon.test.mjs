import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
test('browser default favicon contains valid image entries', async () => {
  const bytes = await readFile(new URL('../public/favicon.ico', import.meta.url));
  assert.equal(bytes.readUInt16LE(0), 0);
  assert.equal(bytes.readUInt16LE(2), 1);
  assert.equal(bytes.readUInt16LE(4), 1);
  for (let i = 0; i < 1; i++) {
    const offset = 6 + i * 16;
    assert.equal(bytes[offset], bytes[offset + 1]);
    const length = bytes.readUInt32LE(offset + 8), start = bytes.readUInt32LE(offset + 12);
    assert.ok(start >= 22 && length > 0 && start + length <= bytes.length);
    assert.equal(bytes.subarray(start, start + 8).toString('hex'), '89504e470d0a1a0a');
  }
});
