Jetpack QR Scanner 📱
Esta es una aplicación simple para escanear códigos QR, construida con Kotlin, el framework Jetpack Compose, la API de CameraX y la librería Google ML Kit para el reconocimiento de códigos de barras.

✨ Características clave
.Escáner en tiempo real: Captura y procesa códigos QR al instante.

.Manejo de permisos: Solicita y gestiona los permisos de la cámara de manera segura y moderna. Si el permiso es denegado, la aplicación guía al usuario a la configuración del sistema.

.Diseño intuitivo: Interfaz de usuario limpia y fácil de usar.

.Feedback visual: La app reacciona cuando detecta un código, mejorando la experiencia de usuario.

🛠️ Requisitos y Dependencias
Para compilar y ejecutar este proyecto, necesitas las siguientes dependencias en tu archivo build.gradle (Module: app):

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

🚀 Uso
1) Abre la aplicación.

2) La app pedirá permiso para usar la cámara (si es la primera vez).

3) Apunta la cámara a un código QR para escanearlo.

4) La app procesará el código automáticamente.

5) Una vez detectado, la actividad del escáner se cerrará y devolverá el resultado a la actividad principal, por ejemplo, MainActivity.kt.

🤝 Contribuciones
¡Tu colaboración es bienvenida! Si deseas mejorar este proyecto, sigue estos pasos:

Haz un "fork" de este repositorio.

Crea una nueva rama (git checkout -b feature/nueva-funcionalidad).

Haz tus cambios y haz "commit" de ellos (git commit -m 'feat: se añade nueva funcionalidad').

Sube tu rama al repositorio remoto (git push origin feature/nueva-funcionalidad).

Envía un "Pull Request".