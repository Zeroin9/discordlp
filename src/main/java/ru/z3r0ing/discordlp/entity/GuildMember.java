package ru.z3r0ing.discordlp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "guild_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "user_id"})
})
public class GuildMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    @Column(name = "last_voice_check_at")
    private Instant lastVoiceCheckAt;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "guild_name")
    private String guildName;
}