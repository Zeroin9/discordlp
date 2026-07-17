package ru.z3r0ing.discordlp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.z3r0ing.discordlp.entity.MutedMember;

import java.time.Instant;
import java.util.List;

@Repository
public interface MutedMemberRepository extends JpaRepository<MutedMember, Long> {

    List<MutedMember> findByMutedAtBefore(Instant threshold);
}
