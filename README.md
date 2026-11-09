# Fadey Mobile Assistant

Asistente Android (Java) de impresión ESC/POS y facturación electrónica SUNAT para **Resto Fadey POS**.

Complementa (no reemplaza) el asistente Windows (`Fadey.Impresion.Setup.exe`).

## Capacidades

- Servicio en primer plano + HTTP local en `0.0.0.0:3002` (mismo contrato `/health`, `/api/printing/*`, `/api/efact/*`).
- Impresión Bluetooth SPP, Wi‑Fi TCP y USB OTG.
- Motor e-fact Java (UBL 2.1, firma XML-DSig, SOAP SUNAT beta/prod).
- Poll en nube: `billing/pending-local` y `print-jobs/pending`.

## Requisitos

- Android 8.0+ (API 26), recomendado con Bluetooth Classic y/o Wi‑Fi.
- Token JWT de cajero/admin del restaurante + URL API.
- Certificado `.pfx` del emisor (importado en la app o vía `pfx_base64` del sale).

## Build

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/`.

## Documentación

- [Auditoría Windows](docs/AUDITORIA_ASISTENTE_WINDOWS.md)
- [Instalación](docs/MANUAL_INSTALACION.md)
- [Impresoras](docs/MANUAL_IMPRESORAS.md)
- [Integración POS](docs/MANUAL_INTEGRACION_POS.md)
- [Informe de pruebas](docs/INFORME_PRUEBAS.md)

## Releases

GitHub Releases del repo `Fadey-Solutions-SAC/Fadey-Mobile-Assistant` (asset `Fadey-Mobile-Assistant.apk` cuando CI firme).

Plantilla CI: `docs/ci/android-release.yml` → copiar a `.github/workflows/` con un token que tenga scope `workflow` y secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
