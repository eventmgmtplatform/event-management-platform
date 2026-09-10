"""Read models for product dashboards. Runtime rules keep their existing publication API."""
QUERIES={
 'inventory-services':"SELECT resource_id::text AS id,tenant,resource_name AS name,enabled,resource_type,ip_address::text,customer_code,attributes,updated_at FROM event_management.inventory_resource ORDER BY tenant,resource_name LIMIT 2001",
 'aiops-extensions':"SELECT id,tenant,name,enabled,revision FROM event_processor.aiops_configuration WHERE NOT deleted ORDER BY tenant,id LIMIT 2001",
}
RULE_SQL="""SELECT d.rule_id AS id,d.tenant,COALESCE(v.definition->>'name',d.rule_id) AS name,
 d.status='ENABLED' AS enabled,d.status,d.active_version,d.latest_version,d.revision,
 v.definition AS configuration FROM event_processor.rule_definition d JOIN event_processor.rule_version v
 ON v.tenant=d.tenant AND v.rule_id=d.rule_id AND v.version=COALESCE(d.active_version,d.latest_version)
 WHERE v.definition->>'type'=ANY(%s) ORDER BY d.tenant,d.rule_id LIMIT 2001"""
# Blackout is a capability; its stored type is the window mode.
RULE_TYPES={'blackouts':['SCHEDULED','IMMEDIATE'],'policies':['POLICY'],'auto-suppression':['SUPPRESSION']}
def snapshot(conn,kind):
    if kind in QUERIES: rows=conn.execute(QUERIES[kind]).fetchall()
    elif kind in RULE_TYPES:
        rows=conn.execute(RULE_SQL,(RULE_TYPES[kind],)).fetchall()
        for row in rows: row['source']='Event Processor'
        if kind=='blackouts':
            legacy=conn.execute("SELECT blackout_id::text AS id,tenant,COALESCE(description,change_number,'Blackout') AS name,enabled,start_at,end_at,resource_pattern,summary_pattern,change_number FROM event_management.blackout ORDER BY start_at DESC LIMIT 2001").fetchall()
            rows += [dict(row,source='Catálogo PostgreSQL') for row in legacy]
        elif kind=='policies':
            legacy=conn.execute("SELECT policy_id::text AS id,tenant,policy_name AS name,enabled,priority,match_condition,actions,updated_at FROM event_management.event_policy ORDER BY tenant,priority LIMIT 2001").fetchall()
            rows += [dict(row,source='Catálogo PostgreSQL') for row in legacy]
    else: return None
    return {'items':rows[:2000],'truncated':len(rows)>2000,'mode':'read_only'}
