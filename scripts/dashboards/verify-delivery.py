"""Read-only smoke checks against the deployed shared Nginx; no fixture mutations."""
import json
import re
from urllib.request import urlopen

base='http://127.0.0.1:8091'
def get(path):
    with urlopen(base+path,timeout=15) as response:
        return response.read().decode()

checks=[]
for query,total in [('',12),('&target=gnm',5),('&target=gnm&applid=CORE&severity=5',1),('&state=2',2),('&severity=any',2),('&applid=__ANY__',1)]:
    d=json.loads(get('/api/dashboards/delivery?customer=DEMO-DASHBOARDS'+query))
    if d['source']!='postgresql' or d['total']!=total:
        raise RuntimeError('Delivery result mismatch: '+query)
    if sum(b['count'] for b in d['facets']['applids'])!=total:
        raise RuntimeError('APPLID facet total mismatch')
    checks.append({'query':query or 'all-demo','total':d['total']})
html=get('/dashboards/delivery')
asset=re.search(r'<script[^>]+src="([^"]+)"',html).group(1)
javascript=get(asset)
for marker in ('/dashboards/delivery','Delivery configuration','Registered filters','oem.language'):
    if marker not in javascript: raise RuntimeError('Deployed frontend missing '+marker)
with urlopen('http://127.0.0.1:8090/health',timeout=5) as response:
    console=json.load(response)
if console['status']!='UP':raise RuntimeError('Console health failed')
print(json.dumps({'status':'PASS','frontendAsset':asset,'delivery':checks,'console':console['status']},indent=2))
