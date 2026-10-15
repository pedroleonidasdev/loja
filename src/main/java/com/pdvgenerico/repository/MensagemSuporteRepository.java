package com.pdvgenerico.repository;

import com.pdvgenerico.model.MensagemSuporte;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MensagemSuporteRepository extends JpaRepository<MensagemSuporte, Long> {

    /** Histórico inicial da conversa entre a e b: da mais nova para a mais antiga. */
    @Query("select m from MensagemSuporte m "
            + "where (m.remetenteId = :a and m.destinatarioId = :b) or (m.remetenteId = :b and m.destinatarioId = :a) "
            + "order by m.id desc")
    List<MensagemSuporte> recentes(@Param("a") Long a, @Param("b") Long b, Pageable pageable);

    /** Polling: só o que chegou depois da última mensagem que o navegador já tem. */
    @Query("select m from MensagemSuporte m "
            + "where m.id > :depois and ((m.remetenteId = :a and m.destinatarioId = :b) "
            + "or (m.remetenteId = :b and m.destinatarioId = :a)) "
            + "order by m.id asc")
    List<MensagemSuporte> depois(@Param("a") Long a, @Param("b") Long b,
                                 @Param("depois") Long depois, Pageable pageable);

    /** Pares (remetenteId, quantidade) das mensagens que o usuário ainda não leu. */
    @Query("select m.remetenteId, count(m) from MensagemSuporte m "
            + "where m.destinatarioId = :eu and m.lida = false group by m.remetenteId")
    List<Object[]> contarNaoLidasPorRemetente(@Param("eu") Long eu);

    @Modifying
    @Query("update MensagemSuporte m set m.lida = true "
            + "where m.destinatarioId = :eu and m.remetenteId = :outro and m.lida = false")
    int marcarComoLidas(@Param("eu") Long eu, @Param("outro") Long outro);

    long deleteByCriadaEmBefore(Instant limite);
}
