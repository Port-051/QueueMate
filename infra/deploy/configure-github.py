#!/usr/bin/env python3
"""Configure deployment environments; accepts only non-secret AWS output JSON."""
import argparse
import json
from pathlib import Path
import re
import subprocess

REPO = 'Port-051/QueueMate'
p = argparse.ArgumentParser()
p.add_argument('--aws-config', type=Path)
p.add_argument('--protect-main', action='store_true')
args = p.parse_args()

def api(endpoint, method='GET', body=None):
    command = ['gh', 'api', f'repos/{REPO}/{endpoint}', '--method', method]
    if body is not None:
        command += ['--input', '-']
    result = subprocess.run(command, input=json.dumps(body) if body is not None else None, text=True, capture_output=True, check=True)
    return json.loads(result.stdout) if result.stdout.strip() else None

for name in ['production-app', 'production-landing']:
    api(f'environments/{name}', 'PUT', {'deployment_branch_policy':{'protected_branches':False,'custom_branch_policies':True}})
    policies = api(f'environments/{name}/deployment-branch-policies')['branch_policies']
    if not any(p['name'] == 'main' and p['type'] == 'branch' for p in policies):
        api(f'environments/{name}/deployment-branch-policies', 'POST', {'name':'main','type':'branch'})
    if any(p['name'] != 'main' or p['type'] != 'branch' for p in policies):
        raise SystemExit(f'Unexpected pre-existing branch policy in {name}; review it before enabling releases')

def variables(environment, values):
    for key, value in values.items():
        if value == '':
            continue  # Unset optional origin prefix means bucket root.
        subprocess.run(['gh','variable','set',key,'--repo',REPO,'--env',environment,'--body',value], check=True)

variables('production-landing', {'VERCEL_ORG_ID':'team_TYonFQEoE1HOMC5bYb5xvQXV','VERCEL_PROJECT_ID':'prj_twOVEOMaFLLQtgu0n4o5l7pmggiu','VERCEL_SCOPE':'port-52'})
if args.aws_config:
    values = json.loads(args.aws_config.read_text())
    expected = {'AWS_ROLE_ARN','AWS_REGION','AWS_S3_BUCKET','AWS_S3_PREFIX','AWS_CLOUDFRONT_DISTRIBUTION_ID'}
    assert set(values) == expected, 'Only the non-secret bootstrap output is accepted'
    assert re.fullmatch(r'arn:aws:iam::[0-9]{12}:role/queuemate-github-frontend-deploy', values['AWS_ROLE_ARN'])
    oidc = api('actions/oidc/customization/sub')
    assert oidc.get('use_immutable_subject') and oidc['sub_claim_prefix'] == 'repo:Port-051@293762925/QueueMate@1350658456', 'AWS trust must match current repository OIDC subject'
    variables('production-app', values)
if args.protect_main:
    api('branches/main/protection', 'PUT', {
        'required_status_checks':{'strict':True,'checks':[{'context':'검증 완료','app_id':15368}]},
        'enforce_admins':True,
        'required_pull_request_reviews':{'dismiss_stale_reviews':True,'require_code_owner_reviews':False,'required_approving_review_count':0},
        'restrictions':None,'required_conversation_resolution':True,'allow_force_pushes':False,'allow_deletions':False,
    })
print('Configured main-only deployment environments and non-secret variables.')
