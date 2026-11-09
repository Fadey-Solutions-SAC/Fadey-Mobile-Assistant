# Manual de instalación — Fadey Mobile Assistant

1. Descargue el APK desde GitHub Releases (`Fadey-Mobile-Assistant.apk`) o desde Configuración → Impresión en el POS (botón **Asistente Android**).
2. En Android permita “Instalar apps desconocidas” para el navegador/gestor de archivos.
3. Abra la app y acepte notificaciones / Bluetooth.
4. En **Configuración**, pegue la API base del restaurante (ej. `https://su-servicio.onrender.com/api`) y el JWT de un usuario cajero/admin.
5. Pulse **Iniciar servicio** (o deje que arranque solo). Debe quedar una notificación persistente.
6. Opcional LAN: en el POS use “Vincular manual” con `http://IP_DEL_TELEFONO:3002`.
7. En **Facturación**, importe el `.pfx` y su contraseña (almacenamiento cifrado).
