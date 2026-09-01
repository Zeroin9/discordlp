-- Переход с фиксированного множителя x2 на тотализатор: выигрыш определяется
-- коэффициентом (призовой фонд / сумма ставок на победивший вариант).

-- Выплата победителю больше не ограничена удвоенной ставкой и может достигать
-- всего призового фонда, поэтому сумма транзакции расширяется до BIGINT (как balance).
ALTER TABLE points_transactions ALTER COLUMN amount TYPE BIGINT;

-- Доля комиссии организатора, зафиксированная в момент создания пари:
-- изменение настройки не влияет на уже идущие пари.
ALTER TABLE paris ADD COLUMN commission_rate NUMERIC(5, 4) NOT NULL DEFAULT 0;

-- Итоги розыгрыша, вычисляются один раз при объявлении исхода.
-- Расчет по каждой ставке опирается только на них, что сохраняет идемпотентность выплат.
ALTER TABLE paris ADD COLUMN total_pool BIGINT;
ALTER TABLE paris ADD COLUMN prize_pool BIGINT;
ALTER TABLE paris ADD COLUMN winning_sum BIGINT;
ALTER TABLE paris ADD COLUMN winning_coefficient NUMERIC(18, 4);

-- Заполняем итоги для уже завершенных пари, чтобы расчет по ним оставался
-- воспроизводимым. Комиссия у них нулевая: на момент розыгрыша ее не существовало.
UPDATE paris p
SET total_pool = s.total_pool,
    prize_pool = s.total_pool,
    winning_sum = s.winning_sum,
    winning_coefficient = CASE
        WHEN s.winning_sum > 0 THEN ROUND(s.total_pool::numeric / s.winning_sum, 4)
    END
FROM (
    SELECT pp.id,
           COALESCE(SUM(b.amount), 0) AS total_pool,
           COALESCE(SUM(b.amount) FILTER (WHERE b.bet_option = pp.winning_option), 0) AS winning_sum
    FROM paris pp
    LEFT JOIN pari_bets b ON b.pari_id = pp.id
    WHERE pp.status = 'FINISHED'
    GROUP BY pp.id
) s
WHERE p.id = s.id;
