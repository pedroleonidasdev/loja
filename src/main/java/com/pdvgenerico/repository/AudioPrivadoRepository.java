package com.pdvgenerico.repository;

import com.pdvgenerico.model.AudioPrivado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AudioPrivadoRepository extends JpaRepository<AudioPrivado, Long> {

    Optional<AudioPrivado> findByMensagemId(Long mensagemId);

    /** Áudios mais antigos que o limite (a mensagem continua, só o som expira). */
    @Modifying
    @Query("delete from AudioPrivado a where a.criadaEm < :limite")
    int excluirAnterioresA(@Param("limite") Instant limite);

    /** Áudios cuja mensagem já foi apagada (conversa apagada pelos dois lados ou retenção). */
    @Modifying
    @Query("delete from AudioPrivado a where a.mensagemId not in (select m.id from MensagemSuporte m)")
    int excluirSemMensagem();
}
