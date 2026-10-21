package com.pdvgenerico.repository;

import com.pdvgenerico.model.AnexoPrivado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AnexoPrivadoRepository extends JpaRepository<AnexoPrivado, Long> {

    Optional<AnexoPrivado> findByMensagemId(Long mensagemId);

    /** Espaço total (em bytes) ocupado pelos anexos guardados no banco. */
    @Query("select coalesce(sum(a.tamanho), 0) from AnexoPrivado a")
    long somaTamanhos();

    /** Anexos mais antigos que o limite (a mensagem continua, só o arquivo expira). */
    @Modifying
    @Query("delete from AnexoPrivado a where a.criadaEm < :limite")
    int excluirAnterioresA(@Param("limite") Instant limite);

    /** Anexos cuja mensagem já foi apagada (conversa apagada pelos dois lados ou retenção). */
    @Modifying
    @Query("delete from AnexoPrivado a where a.mensagemId not in (select m.id from MensagemSuporte m)")
    int excluirSemMensagem();
}
