# Архитектура

## Что это за приложение

Spring Boot приложение с двумя точками входа:

- **Discord-бот** (JDA) — начисляет баллы лояльности (LP) за активность в голосовых каналах
  и обслуживает slash-команды и интерактивные компоненты;
- **веб-дашборд** (Spring MVC + Thymeleaf) — таблица участников с балансами на `/dashboard`.

Состояние хранится в PostgreSQL, схема управляется миграциями Flyway.

## Слои

```
ru.z3r0ing.discordlp
├── config/      конфигурация Spring и инициализация JDA
├── command/     slash-команды (реализации SlashCommandHandler)
├── listener/    обработчики событий Discord (наследники ListenerAdapter)
├── service/     бизнес-логика и планировщики
├── repository/  интерфейсы Spring Data JPA
├── entity/      JPA-сущности
└── controller/  Spring MVC контроллеры
```

Зависимости направлены строго вниз: `command`/`listener` → `service` → `repository` → `entity`.
Слушатели и команды не работают с репозиториями напрямую там, где есть транзакционная логика:
всё, что меняет баланс, проходит через сервисы.

## Запуск и связывание с Discord

| Компонент | Роль |
|---|---|
| `DiscordBotConfig` | создает бин `JDA` по токену из `discord.bot.token` |
| `DiscordBotInitializer` | на `ApplicationReadyEvent` подписывает все бины `ListenerAdapter` на события JDA |
| `SlashCommandRegistrar` | на `ApplicationReadyEvent` публикует список slash-команд в Discord |
| `FlywayConfig` | выполняет миграции до инициализации Hibernate |

`FlywayConfig` заслуживает отдельного пояснения: в используемой версии Spring Boot нет
встроенной `FlywayAutoConfiguration`, поэтому Flyway конфигурируется вручную, а через
`BeanFactoryPostProcessor` бину `entityManagerFactory` принудительно добавляется зависимость
от бина `flyway`. Без этого Hibernate с `ddl-auto: validate` стартовал бы раньше миграций
и падал на несуществующих таблицах.

## Маршрутизация взаимодействий

Discord присылает три вида взаимодействий, которые обрабатывают два слушателя:

| Событие | Слушатель | Что делает |
|---|---|---|
| `SlashCommandInteractionEvent` | `LoyaltyPointsCommandListener` | ищет `SlashCommandHandler` по имени команды, проверяет права и делегирует |
| `ButtonInteractionEvent` | `PariInteractionListener` | разбирает `customId` вида `pari:<действие>:<id>[:yes\|no]` |
| `ModalInteractionEvent` | `PariInteractionListener` | принимает сумму ставки из модального окна |

Новая slash-команда добавляется двумя шагами: класс, реализующий `SlashCommandHandler`
(бин Spring), и строка в `SlashCommandRegistrar`. Регистрация в мапе слушателя происходит
автоматически — все реализации интерфейса внедряются списком.

Проверка прав вынесена в слушатель: если `handler.requiresAdmin()` возвращает `true`,
у вызывающего должно быть право `ADMINISTRATOR`.

## Планировщики

Все фоновые задачи — `@Scheduled` на общем планировщике Spring (`@EnableScheduling`
в `DiscordlpApplication`).

| Задача | Период | Что делает |
|---|---|---|
| `LoyaltyPointsService.processLoyaltyPoints` | 5 мин | начисляет LP участникам в голосовых каналах и снимает истекшие мьюты |
| `PariSettlementService.recoverPendingSettlements` | 60 с | доводит до конца расчет пари, прерванный сбоем или рестартом |
| `PariSettlementService.cancelTimedOutParis` | 60 с | отменяет зависшие пари по тайм-ауту с возвратом ставок |

`processLoyaltyPoints` первым делом проверяет `jda.getStatus() == CONNECTED` и выходит,
если соединение с Discord еще не установлено, — иначе обращение к кэшу гильдий может
заблокироваться.

## Работа с деньгами

Единица валюты — LP, баланс хранится в `guild_members.balance`. Каждое движение средств
пишется в `points_transactions` с причиной (`TransactionReason`) и, где это применимо,
с `initiated_by` (кто инициировал) и `reference_id` (id объекта-источника, например пари).

Правила, которые соблюдаются во всех сценариях:

- изменение баланса и запись транзакции происходят в одной транзакции БД;
- там, где возможна конкуренция (ставки в пари), строка баланса берется под
  `SELECT ... FOR UPDATE`;
- на уровне БД стоит `CHECK (balance >= 0)` — последний рубеж против ухода в минус.

Подробности механики пари: [pari.md](pari.md). Схема БД: [database.md](database.md).
