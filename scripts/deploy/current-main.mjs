import assert from 'node:assert/strict';
const {GITHUB_TOKEN:token, GITHUB_REPOSITORY:repository, GITHUB_SHA:sha, GITHUB_REF:ref} = process.env;
assert.equal(repository, 'Port-051/QueueMate');
assert.equal(ref, 'refs/heads/main');
const response = await fetch(`https://api.github.com/repos/${repository}/git/ref/heads/main`, {headers:{Authorization:`Bearer ${token}`,Accept:'application/vnd.github+json'},signal:AbortSignal.timeout(15000)});
assert.equal(response.status, 200);
assert.equal((await response.json()).object.sha, sha, 'A newer main commit exists; this obsolete build must not deploy');
