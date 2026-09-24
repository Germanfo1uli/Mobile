# Bugs Game API

REST API сохраняет анкеты игроков, игровые настройки и результаты в PostgreSQL.

Серверная часть лабораторных 4–6: игровые раунды, проверка попаданий и сохранение
результатов. Контракт и примеры находятся в [docs/labs-4-6.md](docs/labs-4-6.md).
Android-клиент использует методы раундов, выбора игроков и рекордов. Звуки теургии
воспроизводятся на устройстве; физический наклон пока остаётся будущим этапом.

## Запуск

Из корня репозитория:

```bash
docker compose up --build
```

После запуска API доступен по адресу `http://localhost:8080/api`, PostgreSQL — на порту `5432`.
Миграции применяются автоматически перед запуском API. Данные сохраняются в Docker volume
`bugs_postgres_data`.

Для отдельного запуска API скопируйте `.env.example` в `.env`, задайте `DATABASE_URL`, установите
зависимости командой `npm install`, затем выполните `node --env-file=.env src/server.js`.
Пароль `bugs` в Compose используется только для учебного локального запуска.

## Методы

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/api/health` | Проверка API и соединения с PostgreSQL |
| `POST` | `/api/players` | Регистрация игрока и создание настроек по умолчанию |
| `GET` | `/api/players` | Список зарегистрированных игроков |
| `GET` | `/api/players/:id/settings` | Настройки игрока |
| `PUT` | `/api/players/:id/settings` | Изменение настроек игрока |
| `POST` | `/api/results` | Устаревший метод, HTTP 410: очки вычисляет сервер |
| `GET` | `/api/records?limit=20` | Таблица лучших результатов |
| `POST` | `/api/rounds` | Начать или восстановить игровой раунд |
| `GET` | `/api/rounds/:id` | Состояние раунда |
| `POST` | `/api/rounds/:id/events` | Попадание, промах, бонус или наклон |
| `POST` | `/api/rounds/:id/finish` | Завершить раунд и сохранить результат |
| `GET` | `/api/players/:id/results` | История результатов игрока |

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

### Начало игры

```json
{
  "playerId": "123e4567-e89b-42d3-a456-426614174000"
}
```

Это тело запроса `POST /api/rounds`. Результат сохраняется автоматически по таймеру
либо при `POST /api/rounds/:id/finish`; произвольные очки от клиента не принимаются.

## Подключение Android

По умолчанию приложение использует общий облачный API
`https://althunt-api.vercel.app/api/`, подключённый к PostgreSQL в Neon. Для локального
бэкенда в Android Emulator переопределите адрес при сборке:

```powershell
.\gradlew.bat assembleDebug -PBUGS_API_BASE_URL=http://10.0.2.2:8080/api/
```

Для физического телефона вместо `10.0.2.2` можно указать IP компьютера, если телефон и
компьютер находятся в одной сети. Локальные HTTP-адреса требуют временно разрешить
cleartext-трафик в AndroidManifest; production-сборка использует только HTTPS.

Если телефон подключён по USB, можно обойтись без IP-адреса компьютера:

```powershell
adb reverse tcp:8080 tcp:8080
cd labMob
.\gradlew.bat assembleDebug -PBUGS_API_BASE_URL=http://127.0.0.1:8080/api/
```

`adb reverse` нужно повторять после переподключения телефона.
