# Informe de pruebas — v1.0.0

Fecha: 2026-11-09

| Caso | Resultado | Notas |
|------|-----------|-------|
| App instala y abre | Pendiente dispositivo | Scaffold completo |
| FGS + notificación | Pendiente dispositivo | `AssistantForegroundService` |
| GET /health en LAN | Pendiente dispositivo | NanoHTTPD :3002 |
| Impresión BT prueba | Pendiente impresora real | EscPosBuilder + SPP |
| Impresión Wi‑Fi | Pendiente impresora real | TCP 9100 |
| Poll billing pending-local | Pendiente entorno beta | Misma cola que Windows |
| Boleta beta SUNAT | Pendiente cert + SOL | UBL EXO/INA/IGV |
| Cola print-jobs POS | Implementado servidor | Requiere APK + JWT |

Known issues:

- OEM agresivos pueden matar FGS; pedir exclusión de batería.
- USB OTG requiere aceptar permiso en cada sesión.
- Firma XML depende de Santuario; validar con validador SUNAT antes de producción.
