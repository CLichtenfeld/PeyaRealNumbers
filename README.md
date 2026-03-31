# Peya Real Numbers 🚴‍♂️📦

**Peya Real Numbers** es una herramienta de auditoría avanzada para repartidores de plataformas (como PedidosYa) que necesitan datos precisos sobre su rendimiento real. Mientras que las apps oficiales suelen mostrar distancias "a vuelo de pájaro", esta aplicación registra el movimiento exacto y el esfuerzo físico, permitiendo optimizar rutas y entender la rentabilidad real de la jornada.

---

## 🛠️ Stack Tecnológico (Senior Mobile Architecture)

Este proyecto ha sido refactorizado siguiendo las mejores prácticas de la industria para garantizar escalabilidad, mantenibilidad y robustez:

### 1. Inyección de Dependencias con Hilt & KSP
*   **Implementación:** Uso de **Dagger Hilt** con el procesador de símbolos de Kotlin (**KSP**) para una compilación más rápida.
*   **Beneficio:** Centralización de la configuración de la base de datos y servicios, facilitando el desacoplamiento de componentes y la testabilidad.

### 2. Procesamiento en Segundo Plano Robusto
*   **Foreground Service:** `GpsService.kt` gestiona el tracking en tiempo real con una notificación persistente, garantizando que el sistema Android no detenga el rastreo bajo ninguna circunstancia.
*   **WorkManager:** Implementación de `ElevationWorker` para tareas de "Refinería Topográfica". Cuando finaliza una jornada, el sistema programa automáticamente una tarea en segundo plano para consultar APIs de elevación (Open-Elevation) y refinar los cálculos de esfuerzo físico sin bloquear la UI.

### 3. Persistencia y Reactividad
*   **Room Database:** Arquitectura de base de datos relacional con entidades `Jornada` y `Sesion`.
*   **Flow & Coroutines:** Uso extensivo de **Kotlin Coroutines** para operaciones asíncronas y **Flow** para una interfaz de usuario reactiva que se actualiza en tiempo real ante cambios en la base de datos.

### 4. Geolocalización y Altimetría de Precisión
*   **Filtro de Kalman:** Implementación de un filtro matemático personalizado (`KalmanLatLong.kt`) para eliminar el ruido del GPS y evitar mediciones falsas de distancia.
*   **Cálculo de Potencia (Watts):** Algoritmo físico que estima el gasto energético del repartidor basado en la masa total (rider + bici), coeficiente de rodadura, resistencia aerodinámica y pendiente real.

---

## 📊 Métricas de Valor de Negocio

- **Eficiencia ($/km):** Cálculo automático del ingreso neto por cada kilómetro real recorrido (no el estimado por la plataforma).
- **Tiempo de Vacío (Idle Time):** Identificación precisa de periodos de inactividad para optimizar las zonas de espera.
- **Esfuerzo Extra (%):** Porcentaje de energía adicional consumida debido a la topografía del terreno (desniveles).
- **Verificación Topográfica:** Badge de verificación que indica cuando una jornada ha sido procesada con datos de alta precisión.

---

## 🏗️ Patrones de Diseño Aplicados
*   **MVVM (Model-View-ViewModel):** Separación clara de responsabilidades.
*   **Repository Pattern:** Abstracción de la fuente de datos (Local DB vs API).
*   **Singleton & Factory:** Gestión eficiente de instancias de base de datos y clientes de red.

---

## 🚀 Utilidades para Desarrolladores
La app incluye un **Mock Data Generator** (accesible mediante pulsación larga de 3s en el icono de soporte) que permite poblar la base de datos con jornadas simuladas (1 semana, 1 mes o 1 año) para testear los gráficos de tendencia y el historial sin necesidad de realizar trayectos reales.

---
*Desarrollado con arquitectura moderna para la comunidad de repartidores.*
