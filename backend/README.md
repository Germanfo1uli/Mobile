# Bugs Game API

REST API сохраняет анкеты игроков, игровые настройки и результаты в PostgreSQL.

## Запуск

Из корня репозитория:

```bash
docker compose up --build
```

После запуска API доступен по адресу `http://localhost:8080/api`, PostgreSQL — на порту `5432`.
Миграции применяются автоматически перед запуском API. Данные сохраняются в Docker volume
`bugs_postgres_data`.

Для отдельного запуска API скопируйте `.env.example` в `.env`, задайте `DATABASE_URL`, установите
зависимости командой `npm install`, затем выполните `npm start`.

## Методы

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/api/health` | Проверка API и соединения с PostgreSQL |
| `POST` | `/api/players` | Регистрация игрока и создание настроек по умолчанию |
| `GET` | `/api/players` | Список зарегистрированных игроков |
| `GET` | `/api/players/:id/settings` | Настройки игрока |
| `PUT` | `/api/players/:id/settings` | Изменение настроек игрока |
| `POST` | `/api/results` | Сохранение результата раунда |
| `GET` | `/api/records?limit=20` | Таблица лучших результатов |

### Регистрация игрока

```json
{
  "fullName": "Иванов Иван Иванович",
  "gender": "Мужской",
  "course": 2,
  "difficulty": 3,
  "birthDate": "2004-05-17",
  "zodiac": "Телец"
}
```

### Настройки

```json
{
  "gameSpeed": 1.0,
  "maxInsects": 8,
  "bonusIntervalSeconds": 15,
  "roundDurationSeconds": 60
}
```

### Результат игры

```json
{
  "playerId": "123e4567-e89b-42d3-a456-426614174000",
  "score": 120,
  "hits": 14,
  "misses": 2,
  "difficulty": 3
}
```

## Подключение Android

По умолчанию приложение использует `http://10.0.2.2:8080/api/`, то есть localhost компьютера
из Android Emulator. Для физического телефона укажите IP компьютера при сборке:

```powershell
.\gradlew.bat assembleDebug -PBUGS_API_BASE_URL=http://192.168.1.10:8080/api/
```

Телефон и компьютер должны находиться в одной сети. Для внешнего сервера используйте HTTPS и
запретите cleartext-трафик в AndroidManifest.
