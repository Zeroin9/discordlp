package ru.z3r0ing.discordlp.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.entity.PariStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PariRepository extends JpaRepository<Pari, Long> {

    /**
     * Блокирует строку пари на время транзакции (SELECT ... FOR UPDATE).
     * Гарантирует, что прием ставки и смена статуса не выполнятся одновременно.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pari p WHERE p.id = :id")
    Optional<Pari> findByIdForUpdate(@Param("id") Long id);

    /**
     * Пари в терминальном статусе, расчет по которым не доведен до конца.
     * Используется для дорасчета после рестарта сервиса.
     */
    List<Pari> findByStatusInAndSettledAtIsNull(Collection<PariStatus> statuses, Pageable pageable);

    List<Pari> findByStatusAndCreatedAtBefore(PariStatus status, Instant threshold);

    List<Pari> findByGuildIdAndStatusOrderByCreatedAtDesc(String guildId, PariStatus status);
}
