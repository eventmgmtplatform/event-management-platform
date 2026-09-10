"""Generate a reproducible native OpenSearch dashboard and its dependencies."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
objects = []
PATTERN = 'product-observability-pattern'
def add(kind, ident, attributes, refs=None):
    objects.append(dict(type=kind, id=ident, attributes=attributes, references=refs or []))
def source(query):
    return {'query':{'language':'kuery','query':query},'filter':[], 'indexRefName':'kibanaSavedObjectMeta.searchSourceJSON.index'}
ref = [{'name':'kibanaSavedObjectMeta.searchSourceJSON.index','type':'index-pattern','id':PATTERN}]
add('index-pattern', PATTERN, {'title':'product-observability-current','timeFieldName':'@timestamp'})
panels=[]
def panel(kind, ident, x,y,w,h):
    idx=str(len(panels)+1)
    panels.append({'version':'7.9.3','panelIndex':idx,'type':kind,'gridData':{'x':x,'y':y,'w':w,'h':h,'i':idx},'embeddableConfig':{},'panelRefName':'panel_'+idx})
    return {'name':'panel_'+idx,'type':kind,'id':ident}
refs=[]
add('visualization','product-observability-guide',{'title':'Observabilidad del producto','visState':json.dumps({'title':'Observabilidad del producto','type':'markdown','params':{'markdown':'## Observabilidad · Event Management\nInventario y configuración publicada por el producto. Consulta cada **30 s**. Ventana de **2 minutos**: si el recolector se detiene, las observaciones desaparecen al vencer.\n**UP** indica respuesta HTTP 2xx; no garantiza salud funcional. La salud del contenedor se muestra por separado. Mocks identificados por su nombre. Sin historial; se conserva la última observación por servicio/API.\nSi una fuente falla, revisa **Fuentes de telemetría**; sus datos anteriores pueden permanecer hasta 2 minutos.','fontSize':12},'aggs':[]}), 'uiStateJSON':'{}','kibanaSavedObjectMeta':{'searchSourceJSON':'{}'}})
refs.append(panel('visualization','product-observability-guide',0,0,48,8))
for ident,title,query,x in [('apis','APIs observadas','kind:api',0),('up','APIs con respuesta 2xx','kind:api AND status:UP',16),('attention','APIs sin respuesta correcta','kind:api AND NOT status:UP',32)]:
    vid='product-observability-'+ident
    add('visualization',vid,{'title':title,'visState':json.dumps({'title':title,'type':'metric','params':{'addTooltip':True,'addLegend':False,'metric':{'style':{'fontSize':40},'labels':{'show':True}}},'aggs':[{'id':'1','enabled':True,'type':'count','schema':'metric','params':{'customLabel':title}}]}),'uiStateJSON':'{}','kibanaSavedObjectMeta':{'searchSourceJSON':json.dumps(source(query))}},ref)
    refs.append(panel('visualization',vid,x,8,16,7))
for ident,title,query,columns,y,h in [
    ('apis-table','Estado y latencia de APIs','kind:api',['service','status','httpStatus','latencyMs','endpoint','checkedAt'],15,15),
    ('services','Configuración e inventario del producto','kind:service',['name','category','version','port','runtime','health','status','restartCount'],30,18),
    ('sources','Fuentes de telemetría','kind:collector',['id','status','@timestamp'],48,8)]:
    sid='product-observability-'+ident
    add('search',sid,{'title':title,'description':'Última observación real del producto','columns':columns,'sort':[['@timestamp','desc']],'kibanaSavedObjectMeta':{'searchSourceJSON':json.dumps(source(query))}},ref)
    refs.append(panel('search',sid,0,y,48,h))
add('dashboard','product-observability',{'title':'Event Management · Observabilidad del producto','description':'Salud HTTP, inventario y configuración disponible del producto.','panelsJSON':json.dumps(panels),'optionsJSON':json.dumps({'useMargins':True,'hidePanelTitles':False}),'version':1,'timeRestore':True,'timeFrom':'now-2m','timeTo':'now','refreshInterval':{'pause':False,'value':30000},'kibanaSavedObjectMeta':{'searchSourceJSON':json.dumps({'query':{'language':'kuery','query':''},'filter':[]})}},refs)
output=ROOT/'infrastructure/opensearch/product-observability.ndjson'
output.write_text(''.join(json.dumps(o,ensure_ascii=False)+'\n' for o in objects))
print(output)
