"""Read-only configuration analytics. Counts are distinct filters, never event deliveries."""
import os
from datetime import datetime, timezone
from dashboard import DashboardError

TARGETS = ('gnm','snow','cacf','chatops','extensions')


def parse_delivery_query(params):
    allowed = {'target','applid','customer','state','severity','q','page','limit'}
    if set(params) - allowed or any(len(v) != 1 for v in params.values()):
        raise DashboardError('Invalid Delivery filters.')
    q = {k: params.get(k, [''])[0].strip() for k in allowed - {'page','limit'}}
    if any(len(v) > 128 for v in q.values()):
        raise DashboardError('Delivery filter too long.')
    if q['target'] not in ('',*TARGETS) or q['state'] not in ('','0','1','2') or q['severity'] not in ('','0','1','2','3','4','5','any'):
        raise DashboardError('Unsupported Delivery selection.')
    try:
        q.update(page=int(params.get('page',['1'])[0]), limit=int(params.get('limit',['25'])[0]))
    except ValueError as exc:
        raise DashboardError('Invalid pagination.') from exc
    if not 1 <= q['page'] <= 100000 or not 1 <= q['limit'] <= 100:
        raise DashboardError('Invalid pagination.')
    return q


def validate_delivery(data, query):
    from dashboard import timestamp
    def require(value):
        if not value: raise DashboardError('Invalid Delivery API contract.')
    def count(value): return type(value) is int and value >= 0
    try:
        require(data['schemaVersion'] == '1.0' and data['domain'] == 'delivery')
        require(timestamp(data['observedAt']) and (data['lastUpdatedAt'] is None or timestamp(data['lastUpdatedAt'])))
        require(count(data['total']) and type(data['page']) is int and type(data['limit']) is int and data['page'] == query['page'] and data['limit'] == query['limit'])
        require(set(data['facets']) == {'targets','applids','severities','states'})
        for facet in data['facets'].values():
            require(isinstance(facet,list) and all(isinstance(b['value'],str) and count(b['count']) and b['count'] <= data['total'] for b in facet))
        require(sum(b['count'] for b in data['facets']['states']) == data['total'])
        require(sum(b['count'] for b in data['facets']['applids']) == data['total'])
        rows = data['rows']
        require(isinstance(rows,list) and len(rows) == min(query['limit'],max(0,data['total']-(query['page']-1)*query['limit'])))
        for row in rows:
            require(all(isinstance(row[k],str) and row[k] for k in ('id','name','customerCode','origin')))
            require(row['applid'] is None or isinstance(row['applid'],str))
            require(type(row['state']) is int and row['state'] in (0,1,2) and type(row['weight']) is int)
            require(row['severities'] is None or (isinstance(row['severities'],list) and all(type(s) is int and s in range(6) for s in row['severities'])))
            require(isinstance(row['criteria'],dict) and timestamp(row['updatedAt']))
            require(isinstance(row['targets'],list) and all(t['target'] in TARGETS and t['behavior'] in ('enable','force_off','overlay') for t in row['targets']))
        require(len({r['id'] for r in rows}) == len(rows))
        return {k:data[k] for k in ('schemaVersion','domain','observedAt','lastUpdatedAt','total','page','limit','facets','rows')}
    except (KeyError,TypeError,ValueError) as exc:
        raise DashboardError('Invalid Delivery API contract.') from exc


def load_delivery(query):
    try:
        import psycopg
        from psycopg.rows import dict_row
        dsn = os.getenv('OEM_POSTGRES_DSN')
        if not dsn: raise DashboardError('PostgreSQL is not configured.')
        terms, args = [], []
        if query['target']:
            terms.append("EXISTS (SELECT 1 FROM jsonb_array_elements(targets) t WHERE t->>'target'=%s)")
            args.append(query['target'])
        if query['applid']:
            if query['applid'] == '__ANY__': terms.append('applid IS NULL')
            else:
                terms.append('lower(applid)=lower(%s)'); args.append(query['applid'])
        if query['customer']:
            terms.append('customer_code=%s'); args.append(query['customer'])
        if query['state']:
            terms.append('filter_state=%s'); args.append(int(query['state']))
        if query['severity']:
            if query['severity'] == 'any': terms.append('severities IS NULL')
            else:
                terms.append('(severities IS NULL OR %s=ANY(severities))'); args.append(int(query['severity']))
        if query['q']:
            terms.append("strpos(lower(filter_id || ' ' || name || ' ' || description),lower(%s))>0")
            args.append(query['q'])
        where = ' WHERE ' + ' AND '.join(terms) if terms else ''
        base = 'WITH matched AS (SELECT * FROM dashboard_read.delivery' + where + ') '
        with psycopg.connect(dsn, connect_timeout=3, row_factory=dict_row) as conn:
            with conn.cursor() as cur:
                cur.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY')
                cur.execute("SET LOCAL statement_timeout='4000ms'")
                cur.execute(base + 'SELECT count(*) AS total,max(updated_at) AS updated FROM matched',args)
                totals = cur.fetchone()
                facets={}
                queries={
                    'targets':"SELECT t->>'target' AS value,count(DISTINCT filter_id) AS count FROM matched CROSS JOIN LATERAL jsonb_array_elements(targets) t GROUP BY 1",
                    'applids':"SELECT coalesce(lower(applid),'__ANY__') AS value,count(*) AS count FROM matched GROUP BY 1",
                    'severities':"SELECT coalesce(s::text,'any') AS value,count(DISTINCT filter_id) AS count FROM matched LEFT JOIN LATERAL unnest(severities) s ON true GROUP BY 1",
                    'states':"SELECT filter_state::text AS value,count(*) AS count FROM matched GROUP BY 1",
                }
                for name,sql in queries.items():
                    cur.execute(base+sql+' ORDER BY count DESC,value',args)
                    facets[name]=cur.fetchall()
                cur.execute(base+'SELECT * FROM matched ORDER BY filter_weight DESC,filter_id LIMIT %s OFFSET %s',args+[query['limit'],(query['page']-1)*query['limit']])
                records=cur.fetchall()
        rows=[{'id':r['filter_id'],'name':r['name'],'description':r['description'],'customerCode':r['customer_code'],
               'applid':r['applid'],'state':r['filter_state'],'weight':r['filter_weight'],'severities':r['severities'],
               'criteria':r['criteria'],'origin':r['origin'],'legacyFilterId':r['legacy_filter_id'],
               'updatedAt':r['updated_at'].isoformat(),'targets':r['targets']} for r in records]
        return {'schemaVersion':'1.0','domain':'delivery','observedAt':datetime.now(timezone.utc).isoformat(),
                'lastUpdatedAt':totals['updated'].isoformat() if totals['updated'] else None,
                'total':totals['total'],'page':query['page'],'limit':query['limit'],'facets':facets,'rows':rows}
    except DashboardError: raise
    except Exception as exc:
        raise DashboardError('Delivery PostgreSQL unavailable. Check migration 022 and reader permissions.') from exc
