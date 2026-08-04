# OS_08_10 Cloud Storage GCP — Scripts

Ubicación recomendada en el repositorio:

```text
infrastructure/gcp/terraform/scripts/os-08-10/
├── 00-common.sh
├── 01-prerequisites-and-inventory.sh
├── 02-remediation-gate.sh
├── 03-terraform-execution.sh
├── 04-final-certification.sh
└── README.md
```

Evidencias:

```text
evidence/os-08-10/
├── 01-prerequisites-and-inventory/
├── 02-remediation/
├── 03-execution/
└── 04-final-certification/
```

## Orden de ejecución

```bash
cd /opt/event-management-platform

bash infrastructure/gcp/terraform/scripts/os-08-10/01-prerequisites-and-inventory.sh
bash infrastructure/gcp/terraform/scripts/os-08-10/02-remediation-gate.sh
```

Después de implementar y revisar `modules/cloud-storage` y
`environments/dev/cloud-storage.tf`:

```bash
bash infrastructure/gcp/terraform/scripts/os-08-10/03-terraform-execution.sh
```

El primer intento genera el plan y se detiene. Después de revisarlo:

```bash
OS_08_10_APPROVE_APPLY=YES bash infrastructure/gcp/terraform/scripts/os-08-10/03-terraform-execution.sh
```

Finalmente:

```bash
bash infrastructure/gcp/terraform/scripts/os-08-10/04-final-certification.sh
```

## Seguridad operativa

- No habilita APIs manualmente.
- No concede roles IAM manualmente.
- No modifica automáticamente módulos certificados.
- La remediación es una compuerta de diagnóstico.
- El apply requiere aprobación explícita mediante `OS_08_10_APPROVE_APPLY=YES`.
- El plan final debe devolver `No changes`.
