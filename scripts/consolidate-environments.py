#!/usr/bin/env python3
"""Archive and stop selected duplicate runtimes; retain every container and volume."""
import datetime,hashlib,json,subprocess,tarfile,urllib.request,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PROJECTS={'os11-lifecycle','cacf-certification','em-kafka-candidate-20260910','em-kafka-candidate-20260910-r2'}

def main():
    output=ROOT/'evidences/environment-consolidation'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output.mkdir(parents=True,mode=0o700)
    report={'status':'RUNNING','containers':[],'databases':[],'volumes':[]}
    stopped=[]
    def run(args,data=None,timeout=120):
        return subprocess.run(args,cwd=ROOT,input=data,text=True,capture_output=True,check=True,timeout=timeout).stdout.strip()
    def private(path,data):
        path.write_bytes(data);path.chmod(0o600)
    try:
        ids=run(['docker','ps','-aq']).splitlines()
        inspected=json.loads(run(['docker','inspect',*ids]))
        selected=[c for c in inspected if (c['Config'].get('Labels') or {}).get('com.docker.compose.project') in PROJECTS or c['Name']=='/ess-cert-postgres']
        if not selected:raise RuntimeError('NO_MATCHING_LABS')
        private(output/'containers.private.json',json.dumps(selected,indent=2).encode())
        for c in selected:
            labels=c['Config'].get('Labels') or {};name=c['Name'].lstrip('/');service=labels.get('com.docker.compose.service','postgres')
            report['containers'].append({'name':name,'project':labels.get('com.docker.compose.project'),'service':service,'wasRunning':c['State']['Running'],'image':c['Image']})
        # Quiesce producers/consumers first, leaving databases and mocks available for export.
        for c in selected:
            service=(c['Config'].get('Labels') or {}).get('com.docker.compose.service','postgres')
            if c['State']['Running'] and service in {'gateway','processor','worker','ess','integration-worker'}:
                run(['docker','stop','--time','60',c['Id']]);stopped.append(c['Id'])
        print('Laboratory consumers stopped; exporting mock contracts and databases',flush=True)
        for c in selected:
            name=c['Name'].lstrip('/');service=(c['Config'].get('Labels') or {}).get('com.docker.compose.service','postgres')
            if 'mock' in service and c['State']['Running']:
                bindings=c['NetworkSettings']['Ports'].get('8080/tcp') or []
                if bindings:
                    for resource in ['mappings','scenarios','requests']:
                        with urllib.request.urlopen('http://127.0.0.1:'+bindings[0]['HostPort']+'/__admin/'+resource,timeout=20) as response:
                            private(output/(name+'-'+resource+'.json'),response.read())
            if service!='postgres' or not c['State']['Running']:continue
            def sql(query,db='postgres'):
                return run(['docker','exec','-i','-e','CONSOLIDATION_DB='+db,name,'sh','-c','psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$CONSOLIDATION_DB"'],query)
            databases=sql("SELECT datname FROM pg_database WHERE NOT datistemplate AND datname<>'postgres' ORDER BY 1").splitlines()
            counts="SELECT format('SELECT %L, count(*) FROM %I.%I',schemaname||'.'||tablename,schemaname,tablename) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY schemaname,tablename;\n\\gexec\n"
            for index,db in enumerate(databases):
                # DB identifiers are catalog data; pass to tools as arguments/environment, never shell interpolation.
                backup=output/(name+'-'+str(index)+'.dump')
                with backup.open('wb') as stream:
                    backup.chmod(0o600)
                    subprocess.run(['docker','exec','-e','CONSOLIDATION_DB='+db,name,'sh','-c','pg_dump -U "$POSTGRES_USER" -d "$CONSOLIDATION_DB" -Fc --no-owner --no-privileges'],stdout=stream,stderr=subprocess.PIPE,check=True,timeout=180)
                original=sql(counts,db)
                restore='consolidation_restore_'+uuid.uuid4().hex
                sql('CREATE DATABASE '+restore)
                try:
                    with backup.open('rb') as stream:
                        subprocess.run(['docker','exec','-i','-e','CONSOLIDATION_DB='+restore,name,'sh','-c','pg_restore --exit-on-error --no-owner --no-privileges -U "$POSTGRES_USER" -d "$CONSOLIDATION_DB"'],stdin=stream,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,check=True,timeout=180)
                    if sql(counts,restore)!=original:raise RuntimeError('RESTORE_ROW_COUNTS_DIFFER')
                    report['databases'].append({'container':name,'database':db,'backup':backup.name,'restore':'PASS','tableCounts':original.splitlines()})
                finally:sql('DROP DATABASE '+restore)
        for c in selected:
            if c['State']['Running'] and c['Id'] not in stopped:
                run(['docker','stop','--time','60',c['Id']]);stopped.append(c['Id'])
        print('Duplicate runtimes stopped; archiving persistent volumes',flush=True)
        sources=sorted({m['Source'] for c in selected for m in c.get('Mounts',[]) if m['Type']=='bind'})
        with tarfile.open(output/'bind-sources.tar.gz','w:gz') as archive:
            for index,source in enumerate(sources):
                path=Path(source)
                if path.exists():archive.add(path,arcname='bind-'+str(index),recursive=True)
        report['bindSources']=[{'source':source,'member':'bind-'+str(index)} for index,source in enumerate(sources)]

        volumes=sorted({m['Name'] for c in selected for m in c.get('Mounts',[]) if m['Type']=='volume'})
        for index,volume in enumerate(volumes):
            filename='volume-'+str(index)+'.tar.gz'
            run(['docker','run','--rm','--network','none','--user','0','--entrypoint','tar','-v',volume+':/source:ro','-v',str(output)+':/archive','postgres:17','-czf','/archive/'+filename,'-C','/source','.'],timeout=300)
            # Read every member to detect truncated archives, not just the table of contents.
            run(['docker','run','--rm','--network','none','--entrypoint','tar','-v',str(output)+':/archive:ro','postgres:17','-tzf','/archive/'+filename],timeout=120)
            report['volumes'].append({'volume':volume,'archive':filename,'readable':True})
        now=json.loads(run(['docker','inspect',*[c['Id'] for c in selected]]))
        if any(c['State']['Running'] for c in now):raise RuntimeError('DUPLICATE_STILL_RUNNING')
        report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',errorType=type(error).__name__,stopped=stopped)
    finally:
        report['retention']='No containers or original volumes deleted. No laboratory records or Kafka offsets merged into shared data.'
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        # Volume archives can be root-owned; compute checksum in a read-only Docker mount.
        try:
            sums=run(['docker','run','--rm','--network','none','--entrypoint','sh','-v',str(output)+':/archive:ro','postgres:17','-c','cd /archive && sha256sum *.dump *.json *.tar.gz'],timeout=120)
            (output/'SHA256SUMS').write_text(sums+'\n')
        except Exception:report['status']='FAIL';report['checksumStatus']='FAILED';(output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps({'status':report['status'],'evidence':str(output/'report.json')}))
    return 0 if report['status']=='PASS' else 1
if __name__=='__main__':raise SystemExit(main())
