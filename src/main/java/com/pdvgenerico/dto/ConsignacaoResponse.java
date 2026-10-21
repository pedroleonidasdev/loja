package com.pdvgenerico.dto;

import com.pdvgenerico.model.Consignacao;
import com.pdvgenerico.model.ItemConsignacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ConsignacaoResponse(
        Long id,
        String usuarioNome,
        LocalDateTime dataHora,
        String consignatario,
        LocalDate prazoDevolucao,
        String observacao,
        String status,
        LocalDateTime dataFechamento,
        BigDecimal totalEnviado,
        BigDecimal totalVendido,
        BigDecimal totalComissao,
        BigDecimal totalLiquidoLoja,
        /** Valor do recibo: o editado no fechamento, ou o líquido da loja (vendido − comissão) se não houve edição. */
        BigDecimal valorRecibo,
        List<ItemResponse> itens
) {
    public record ItemResponse(
            Long id,
            Long produtoId,
            String produtoNome,
            String codigoBarras,
            String categoriaNome,
            Integer quantidadeEnviada,
            Integer quantidadeVendida,
            Integer quantidadeDevolvida,
            BigDecimal precoUnitario,
            BigDecimal percentualComissao,
            BigDecimal valorEnviado,
            BigDecimal valorVendido,
            BigDecimal comissao
    ) {
        static ItemResponse fromEntity(ItemConsignacao i) {
            BigDecimal valorEnviado = i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidadeEnviada()));
            BigDecimal valorVendido = i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidadeVendida()));
            BigDecimal comissao = valorVendido.multiply(i.getPercentualComissao())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return new ItemResponse(
                    i.getId(),
                    i.getProduto().getId(),
                    i.getProduto().getNome(),
                    i.getProduto().getCodigoBarras(),
                    i.getProduto().getCategoria() != null ? i.getProduto().getCategoria().getNome() : null,
                    i.getQuantidadeEnviada(),
                    i.getQuantidadeVendida(),
                    i.getQuantidadeEnviada() - i.getQuantidadeVendida(),
                    i.getPrecoUnitario(),
                    i.getPercentualComissao(),
                    valorEnviado,
                    valorVendido,
                    comissao
            );
        }
    }

    public static ConsignacaoResponse fromEntity(Consignacao c) {
        List<ItemResponse> itens = c.getItens().stream().map(ItemResponse::fromEntity).toList();
        BigDecimal totalEnviado = itens.stream().map(ItemResponse::valorEnviado).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVendido = itens.stream().map(ItemResponse::valorVendido).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalComissao = itens.stream().map(ItemResponse::comissao).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ConsignacaoResponse(
                c.getId(),
                c.getUsuario().getNome(),
                c.getDataHora(),
                c.getConsignatario(),
                c.getPrazoDevolucao(),
                c.getObservacao(),
                c.getStatus().name(),
                c.getDataFechamento(),
                totalEnviado,
                totalVendido,
                totalComissao,
                totalVendido.subtract(totalComissao),
                c.getValorRecibo() != null ? c.getValorRecibo() : totalVendido.subtract(totalComissao),
                itens
        );
    }
}
