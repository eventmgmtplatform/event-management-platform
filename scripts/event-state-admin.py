#!/usr/bin/env python3
"""ESS read administration. Credentials stay in the local protected directory."""
import argparse,json,os,secrets,urllib.request,urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CREDENTIALS=ROOT/'.local/ess-admin/tokens.json'

def configure():
    CREDENTIALS.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    CREDENTIALS.parent.chmod(0o700)
    if not CREDENTIALS.exists():
        with CREDENTIALS.open('x') as stream:
            json.dump({'tenants':{t:secrets.token_urlsafe(32) for t in ('os11-synthetic','vit')},'operatorToken':secrets.token_urlsafe(32)},stream)
        # Parent directory is private; mounted file must be readable by container UID 1001.
        CREDENTIALS.chmod(0o644)

def request(action,tenant='os11-synthetic',key=None,token_override=None):
    credentials=json.loads(CREDENTIALS.read_text())
    token=credentials['operatorToken'] if action=='quarantine' else credentials['tenants'][tenant]
    url='http://127.0.0.1:8084/api/v1/state/'+action
    if key is not None:url+='?'+urllib.parse.urlencode({'eventKey':key})
    req=urllib.request.Request(url,headers={'X-ESS-Admin-Token':token if token_override is None else token_override,'X-Tenant-Id':tenant})
    with urllib.request.urlopen(req,timeout=10) as response:return json.load(response)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['configure','events','event','history','quarantine'],nargs='?',default='events')
    parser.add_argument('--tenant',default=os.environ.get('ESS_ADMIN_TENANT','os11-synthetic'))
    parser.add_argument('--event-key')
    args=parser.parse_args()
    if args.action=='configure':configure();print('ESS local credentials configured (values not displayed)');return
    if args.action in ('event','history') and not args.event_key:parser.error('--event-key required')
    print(json.dumps(request(args.action,args.tenant,args.event_key),ensure_ascii=False,indent=2))
if __name__=='__main__':main()
