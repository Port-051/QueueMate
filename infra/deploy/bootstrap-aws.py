#!/usr/bin/env python3
"""Run in the AWS account's CloudShell. Creates only the frontend deploy role."""
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
STACK = 'queuemate-frontend-github-oidc'
STACK_REGION = 'ap-northeast-2'
DISTRIBUTION = sys.argv[1] if len(sys.argv) > 1 else 'E256CECAKU833U'

def aws(*args):
    result = subprocess.run(['aws', *args, '--output', 'json', '--no-cli-pager'], check=True, capture_output=True, text=True)
    return json.loads(result.stdout) if result.stdout.strip() else {}

assert re.fullmatch(r'[A-Z0-9]+', DISTRIBUTION)
identity = aws('sts', 'get-caller-identity')
config = aws('cloudfront', 'get-distribution', '--id', DISTRIBUTION)['Distribution']['DistributionConfig']
assert config['Enabled'] and 'app.queue-mate.com' in config['Aliases'].get('Items', []), 'Wrong distribution/domain'
origin = next(o for o in config['Origins']['Items'] if o['Id'] == config['DefaultCacheBehavior']['TargetOriginId'])
match = re.fullmatch(r'([a-z0-9][a-z0-9.-]+)\.s3(?:[.-][a-z0-9-]+)?\.amazonaws\.com', origin['DomainName'])
assert match, 'Default origin must be an S3 bucket'
bucket = match.group(1)
prefix = origin.get('OriginPath', '').removeprefix('/')
assert not prefix or re.fullmatch(r'[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*', prefix), 'Unsupported origin prefix'
region = aws('s3api', 'get-bucket-location', '--bucket', bucket)['LocationConstraint'] or 'us-east-1'
if region == 'EU':
    region = 'eu-west-1'
# Verify the app entry point before granting access to this bucket.
aws('s3api', 'head-object', '--bucket', bucket, '--key', (prefix + '/' if prefix else '') + 'index.html')
providers = aws('iam', 'list-open-id-connect-providers')['OpenIDConnectProviderList']
provider = next((p['Arn'] for p in providers if p['Arn'].endswith('/token.actions.githubusercontent.com')), '')
if provider:
    existing = aws('iam', 'get-open-id-connect-provider', '--open-id-connect-provider-arn', provider)
    assert 'sts.amazonaws.com' in existing['ClientIDList'], 'Existing GitHub OIDC provider lacks AWS audience'
# Preserve ownership of a provider created by an earlier run of this stack.
stacks = aws('cloudformation', 'list-stacks', '--region', STACK_REGION)['StackSummaries']
if any(s['StackName'] == STACK and s['StackStatus'] != 'DELETE_COMPLETE' for s in stacks):
    stack = aws('cloudformation', 'describe-stacks', '--region', STACK_REGION, '--stack-name', STACK)['Stacks'][0]
    provider = next(p['ParameterValue'] for p in stack['Parameters'] if p['ParameterKey'] == 'ExistingOIDCProviderArn')
print(f"Configure frontend deploy role in account {identity['Account']}: s3://{bucket}/{prefix}, CloudFront {DISTRIBUTION}", flush=True)
subprocess.run([
    'aws', 'cloudformation', 'deploy', '--region', STACK_REGION,
    '--stack-name', STACK, '--template-file', str(ROOT / 'infra/deploy/aws-role.json'),
    '--capabilities', 'CAPABILITY_NAMED_IAM', '--no-fail-on-empty-changeset', '--no-cli-pager',
    '--parameter-overrides', f'BucketName={bucket}', f'ObjectPrefix={prefix + "/" if prefix else ""}',
    f'DistributionId={DISTRIBUTION}', f'ExistingOIDCProviderArn={provider}',
], check=True)
stack = aws('cloudformation', 'describe-stacks', '--region', STACK_REGION, '--stack-name', STACK)['Stacks'][0]
role = next(o['OutputValue'] for o in stack['Outputs'] if o['OutputKey'] == 'DeployRoleArn')
variables = {'AWS_ROLE_ARN': role, 'AWS_REGION': region, 'AWS_S3_BUCKET': bucket, 'AWS_S3_PREFIX': prefix, 'AWS_CLOUDFRONT_DISTRIBUTION_ID': DISTRIBUTION}
output = ROOT / '.deploy/aws-config.json'
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(variables, indent=2) + '\n')
print(f'Non-secret GitHub environment variables written to {output}:')
print(json.dumps(variables, indent=2))
