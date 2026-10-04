# SyncFlow

<p align="center">
  <strong>Gestión inteligente de calendarios y coordinación de equipos</strong>
</p>

<p align="center">
  Plataforma web para organizar eventos personales y empresariales, coordinar equipos y simplificar la planificación mediante recurrencia e inteligencia artificial.
</p>

<p align="center">
  <a href="https://github.com/Ricardorogramador/SyncFlow">
    <img src="https://img.shields.io/badge/Repositorio-GitHub-181717?logo=github&logoColor=white" alt="Repositorio GitHub">
  </a>
  <img src="https://img.shields.io/badge/Frontend-React-61DAFB?logo=react&logoColor=black" alt="React">
  <img src="https://img.shields.io/badge/Backend-Spring_Boot-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Contenedores-Docker-2496ED?logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/Orquestación-Docker_Compose-2496ED?logo=docker&logoColor=white" alt="Docker Compose">
  <img src="https://img.shields.io/badge/Estado-En_desarrollo-orange" alt="En desarrollo">
</p>

---

## Tabla de contenidos

- [Descripción](#descripción)
- [Características principales](#características-principales)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Requisitos previos](#requisitos-previos)
- [Instalación y configuración](#instalación-y-configuración)
- [Construcción y ejecución con Docker Compose](#construcción-y-ejecución-con-docker-compose)
- [Comandos útiles](#comandos-útiles)
- [Reglas de negocio](#reglas-de-negocio)
- [Alcance y roadmap](#alcance-y-roadmap)

## Descripción

**SyncFlow** es una plataforma web que busca centralizar la gestión de calendarios y eventos en entornos personales y empresariales. Permite a los usuarios organizar sus propios compromisos y, cuando pertenecen a una organización, consultar y gestionar los eventos asignados por esta.

El sistema incorpora herramientas para programar eventos recurrentes, crear eventos mediante descripciones en lenguaje natural y enviar notificaciones por correo electrónico relacionadas con la actividad del calendario.

El frontend y el backend se encuentran en un mismo repositorio. Docker Compose coordina la construcción y ejecución de los servicios de la aplicación.

## Características principales

- **Gestión de usuarios:** registro e inicio de sesión.
- **Calendario personal:** creación, edición y eliminación de eventos.
- **Gestión de organizaciones:** registro de empresas y administración de empleados.
- **Códigos de invitación:** incorporación de empleados mediante códigos con límites de uso.
- **Eventos grupales:** asignación de eventos a uno o varios empleados.
- **Recurrencia configurable:** eventos diarios, semanales, mensuales y con intervalos personalizados.
- **Creación asistida por IA:** interpretación de descripciones en lenguaje natural para proponer eventos.
- **Notificaciones por correo:** avisos ante la creación, modificación o cancelación de eventos.
- **Conflictos de horario:** detección y señalización de solapamientos entre compromisos.
- **Suscripciones:** soporte conceptual para planes individuales y empresariales.

## Stack tecnológico

| Componente | Tecnología | Ubicación |
|---|---|---|
| Frontend | React | `frontend-saas/` |
| Backend | Spring Boot | `PA-SaaS2/` |
| Contenedores | Docker | Configuración del proyecto |
| Orquestación | Docker Compose | `docker-compose.yaml` |
| Configuración local | Variables de entorno | `.env.example` |

## Estructura del repositorio

```text
SyncFlow/
├── frontend-saas/        # Aplicación frontend con React
├── PA-SaaS2/             # Aplicación backend con Spring Boot
├── .env.example          # Plantilla de variables de entorno
├── .gitignore
├── docker-compose.yaml   # Construcción y ejecución de servicios
└── README.md             # Documentación del proyecto
```

## Requisitos previos

Antes de ejecutar el proyecto, instala:

- [Git](https://git-scm.com/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) o Docker Engine.
- Docker Compose, disponible como `docker compose` en las versiones actuales de Docker Desktop y Docker Engine.

Comprueba la instalación:

```bash
git --version
docker --version
docker compose version
```

## Instalación y configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/Ricardorogramador/SyncFlow.git
cd SyncFlow
```

### 2. Crear el archivo `.env`

El repositorio incluye `.env.example` como plantilla de configuración. Crea el archivo local `.env` copiando esa plantilla.

**Windows PowerShell:**

```powershell
Copy-Item .env.example .env
```

**Linux o macOS:**

```bash
cp .env.example .env
```

Abre `.env` y reemplaza los valores de ejemplo por las claves, credenciales y configuraciones de tu entorno. Conserva los nombres de las variables que ya aparecen en `.env.example`.

## Construcción y ejecución con Docker Compose

Ejecuta los comandos desde la raíz del repositorio, en la misma carpeta donde se encuentra `docker-compose.yaml`.

### Construir e iniciar los servicios

```bash
docker compose up --build -d
```

Este comando construye las imágenes definidas en Docker Compose e inicia los servicios en segundo plano. Si el frontend y el backend están configurados en el archivo Compose, no necesitas construirlos manualmente por separado.

### Verificar los contenedores

```bash
docker compose ps
```

### Consultar los registros

```bash
docker compose logs -f
```

Para ver los registros de un servicio concreto:

```bash
docker compose logs -f <nombre-del-servicio>
```

Accede al frontend mediante la dirección y el puerto publicados en `docker-compose.yaml`. Los puertos concretos dependen de la configuración del proyecto.

## Comandos útiles

| Comando | Descripción |
|---|---|
| `docker compose build` | Construye las imágenes de los servicios. |
| `docker compose up -d` | Inicia los servicios en segundo plano. |
| `docker compose up --build -d` | Construye las imágenes e inicia los servicios. |
| `docker compose ps` | Muestra el estado de los contenedores. |
| `docker compose logs -f` | Sigue los registros de los servicios. |
| `docker compose restart` | Reinicia los servicios. |
| `docker compose down` | Detiene y elimina los contenedores y las redes creadas por Compose. |


## Reglas de negocio

- **Confirmación de eventos generados por IA:** los datos interpretados se presentan en una vista previa editable y requieren confirmación explícita antes de guardarse.
- **Conflictos de horario:** los solapamientos se señalan y notifican, pero no bloquean automáticamente la asignación de un evento organizacional.
- **Suscripciones individuales:** cuando un usuario con una suscripción individual activa se incorpora a una organización, la suscripción se pausa conservando los días restantes. Al salir, puede reactivarse con el tiempo pendiente.
- **Códigos de invitación:** la incorporación requiere un código válido y con usos disponibles.
- **Aislamiento de organizaciones:** los usuarios no deben acceder a información de organizaciones a las que no pertenecen.

## Alcance y roadmap

El alcance inicial contempla autenticación, calendarios personales, eventos recurrentes, creación asistida por IA, notificaciones, organizaciones, invitaciones, asignación de eventos y gestión básica de suscripciones.

Como trabajo futuro se consideran:

- Aplicación móvil nativa.
- Integración con Google Calendar y Outlook.
- Soporte para estructuras organizacionales multinivel.
- Módulos de asistencia y nómina.
- Historial de pagos.
- Mejoras para editar ocurrencias individuales de eventos recurrentes.
- Integración completa con una pasarela de pagos.

<p align="center">
  <strong>SyncFlow</strong><br>
  Organización, planificación y coordinación en un solo lugar.
</p>
