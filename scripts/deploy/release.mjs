import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readdir, readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';

export const digest = bytes => createHash('sha256').update(bytes).digest('hex');
export function safePath(name) {
  assert.ok(/^[a-zA-Z0-9_./-]+$/.test(name) && !name.startsWith('/') && !name.split('/').some(p => !p || p === '.' || p === '..' || p.startsWith('.')), 'Unsafe release path');
  return name;
}
export async function createRelease(directory, sha, kind) {
  assert.match(sha, /^[a-f0-9]{40}$/);
  assert.ok(['app', 'landing'].includes(kind));
  const files = {};
  async function walk(dir) {
    for (const entry of await readdir(dir, {withFileTypes:true})) {
      const file = path.join(dir, entry.name);
      assert.ok(!entry.isSymbolicLink(), 'Release cannot contain symlinks');
      if (entry.isDirectory()) await walk(file);
      else {
        const relative = safePath(path.relative(directory, file).split(path.sep).join('/'));
        if (relative !== 'release.json') files[relative] = digest(await readFile(file));
      }
    }
  }
  await walk(directory);
  assert.ok(files['index.html']);
  assert.ok(files[kind === 'app' ? 'brand/queuemate-mark.svg' : 'assets/queuemate-mark.svg']);
  const release = {schema:1, sha, kind, files};
  await writeFile(path.join(directory, 'release.json'), JSON.stringify(release, null, 2) + '\n');
  return release;
}
export async function validateLocal(directory, sha) {
  const release = JSON.parse(await readFile(path.join(directory, 'release.json'), 'utf8'));
  assert.equal(release.schema, 1);
  assert.match(sha || '', /^[a-f0-9]{40}$/);
  assert.equal(release.sha, sha, 'Artifact belongs to another commit');
  assert.ok(['app', 'landing'].includes(release.kind));
  assert.ok(release.files && typeof release.files === 'object' && !Array.isArray(release.files));
  assert.ok(release.files['index.html'], 'Index missing');
  assert.ok(release.files[release.kind === 'app' ? 'brand/queuemate-mark.svg' : 'assets/queuemate-mark.svg'], 'Logo missing');
  for (const [name, hash] of Object.entries(release.files)) {
    assert.notEqual(name, 'release.json');
    assert.match(hash, /^[a-f0-9]{64}$/);
    assert.equal(digest(await readFile(path.join(directory, safePath(name)))), hash, `Artifact corrupted: ${name}`);
  }
  return release;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [directory, sha, kind] = process.argv.slice(2);
  const release = await createRelease(directory, sha, kind);
  console.log(`${kind}: ${Object.keys(release.files).length} files, commit ${sha}`);
}
