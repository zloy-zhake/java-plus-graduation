# ExploreWithMe

Платформа для публикации городских мероприятий и сбора заявок на участие.
Дипломный проект курса "Java-разработчик. Расширенный" (https://practicum.yandex.ru/java-developer-plus/)

## Архитектура

Приложение разбито на независимые микросервисы, каждый со своей схемой БД. Все внешние запросы проходят через единую точку входа — Gateway.

```
Клиент
  │
  ▼
gateway-server  :8080   — маршрутизация по пути
  │
  ├── user-service        — управление пользователями (/admin/users/**)
  ├── event-service       — события, категории, локации (/events/**, /categories/**, /locations/**, /admin/events/**, ...)
  ├── request-service     — заявки на участие (/users/*/requests/**, /users/*/events/*/requests/**)
  ├── compilation-service — подборки (/compilations/**, /admin/compilations/**)
  └── stats-server        — статистика просмотров (/hit, /stats/**)

discovery-server  :8761  — Eureka: регистрация и обнаружение сервисов
config-server     :8888  — Spring Cloud Config: централизованная конфигурация
```

Базы данных:
- `ewm_main_db` — общая БД для user-service, event-service, request-service, compilation-service (каждый работает только со своими таблицами)
- `ewm_stats_db` — БД stats-server

## Сервисы

| Сервис | Назначение | Порт |
|--------|-----------|------|
| gateway-server | Единая точка входа, маршрутизация | 8080 |
| discovery-server | Реестр сервисов (Eureka) | 8761 |
| config-server | Централизованная конфигурация | 8888 |
| user-service | CRUD пользователей | случайный |
| event-service | События, категории, локации, статистика | случайный |
| request-service | Заявки на участие в событиях | случайный |
| compilation-service | Подборки событий | случайный |
| stats-server | Запись и чтение статистики просмотров | 9090 |

Бизнес-сервисы используют случайный порт (`server.port: 0`) — адрес обнаруживается через Eureka. stats-server использует фиксированный порт 9090, так как к нему обращаются напрямую тестовые клиенты.

## Взаимодействие между сервисами

Вызовы между микросервисами происходят через Feign-клиенты с circuit breaker (Resilience4j). При недоступности зависимого сервиса применяется fallback:

| Вызывает | Вызывает кого | Эндпоинт | Fallback |
|---------|--------------|----------|---------|
| event-service | user-service | `GET /internal/users/{id}` | `initiator.name = "N/A"` |
| event-service | user-service | `GET /internal/users?ids=` | пустой список |
| event-service | request-service | `GET /internal/requests/count?eventIds=` | пустая Map (confirmedRequests = 0) |
| request-service | event-service | `GET /internal/events/{id}` | исключение (критично) |
| compilation-service | event-service | `GET /internal/events?ids=` | пустой список событий |
| compilation-service | event-service | `GET /internal/events/{id}` | пустой список событий |

### Внутренний API (не проксируется через Gateway)

**user-service** — `GET /internal/users/{userId}`, `GET /internal/users?ids=`  
**event-service** — `GET /internal/events/{eventId}`, `GET /internal/events?ids=`  
**request-service** — `GET /internal/requests/count?eventIds=`

## Настройки

Все настройки хранятся в `infra/config-server/src/main/resources/config/`:

| Файл | Назначение |
|------|-----------|
| `gateway-server.yaml` | Маршруты Gateway |
| `user-service.yaml` / `*-docker.yaml` | Настройки user-service |
| `event-service.yaml` / `*-docker.yaml` | Настройки event-service |
| `request-service.yaml` / `*-docker.yaml` | Настройки request-service |
| `compilation-service.yaml` / `*-docker.yaml` | Настройки compilation-service |
| `stats-server.yaml` / `*-docker.yaml` | Настройки stats-server (в т.ч. порт 9090) |

Профиль `docker` активируется через `SPRING_PROFILES_ACTIVE=docker` в `docker-compose.yml` и переопределяет URL базы данных с `localhost` на имя контейнера.

## Запуск

```bash
# Сборка всех модулей
mvn package -DskipTests

# Запуск всех сервисов
docker compose up --build
```

После запуска:
- Gateway: http://localhost:8080
- Eureka Dashboard: http://localhost:8761
- Stats-server (прямой доступ): http://localhost:9090

## Спецификации API

- [Основной сервис (ewm-main-service)](https://raw.githubusercontent.com/yandex-praktikum/java-explore-with-me/main/ewm-main-service-spec.json)
- [Сервис статистики (ewm-stats-service)](https://raw.githubusercontent.com/yandex-praktikum/java-explore-with-me/main/ewm-stats-service-spec.json)
