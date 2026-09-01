package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariStatus;
import ru.z3r0ing.discordlp.repository.PariBetRepository;
import ru.z3r0ing.discordlp.repository.PariRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Батч-расчет пари: перебирает нерассчитанные ставки порциями и отдает каждую
 * в {@link PariPayoutProcessor}, где выплата проводится в отдельной транзакции.
 * <p>
 * Пари считается рассчитанным только после того, как не осталось ставок с {@code settled = false};
 * до этого момента {@code settled_at} остается пустым, и планировщик
 * {@link #recoverPendingSettlements()} доводит расчет до конца после сбоя или рестарта сервиса.
 * <p>
 * Как только расчет доведен до конца, в канал уходит сводка выплат
 * ({@link PariMessageService#publishResults(Long)}) — ответом на сообщение пари.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PariSettlementService {

    /** Размер порции ставок, обрабатываемых за один проход. */
    private static final int BATCH_SIZE = 200;

    /** Сколько пари за раз забирает восстановительный планировщик. */
    private static final int RECOVERY_LIMIT = 20;

    private static final long RECOVERY_INTERVAL_MS = 60_000L;

    private final PariRepository pariRepository;
    private final PariBetRepository pariBetRepository;
    private final PariPayoutProcessor pariPayoutProcessor;
    private final PariService pariService;
    private final PariMessageService pariMessageService;

    @Value("${pari.timeout-hours:24}")
    private long timeoutHours;

    /**
     * Проводит расчет по всем нерассчитанным ставкам пари.
     *
     * @return количество ставок, рассчитанных этим вызовом
     */
    public int settle(Long pariId) {
        int processed = 0;
        while (true) {
            List<Long> betIds = pariBetRepository.findUnsettledBetIds(pariId, PageRequest.of(0, BATCH_SIZE));
            if (betIds.isEmpty()) {
                break;
            }

            int processedInBatch = 0;
            for (Long betId : betIds) {
                try {
                    if (pariPayoutProcessor.settleBet(betId)) {
                        processedInBatch++;
                    }
                } catch (Exception e) {
                    log.error("Ошибка при расчете ставки {} в пари {}", betId, pariId, e);
                }
            }

            if (processedInBatch == 0) {
                // Прогресса нет — прекращаем, чтобы не крутиться вхолостую.
                // Незакрытое пари подберет восстановительный планировщик.
                log.warn("Расчет пари {} не продвигается: осталось {} нерассчитанных ставок",
                        pariId, betIds.size());
                return processed;
            }
            processed += processedInBatch;
        }

        markSettled(pariId);
        log.info("Расчет пари {} завершен, обработано ставок: {}", pariId, processed);
        // Сводка выплат в канал: отправляется один раз, повторный вызов ее не продублирует.
        pariMessageService.publishResults(pariId);
        return processed;
    }

    /** Помечает пари рассчитанным, если действительно не осталось незакрытых ставок. */
    public void markSettled(Long pariId) {
        if (!pariBetRepository.findUnsettledBetIds(pariId, PageRequest.of(0, 1)).isEmpty()) {
            return;
        }
        pariRepository.findById(pariId).ifPresent(pari -> {
            if (pari.getSettledAt() == null) {
                pari.setSettledAt(Instant.now());
                pariRepository.save(pari);
            }
        });
    }

    /**
     * Дорасчет пари, у которых исход объявлен, но выплаты не доведены до конца
     * (например, сервис упал в середине расчета).
     */
    @Scheduled(fixedDelay = RECOVERY_INTERVAL_MS)
    public void recoverPendingSettlements() {
        List<Pari> pending;
        try {
            pending = pariRepository.findByStatusInAndSettledAtIsNull(
                    List.of(PariStatus.FINISHED, PariStatus.CANCELED), PageRequest.of(0, RECOVERY_LIMIT));
        } catch (Exception e) {
            log.error("Не удалось получить список пари, ожидающих расчета", e);
            return;
        }

        for (Pari pari : pending) {
            try {
                log.info("Возобновление расчета пари {} ({})", pari.getId(), pari.getStatus());
                settle(pari.getId());
                pariMessageService.refresh(pari.getId());
            } catch (Exception e) {
                log.error("Ошибка при возобновлении расчета пари {}", pari.getId(), e);
            }
        }
    }

    /**
     * Отменяет пари, которые слишком долго остаются открытыми, с полным возвратом ставок.
     */
    @Scheduled(fixedDelay = RECOVERY_INTERVAL_MS)
    public void cancelTimedOutParis() {
        if (timeoutHours <= 0) {
            return;
        }

        Instant threshold = Instant.now().minus(Duration.ofHours(timeoutHours));
        List<Pari> expired;
        try {
            expired = pariService.findExpiredParis(threshold);
        } catch (Exception e) {
            log.error("Не удалось получить список просроченных пари", e);
            return;
        }

        for (Pari pari : expired) {
            try {
                log.info("Отмена пари {} по тайм-ауту ({} ч)", pari.getId(), timeoutHours);
                pariService.cancel(pari.getId(), null);
                settle(pari.getId());
                pariMessageService.refresh(pari.getId());
            } catch (PariException e) {
                log.debug("Пари {} уже закрыто: {}", pari.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Ошибка при отмене пари {} по тайм-ауту", pari.getId(), e);
            }
        }
    }
}
