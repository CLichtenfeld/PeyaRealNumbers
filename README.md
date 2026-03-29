# Peya Real Numbers 🚴‍♂️📦

**Peya Real Numbers** es una herramienta avanzada para repartidores de plataformas (como PedidosYa) que necesitan datos precisos sobre su rendimiento real en la calle. Mientras que las apps oficiales suelen mostrar distancias estimadas ("a vuelo de pájaro"), esta aplicación registra el movimiento exacto, permitiendo optimizar rutas y entender el desgaste real del vehículo y del repartidor.

---

## 🛠️ Tecnologías y por qué las elegimos

En este proyecto se implementaron soluciones técnicas específicas para resolver problemas comunes en apps de tracking:

### 1. Geolocalización de Precisión y Filtrado de Kalman
*   **Tecnología:** `Google Play Services Location` + **Filtro de Kalman Personalizado**.
*   **Por qué:** Las señales GPS suelen tener "ruido" (saltos bruscos de posición). Implementamos un **Filtro de Kalman** (`KalmanLatLong.kt`) para suavizar la trayectoria. Esto evita que la app sume kilómetros falsos cuando estás detenido o la señal es débil, resultando en una medición de distancia extremadamente fiel a la realidad.

### 2. Persistencia de Datos Compleja
*   **Tecnología:** `Room Persistence Library` con Relaciones 1:N.
*   **Por qué:** Una jornada de trabajo se divide en múltiples sesiones. Usamos **Room** con entidades para `Jornada` y `Sesion`, asegurando que el historial sea persistente incluso si el teléfono se apaga. Las consultas se manejan con **Kotlin Flow** para que la UI se actualice automáticamente cuando cambian los datos.

### 3. Procesamiento en Segundo Plano
*   **Tecnología:** `Android Services` (Foreground Service) + `Notifications`.
*   **Por qué:** El tracking no debe detenerse si el repartidor usa otras apps o apaga la pantalla. Usamos un **Foreground Service** (`GpsService.kt`) con una notificación persistente para garantizar que el sistema Android no mate el proceso de rastreo durante la jornada.

### 4. Interfaz de Usuario y Visualización de Datos
*   **Tecnología:** `XML Custom Views` + `osmdroid`.
*   **Por qué:** 
    - **osmdroid:** Elegido por su flexibilidad y por ser una alternativa de código abierto a Google Maps.
    - **TrendChartView:** Creamos una vista personalizada desde cero para dibujar los gráficos de tendencia de ganancias por kilómetro, permitiendo al usuario ver su progreso de un vistazo.

---

## 📊 Métricas Exclusivas (El "Por Qué" de la App)

- **Distancia Real vs. Distancia Plana:** Calculamos la distancia lineal entre puntos y la comparamos con el recorrido real registrado. Esto ayuda a visualizar cuánto más se viaja debido a calles cortadas o desvíos.
- **Tiempo Vacío (T. Vacío):** Una métrica crítica que mide cuánto tiempo pasa el repartidor sin un pedido asignado. Ayuda a identificar las "horas muertas" y mejorar la rentabilidad.
- **Desnivel Acumulado:** Fundamental para repartidores en bicicleta, midiendo el esfuerzo físico real basado en la altitud ganada durante el día.

---

## 🏗️ Arquitectura del Código
El proyecto sigue el patrón **MVVM**, separando claramente la lógica de negocio (Tracking y Base de Datos) de la interfaz de usuario. Esto facilita la escalabilidad y las pruebas unitarias.

---

## 📈 Próximos Pasos
- [ ] Implementar exportación de datos a CSV/Excel.
- [ ] Sincronización en la nube (Firebase/Supabase).
- [ ] Soporte para modo oscuro completo.

---
*Desarrollado con ❤️ para la comunidad de repartidores.*
