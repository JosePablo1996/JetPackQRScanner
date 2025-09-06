Jetpack QR Scanner
Esta es una aplicación simple de escáner de códigos QR construida con Kotlin, Jetpack Compose y la API de CameraX, utilizando Google ML Kit para el reconocimiento de códigos de barras.

Características
Escáner de código QR en tiempo real.

Manejo de permisos de cámara de forma segura y moderna.

Feedback visual para la detección del código.

Permisos
La aplicación requiere permiso para acceder a la cámara del dispositivo. La lógica de permisos se maneja automáticamente y solicita el acceso al usuario cuando es necesario. Si el permiso es denegado, la aplicación guía al usuario a la configuración del sistema para habilitarlo manualmente.

Componentes principales
ScannerActivity.kt: La actividad principal que maneja la lógica de la cámara, el análisis de la imagen y la detección de códigos QR.

activity_scanner.xml: El archivo de diseño (layout) que contiene la vista previa de la cámara.

Dependencias
Asegúrate de tener las siguientes dependencias en tu archivo build.gradle (Module: app):

// CameraX
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// ML Kit Barcode Scanning
implementation("com.google.mlkit:barcode-scanning:17.2.0")

// AppCompat and Activity KTX
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("androidx.activity:activity-ktx:1.8.2")

Uso
Abre la aplicación.

La aplicación solicitará permiso para usar la cámara (si es la primera vez).

Apunta la cámara a un código QR.

La aplicación detectará y procesará el código automáticamente.

Una vez que un código es detectado, la actividad de escaneo se cerrará y devolverá el resultado a la actividad anterior (por ejemplo, MainActivity.kt).

Contribuciones
Si deseas contribuir a este proyecto, por favor, haz un "fork" del repositorio, crea una nueva rama para tus cambios y envía un "pull request".