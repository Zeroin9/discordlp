package ru.z3r0ing.discordlp.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.z3r0ing.discordlp.entity.PariBet;

import java.util.List;
import java.util.Optional;

@Repository
public interface PariBetRepository extends JpaRepository<PariBet, Long> {

    /**
     * Блокирует строку ставки на время транзакции (SELECT ... FOR UPDATE).
     * Повторный расчет по уже рассчитанной ставке отсекается проверкой флага settled.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PariBet b WHERE b.id = :id")
    Optional<PariBet> findByIdForUpdate(@Param("id") Long id);

    Optional<PariBet> findByPariIdAndMemberId(Long pariId, Long memberId);

    /** Порция нерассчитанных ставок для батч-обработки. */
    @Query("SELECT b.id FROM PariBet b WHERE b.pari.id = :pariId AND b.settled = false ORDER BY b.id")
    List<Long> findUnsettledBetIds(@Param("pariId") Long pariId, Pageable pageable);

    long countByPariIdAndOption(Long pariId, Boolean option);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM PariBet b WHERE b.pari.id = :pariId AND b.option = :option")
    long sumAmountByPariIdAndOption(@Param("pariId") Long pariId, @Param("option") Boolean option);
}
