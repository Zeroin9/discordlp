# База данных

PostgreSQL. Схема управляется Flyway; скрипты лежат в `src/main/resources/db/migration`
и применяются при старте приложения до инициализации Hibernate (см. `FlywayConfig`).
Hibernate работает в режиме `ddl-auto: validate` и схему не меняет — любое изменение
модели требует новой миграции.

## Миграции

| Версия | Что добавляет |
|---|---|
| `V1__initial_schema.sql` | `guild_members`, `points_transactions` |
| `V2__add_initiated_by.sql` | `points_transactions.initiated_by` |
| `V3__add_muted_members.sql` | `muted_members` |
| `V4__add_pari.sql` | `paris`, `pari_bets`, `points_transactions.reference_id`, `CHECK (balance >= 0)` |
| `V5__pari_coefficient.sql` | Итоги розыгрыша в `paris`, `commission_rate`, расширение `points_transactions.amount` до `BIGINT` |

Flyway настроен с `baselineOnMigrate = true`, поэтому подключается и к существующей базе.

## Таблицы

### `guild_members`

Участник конкретного сервера и его баланс.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `BIGINT` PK | |
| `guild_id`, `user_id` | `VARCHAR(255)` | Discord-идентификаторы, уникальны в паре |
| `balance` | `BIGINT` | Текущий баланс LP, `CHECK (balance >= 0)` |
| `last_voice_check_at` | `TIMESTAMPTZ` | Когда последний раз начислялись голосовые баллы |
| `user_name`, `guild_name` | `VARCHAR(255)` | Кэш имен для дашборда, обновляется планировщиком |

Ограничение `CHECK (balance >= 0)` добавлено как `NOT VALID`: существующие строки не
перепроверяются, но все последующие вставки и обновления контролируются.

### `points_transactions`

Журнал движений средств. Записи не изменяются и не удаляются.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `BIGINT` PK | |
| `member_id` | `BIGINT` FK → `guild_members` | Чей баланс изменился |
| `amount` | `BIGINT` | Со знаком: списание отрицательное |
| `reason` | `VARCHAR(255)` | Значение `TransactionReason` |
| `initiated_by` | `VARCHAR(255)` | Discord-id инициатора действия |
| `reference_id` | `BIGINT` | Id объекта-источника (для пари — `paris.id`) |
| `created_at` | `TIMESTAMPTZ` | |

Причины (`TransactionReason`):

| Значение | Когда |
|---|---|
| `VOICE_STANDARD`, `VOICE_STREAMER`, `VOICE_VIEWER` | Начисление за голосовой канал |
| `ADMIN_MANUAL`, `ADMIN_REMOVE` | `/lpadd`, `/lpremove` |
| `USER_KICK`, `USER_MUTE` | `/lpkick`, `/lpmute` |
| `BET_HOLD`, `BET_WIN`, `BET_REFUND` | Ставка, выигрыш и возврат по пари |

### `muted_members`

Активные мьюты, выданные через `/lpmute`. Запись удаляется, когда планировщик снимает мьют.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `BIGINT` PK | |
| `guild_id`, `user_id` | `VARCHAR(255)` | Уникальны в паре |
| `muted_at` | `TIMESTAMP` | Момент выдачи мьюта |

### `paris`

Событие-пари.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `BIGINT` PK | |
| `guild_id`, `author_id`, `author_name` | `VARCHAR(255)` | Сервер и автор |
| `title` | `VARCHAR(255)` | Название (в приложении обрезается до 200 символов) |
| `status` | `VARCHAR(32)` | `OPEN`, `RESOLVING`, `FINISHED`, `CANCELED` |
| `winning_option` | `BOOLEAN` | `true` — «Да», `false` — «Нет», `NULL` — исход не объявлен |
| `commission_rate` | `NUMERIC(5,4)` | Доля комиссии, зафиксированная при создании пари |
| `total_pool` | `BIGINT` | Сумма всех ставок на момент объявления исхода |
| `prize_pool` | `BIGINT` | Призовой фонд: общий пул за вычетом комиссии |
| `winning_sum` | `BIGINT` | Сумма ставок на победивший вариант; `0` — победителей нет |
| `winning_coefficient` | `NUMERIC(18,4)` | Итоговый коэффициент, `NULL` — если победителей нет |
| `channel_id`, `message_id` | `VARCHAR(255)` | Координаты сообщения-опроса для перерисовки |
| `created_at` | `TIMESTAMPTZ` | |
| `closed_at` | `TIMESTAMPTZ` | Переход в терминальный статус |
| `settled_at` | `TIMESTAMPTZ` | Расчет доведен до конца; `NULL` — пари ждет дорасчета |

Индекс по `status` обслуживает выборку планировщиков.

Итоги розыгрыша (`total_pool`, `prize_pool`, `winning_sum`, `winning_coefficient`)
заполняются один раз, при объявлении исхода, и служат единственным источником для расчета
выплат — именно это делает расчет воспроизводимым и идемпотентным. `commission_rate`
фиксируется при создании пари, поэтому изменение настройки не влияет на идущие пари.
Подробнее — [pari.md](pari.md).

### `pari_bets`

Ставка участника.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `BIGINT` PK | |
| `pari_id` | `BIGINT` FK → `paris` | |
| `member_id` | `BIGINT` FK → `guild_members` | Уникален в паре с `pari_id` |
| `bet_option` | `BOOLEAN` | Выбранный вариант |
| `amount` | `BIGINT` | Сумма ставки, `CHECK (amount > 0)` |
| `settled` | `BOOLEAN` | Расчет по ставке выполнен — обеспечивает идемпотентность выплат |
| `payout` | `BIGINT` | Фактически начислено: доля призового фонда, возврат ставки или `0` |
| `created_at`, `settled_at` | `TIMESTAMPTZ` | |

Колонка называется `bet_option`, а не `option`, чтобы не спорить с ключевыми словами SQL.
Индекс `(pari_id, settled)` обслуживает батч-выборку нерассчитанных ставок.

## Блокировки

Репозитории объявляют явные запросы с `@Lock(PESSIMISTIC_WRITE)` — они транслируются в
`SELECT ... FOR UPDATE`:

| Метод | Что блокирует |
|---|---|
| `GuildMemberRepository.findByIdForUpdate` | Строку баланса |
| `PariRepository.findByIdForUpdate` | Строку пари |
| `PariBetRepository.findByIdForUpdate` | Строку ставки |

Порядок захвата всегда одинаковый (пари → баланс при приеме ставки, ставка → баланс при
расчете), поэтому взаимных блокировок не возникает. Подробнее — [pari.md](pari.md).
