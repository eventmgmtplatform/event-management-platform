"""Read-only product view of the fixed local Kafka cluster via existing Kafbat API."""
import datetime,json,urllib.request
CLUSTER='event-management-local'
PRODUCT=[
 ('events.raw','Entrada de eventos','Event Gateway → Event Processor',3),
 ('events.normalized','Eventos normalizados','Event Processor → consumidores de eventos',3),
 ('integration.commands','Comandos de integración','Event Processor → Integration Worker',6),
 ('integration.results','Resultados de integración','Integration Worker → Event Processor / Event State Service',6),
 ('integration.callbacks','Callbacks de proveedores','Proveedores → procesamiento de callbacks',3),
 ('events.lifecycle','Ciclo de vida','Event Processor → Event State Service',3),
 ('events.state.requested','Solicitudes de estado','Event Processor → Event State Service',3),
 ('event.journal','Bitácora de eventos','Registro de transiciones y trazabilidad',3),
 ('events.dlq','Eventos con error','Mensajes que requieren diagnóstico',1),
]
def read(path):
    with urllib.request.urlopen('http://kafka-ui:8080/api/clusters'+path,timeout=5) as response:
        return json.load(response)
def snapshot():
    clusters=read(''); cluster=next((x for x in clusters if x['name']==CLUSTER),None)
    if not cluster: raise ValueError('cluster_not_found')
    payload=read('/'+CLUSTER+'/topics?page=1&perPage=100&showInternal=false')
    topics=payload['topics']
    for page in range(2,min(payload.get('pageCount',1),20)+1):
        topics+=read('/'+CLUSTER+f'/topics?page={page}&perPage=100&showInternal=false')['topics']
    by_name={x['name']:x for x in topics}
    def item(name,label,flow,partitions):
        actual=by_name.get(name)
        status='missing' if not actual else 'error' if actual.get('underReplicatedPartitions',0)>0 else 'drift' if partitions and actual.get('partitionCount')!=partitions else 'healthy'
        return dict(name=name,label=label,flow=flow,expectedPartitions=partitions,status=status,partitions=actual.get('partitionCount') if actual else None,replicationFactor=actual.get('replicationFactor') if actual else None,messages=actual.get('messagesCount') if actual else None,cleanupPolicy=actual.get('cleanUpPolicy') if actual else None)
    result=[item(*x) for x in PRODUCT]
    result += [item(name,'Otro tópico','',None) for name in by_name if name not in {p[0] for p in PRODUCT}]
    return {'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'cluster':CLUSTER,'status':cluster['status'],'brokers':cluster.get('brokerCount'),'readOnly':cluster.get('readOnly'),'topics':result}
