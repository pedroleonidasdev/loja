package com.pdvgenerico.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado da leitura de UMA foto (página) do relatório de consignação.
 * É só uma sugestão para a conferência: nada é gravado nem baixado do estoque aqui.
 */
public record LeituraPaginaResponse(
        List<Linha> linhas,
        List<String> naoEncontradas,
        BigDecimal totalManuscrito,
        BigDecimal somaLida,
        String observacao
) {
    public record Linha(
            Long itemId,
            String produtoNome,
            String codigoBarras,
            Integer enviada,
            Integer qtdVendida,
            BigDecimal valorLido,
            BigDecimal valorEsperado,
            boolean incerto,
            List<String> avisos
    ) {
    }
}
