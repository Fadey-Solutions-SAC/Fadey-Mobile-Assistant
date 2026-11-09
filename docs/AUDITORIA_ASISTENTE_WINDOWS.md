# Auditoría del asistente Windows (Resto Fadey POS)

Fecha: 2026-11-09  
Proyecto origen: `Fadey-Solutions-SAC/RESTO_FADEY_POS`  
Producto Windows: `Fadey.Impresion.Setup.exe` (Electron + Express + bot Python e-fact)

## 1. Resumen ejecutivo

El asistente de Windows **no** es un servicio genérico. Es:

1. **Electron** con bandeja del sistema (segundo plano al ocultar la ventana).
2. **Express** en `127.0.0.1:3002+` (puerto 3001 = API embebida del POS en el .exe).
3. **Bot Python** e-fact en `127.0.0.1:8765` (`POST /emitir`, `GET /health`).
4. Impresión **USB** vía spooler de Windows (RAW/GDI) y **red** vía TCP ESC/POS.

El POS (navegador) descubre el asistente sondeando `http://127.0.0.1:3001–3025` y guarda el origen en `localStorage` (`resto_local_printing_api`).

Para e-fact desde la nube (Render), el asistente (o la página de caja) hace poll a:

`GET /billing/pending-local` → `POST /billing/:id/claim-local` → emitir local → `POST /billing/:id/local-result`.

## 2. Instalación y segundo plano

| Aspecto | Implementación |
|---------|----------------|
| Instalador | NSIS (`electron:build` / `electron:release`) |
| Tray | `electron/main.js` — cerrar ventana = hide |
| API embebida | Puerto 3001 empaquetado |
| Bridge impresión | Puerto 3002+ (`RESTO_ASSISTANT_PORT`) |
| Bot e-fact | `resources/efact` + `resources/efact-runtime/python.exe` |

## 3. Identificación ante el POS

Health JSON esperado:

```json
{
  "status": "ok",
  "mode": "assistant",
  "port": 3002,
  "origin": "http://127.0.0.1:3002",
  "service": "resto-fadey-printing-assistant",
  "features": ["footer_image"]
}
```

No hay device-id formal en Windows. El móvil añadirá emparejamiento restaurante + JWT.

## 4. Contratos HTTP a replicar

| Método | Ruta | Rol |
|--------|------|-----|
| GET | `/health`, `/api/health`, `/api/printing/bridge` | Detección |
| GET/PUT | `/api/printing/config` | Config módulos |
| GET | `/api/printers`, `/api/printing/network-printers` | Listado |
| GET | `/api/printing/status/:module` | Estado |
| POST | `/api/printing/print/:module`, `/api/printing/test/:module` | Impresión |
| POST | `/api/efact/cloud-link` | `{ apiBase, token }` |
| POST | `/api/efact/emitir` | Proxy al motor e-fact |

Bot: `GET http://127.0.0.1:8765/health`, `POST /emitir`.

## 5. Cola anti-duplicado e-fact (servidor POS)

Estados relevantes en `electronic_documents.provider_status`: `pending`, `sending`, `accepted`, `error`, …

Flujo:

1. `pending-local` — documentos que esperan relay local.
2. `claim-local` — lock ~90 s (`local_claim` en `provider_response`).
3. Emitir con sale JSON (incluye `pfx_base64` vía `prepareRelaySale`).
4. `local-result` o `release-local` si falla antes de enviar.

## 6. Impresión

- Payload → `buildTicket` (`server/printing/escposBuilder.js`).
- Módulos: `caja`, cocina, bar, etc. en `printer-config.json`.
- USB Windows ≠ Bluetooth Android: el móvil usa ESC/POS nativo (BT / TCP / USB Host).

## 7. Facturación electrónica (Python)

Pipeline en `server/efact/`:

1. `venta_desde_dict` (afectación 10/20/30).
2. UBL (`ubl_generator.py`) — FormaPago Contado; tributos 1000/9997/9998.
3. Firma (`signer.py`).
4. SOAP SUNAT (`sunat_client.py`).
5. PDF ticket (`pdf_generator.py`).

## 8. Reutilización / adaptación / nuevo (Android)

| Pieza | Acción |
|-------|--------|
| Contratos HTTP | Reutilizar rutas/JSON; escuchar `0.0.0.0` |
| Cola billing cloud | Reutilizar APIs; poll desde FGS |
| ESC/POS builder | Port a Java |
| Bot Python | **No** embeber; motor Java (BouncyCastle + OkHttp) |
| Cert `.pfx` | Almacenamiento cifrado; nunca en el APK |
| Tray | Notificación Foreground Service |
| Detección solo localhost | URL LAN + cola print-jobs en nube |

## 9. Limitación móvil

El POS en Vercel **no** puede hablar con `127.0.0.1` del teléfono.

Estrategia dual:

1. HTTP local en el móvil (mismo contrato) para mismo dispositivo / LAN.
2. Poll cloud para e-fact (`pending-local`…).
3. Cola `print-jobs` en el POS para impresión remota sin romper Windows.

## 10. Archivos clave en RESTO_FADEY_POS

- `electron/main.js` — tray, Express, poll e-fact, impresión.
- `client/src/utils/api.js` — descubrimiento de puertos.
- `client/src/utils/efactPcRelay.js` / `efactLocalRelay.js`.
- `server/routes/billing.js` — claim/local-result.
- `server/efact/**` — motor SUNAT.
- `server/printing/escposBuilder.js` — bytes térmicos.
