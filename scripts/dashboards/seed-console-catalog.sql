\set ON_ERROR_STOP on
BEGIN;
INSERT INTO event_management.customer_configuration(customer_code,customer,bamid,gnmorgid,snow_company_id,gnm_assignment_group,snow_assignment_group,cacf_assignment_group,chatops_team,aiops_extension,origin)
VALUES ('DEMO-CONSOLE-RETAIL','Retail • Demostración','DEMO-BAM-001','DEMO-GNM-001','DEMO-SNOW-001','DEMO-RETAIL-NOC','DEMO-RETAIL-SERVICE-DESK','DEMO-RETAIL-AUTOMATION','DEMO-RETAIL-TEAMS','DEMO-AIOPS-RETAIL','demo'),
('DEMO-CONSOLE-BANK','Banca • Demostración','DEMO-BAM-002','DEMO-GNM-002','DEMO-SNOW-002','DEMO-BANK-NOC','DEMO-BANK-SERVICE-DESK','DEMO-BANK-AUTOMATION','DEMO-BANK-TEAMS','DEMO-AIOPS-BANK','demo')
ON CONFLICT DO NOTHING;
WITH created_filters AS (
INSERT INTO event_management.delivery_filter(filter_id,name,description,customer_code,applid,filter_state,filter_weight,severities,criteria,origin)
SELECT 'demo-console:'||n, (ARRAY['Disponibilidad de tiendas','Pagos degradados','Capacidad de base de datos','Latencia de sucursales','Respaldo fallido','Certificado por vencer','Errores de aplicación','Infraestructura crítica'])[n],
 'Ejemplo de configuración; destinos ficticios. No activa integraciones.',CASE WHEN n<=4 THEN 'DEMO-CONSOLE-RETAIL' ELSE 'DEMO-CONSOLE-BANK' END,
 CASE WHEN n<=4 THEN 'RETAIL' ELSE 'BANK' END,1,n*10,ARRAY[4,5]::smallint[],jsonb_build_object('Service',jsonb_build_object('operator','eq_ci','value',CASE WHEN n<=4 THEN 'retail' ELSE 'banking' END)),'demo'
FROM generate_series(1,8) n ON CONFLICT DO NOTHING RETURNING filter_id,customer_code
)
INSERT INTO event_management.delivery_filter_target(filter_id,target,assignment_group,action_reference,depends_on_ticketing)
SELECT f.filter_id,t,CASE WHEN t='extensions' THEN NULL ELSE 'DEMO-'||upper(t)||'-'||CASE WHEN f.customer_code LIKE '%RETAIL' THEN 'RETAIL' ELSE 'BANK' END END,
 CASE WHEN t='extensions' THEN 'DEMO-AIOPS-EXTENSION' ELSE NULL END,t IN ('gnm','cacf','chatops')
FROM created_filters f CROSS JOIN unnest(ARRAY['gnm','snow','cacf','chatops','extensions']) t
WHERE f.filter_id LIKE 'demo-console:%' ON CONFLICT DO NOTHING;
COMMIT;
