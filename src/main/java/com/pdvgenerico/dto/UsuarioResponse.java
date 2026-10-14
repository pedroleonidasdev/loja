package com.pdvgenerico.dto;

import com.pdvgenerico.model.Usuario;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @param online             true se o usuário teve atividade recente (ver PresencaService)
 * @param ultimoAcessoHaSeg  segundos desde a última atividade; null se nunca acessou
 *                           (ou se a sessão foi encerrada/saiu do sistema)
 */
public record UsuarioResponse(
        Long id,
        String login,
        String nome,
        String perfil,
        boolean ativo,
        boolean online,
        Long ultimoAcessoHaSeg
) {
    public static UsuarioResponse fromEntity(Usuario usuario) {
        LocalDateTime ultimo = usuario.getUltimoAcesso();
        Long haSeg = ultimo == null ? null : Math.max(0, Duration.between(ultimo, LocalDateTime.now()).getSeconds());
        boolean online = usuario.isAtivo() && haSeg != null
                && haSeg <= com.pdvgenerico.service.PresencaService.JANELA_ONLINE.getSeconds();
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getLogin(),
                usuario.getNome(),
                usuario.getPerfil().name(),
                usuario.isAtivo(),
                online,
                haSeg
        );
    }
}
