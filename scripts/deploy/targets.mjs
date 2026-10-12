import assert from 'node:assert/strict';
export function awsTarget(distribution, bucket, prefix) {
  const config = distribution.Distribution.DistributionConfig;
  assert.equal(config.Enabled, true);
  assert.ok(config.Aliases.Items?.includes('app.queue-mate.com'), 'Distribution does not serve app.queue-mate.com');
  const origin = config.Origins.Items.find(o => o.Id === config.DefaultCacheBehavior.TargetOriginId);
  assert.ok(origin?.DomainName.startsWith(bucket + '.s3.') || origin?.DomainName.startsWith(bucket + '.s3-'), 'Default origin is not the expected S3 bucket');
  assert.equal((origin.OriginPath || '').replace(/^\//, ''), prefix);
  return origin;
}
export function vercelProject(project, env) {
  assert.equal(project.id, env.VERCEL_PROJECT_ID);
  assert.equal(project.accountId, env.VERCEL_ORG_ID);
  assert.equal(project.name, 'queue-mate-landing');
}
export function vercelDeployment(deployment, projectId) {
  assert.equal(deployment.projectId, projectId);
  assert.equal(deployment.readyState, 'READY');
  assert.match(deployment.id, /^dpl_[a-zA-Z0-9]+$/);
}
