# Тестирование

## Запуск

```bash
# Все тесты (нужен Docker для интеграционных)
.\gradlew.bat test

# Только модульные тесты — Docker не нужен
.\gradlew.bat test --tests "ru.z3r0ing.discordlp.service.*" \
                   --tests "ru.z3r0ing.discordlp.command.*" \
                   --tests "ru.z3r0ing.discordlp.listener.*" \
                   --tests "ru.z3r0ing.discordlp.config.*" \
                   --tests "ru.z3r0ing.discordlp.controller.*"

# Отчет о покрытии (генерируется автоматически после test)
.\gradlew.bat test
# build/reports/jacoco/test/html/index.html
```

## Требования

Модульные тесты не требуют ничего, кроме JDK 21.

Интеграционным тестам нужен **доступный Docker**: PostgreSQL поднимается через
Testcontainers (образ `postgres:16-alpine`). Контейнер запускается один раз на всю JVM
(singleton container в `PostgresContainerTest`) и переиспользуется всеми интеграционными
тестами.

## Что чем покрыто

### Модульные тесты (Mockito)

| Класс тестов | Что проверяет |
|---|---|
| `PariServiceTest` | Валидацию и правила приема ставок, переходы статусов, права автора |
| `PariPayoutProcessorTest` | Математику выплат (x2 / 0 / возврат) и отказ от повторного начисления |
| `PariSettlementServiceTest` | Батч-обработку, остановку при отсутствии прогресса, планировщики восстановления и тайм-аута |
| `PariMessageServiceTest` | Формат `customId`, содержимое эмбеда, состав и доступность кнопок по статусам, перерисовку сообщения |
| `LoyaltyPointsServiceTest` | Начисления 100/150/200 LP, пропуск заглушенных и AFK, интервал начисления, снятие мьютов |
| `GuildMemberServiceTest` | Создание участника и переиспользование существующего |
| `DashboardServiceTest` | Разбор параметра сортировки и откат к значению по умолчанию |
| `BalanceCommandsTest` | `/lp`, `/lpuser`, `/lpadd`, `/lpremove` |
| `ModerationCommandsTest` | `/lpkick`, `/lpmute`, включая отказы Discord API |
| `LpPariCommandTest` | Публикацию опроса и сохранение координат сообщения |
| `LoyaltyPointsCommandListenerTest` | Маршрутизацию команд и проверку прав ADMINISTRATOR |
| `PariInteractionListenerTest` | Разбор `customId`, открытие модального окна, разбор суммы, кнопки управления |
| `ConfigurationTest` | Регистрацию slash-команд, подписку слушателей, порядок Flyway → Hibernate |
| `DashboardControllerTest` | HTTP-слой дашборда и рендеринг Thymeleaf-шаблона |

Взаимодействие с Discord мокается целиком: JDA-объекты (`Guild`, `Member`, `User`,
события взаимодействий) — интерфейсы, а асинхронные `RestAction` проверяются через захват
success/failure-колбэков и их вызов вручную.

### Интеграционные тесты (Testcontainers + PostgreSQL)

| Класс тестов | Что проверяет |
|---|---|
| `DiscordlpApplicationTests` | Полный контекст приложения: миграции применяются, Hibernate валидирует схему, все бины связываются. Соединение с Discord подменено моком |
| `PariFlowIntegrationTest` | Полный цикл пари на настоящей БД |

`PariFlowIntegrationTest` покрывает то, что нельзя проверить моками:

- выплата победителю и обнуление проигравшей ставки;
- троекратный повторный расчет не начисляет выигрыш дважды;
- отмена возвращает ставки полностью;
- **8 потоков со спам-кликами по одной ставке — принята ровно одна**, баланс не уходит
  в минус;
- БД отклоняет отрицательный баланс;
- после `FINISHED` ставки не принимаются.

Тестовые транзакции в этом классе отключены (`@Transactional(propagation = NOT_SUPPORTED)`):
иначе каждый вызов сервиса выполнялся бы внутри общей откатываемой транзакции, и ни
пессимистичные блокировки, ни расчет в отдельных транзакциях проверить было бы нельзя.

## Покрытие

JaCoCo подключен и генерирует отчет после каждого прогона `test`:
`build/reports/jacoco/test/` (HTML, XML, CSV).

Текущие показатели — **95% инструкций, 90% ветвей**. Непокрытым осознанно остается:

- `DiscordBotConfig` — создание бина `JDA` требует реального токена и сетевого логина;
- `DiscordlpApplication.main` — точка входа Spring Boot;
- часть лямбд-логгеров в failure-колбэках.

JPA-сущности в отчете не фигурируют: их код целиком генерируется Lombok, а JaCoCo
пропускает классы, помеченные `@lombok.Generated`.
