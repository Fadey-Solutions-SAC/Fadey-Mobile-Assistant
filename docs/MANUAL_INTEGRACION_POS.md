# Integración con RESTO_FADEY_POS

## Detección LAN

Health compatible:

```json
{ "status":"ok", "mode":"assistant", "service":"resto-fadey-printing-assistant", "platform":"android" }
```

El POS puede guardar `resto_local_printing_api = http://IP:3002`.

## Nube (recomendado en móvil)

1. App guarda `apiBase` + JWT (o `POST /api/efact/cloud-link` desde el POS hacia el HTTP local).
2. Poll cada 5 s:
   - `GET /billing/pending-local` → claim → emitir → `local-result`
   - `GET /print-jobs/pending` → claim → imprimir → result
3. El POS encola impresión remota en `POST /api/print-jobs` cuando no hay bridge local.

Windows sigue usando localhost; no se modifica su flujo USB/red.
