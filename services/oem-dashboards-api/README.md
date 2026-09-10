# OEM Dashboards API

BFF de consulta read-only. `dashboard.py` contiene el puerto/adaptadores;
`server.py` entrega HTTP; `cli.py` administra las fuentes por dominio.
Python 3.12, psycopg 3.2.9. El navegador nunca accede directamente a PostgreSQL.

Ver [contratos](../../docs/dashboards/contracts.md) y
[runbook](../../docs/dashboards/operational-runbook.md). Liveness y readiness son
endpoints distintos. Servidor foundation para laboratorio, con Nginx delante.
