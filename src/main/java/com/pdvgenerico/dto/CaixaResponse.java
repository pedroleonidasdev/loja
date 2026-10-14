package com.pdvgenerico.dto;

import com.pdvgenerico.model.Caixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CaixaResponse(
        Long id,
        String usuarioAberturaNome,
        String usuarioAberturaLogin,
        BigDecimal valorInicial,
        LocalDateTime dataAbertura,
        String usuarioFechamentoNome,
        BigDecimal valorFinal,
        LocalDateTime dataFechamento,
        boolean aberto,
        String usuarioReaberturaNome,
        LocalDateTime dataReabertura,
        BigDecimal valorInicialOriginal,
        String usuarioEdicaoAberturaNome,
        LocalDateTime dataEdicaoAbertura,
        List<OperadorOnline> operadoresOnline
) {
    /** Quem está online agora (usado na sidebar para mostrar quem está operando o caixa). */
    public record OperadorOnline(String nome, String login) {}

    public static CaixaResponse fromEntity(Caixa caixa) {
        return new CaixaResponse(
                caixa.getId(),
                caixa.getUsuarioAbertura() != null ? caixa.getUsuarioAbertura().getNome() : null,
                caixa.getUsuarioAbertura() != null ? caixa.getUsuarioAbertura().getLogin() : null,
                caixa.getValorInicial(),
                caixa.getDataAbertura(),
                caixa.getUsuarioFechamento() != null ? caixa.getUsuarioFechamento().getNome() : null,
                caixa.getValorFinal(),
                caixa.getDataFechamento(),
                caixa.isAberto(),
                caixa.getUsuarioReabertura() != null ? caixa.getUsuarioReabertura().getNome() : null,
                caixa.getDataReabertura(),
                caixa.getValorInicialOriginal(),
                caixa.getUsuarioEdicaoAbertura() != null ? caixa.getUsuarioEdicaoAbertura().getNome() : null,
                caixa.getDataEdicaoAbertura(),
                List.of()
        );
    }

    public CaixaResponse comOperadoresOnline(List<OperadorOnline> operadores) {
        return new CaixaResponse(id, usuarioAberturaNome, usuarioAberturaLogin, valorInicial, dataAbertura,
                usuarioFechamentoNome, valorFinal, dataFechamento, aberto, usuarioReaberturaNome, dataReabertura,
                valorInicialOriginal, usuarioEdicaoAberturaNome, dataEdicaoAbertura, operadores);
    }
}
