package com.pdvgenerico.service;

import com.pdvgenerico.dto.ConsignacaoRequest;
import com.pdvgenerico.dto.FecharConsignacaoRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.*;
import com.pdvgenerico.repository.ConsignacaoRepository;
import com.pdvgenerico.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsignacaoService {

    /** Comissão sobre Livros e Bíblias (conforme o Termo de Responsabilidade). */
    public static final BigDecimal COMISSAO_LIVROS_BIBLIAS = new BigDecimal("15.00");
    /** Comissão sobre os demais artigos religiosos. */
    public static final BigDecimal COMISSAO_DEMAIS_ARTIGOS = new BigDecimal("20.00");

    private final ConsignacaoRepository consignacaoRepository;
    private final ProdutoRepository produtoRepository;

    /**
     * Livros e Bíblias pagam 15%; todo o resto, 20%. A decisão é pela categoria:
     * qualquer categoria cujo nome contenha "livro" ou "bíblia" (sem diferenciar
     * acento/maiúscula) conta como Livros e Bíblias.
     */
    public static BigDecimal percentualComissaoPara(Produto produto) {
        if (produto.getCategoria() == null || produto.getCategoria().getNome() == null) {
            return COMISSAO_DEMAIS_ARTIGOS;
        }
        String nome = Normalizer.normalize(produto.getCategoria().getNome(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        return (nome.contains("livro") || nome.contains("biblia"))
                ? COMISSAO_LIVROS_BIBLIAS
                : COMISSAO_DEMAIS_ARTIGOS;
    }

    @Transactional
    public Consignacao criar(ConsignacaoRequest request, Usuario usuarioLogado) {
        Consignacao consignacao = Consignacao.builder()
                .usuario(usuarioLogado)
                .dataHora(LocalDateTime.now(ZoneOffset.UTC))
                .consignatario(request.consignatario().trim())
                .prazoDevolucao(request.prazoDevolucao())
                .observacao(request.observacao())
                .status(StatusConsignacao.ABERTA)
                .build();

        // Junta linhas repetidas do mesmo produto antes de validar o estoque.
        Map<Long, Integer> quantidadePorProduto = new HashMap<>();
        List<Long> ordem = new ArrayList<>();
        for (ConsignacaoRequest.ItemConsignacaoRequest itemReq : request.itens()) {
            if (!quantidadePorProduto.containsKey(itemReq.produtoId())) {
                ordem.add(itemReq.produtoId());
            }
            quantidadePorProduto.merge(itemReq.produtoId(), itemReq.quantidade(), Integer::sum);
        }

        List<ItemConsignacao> itens = new ArrayList<>();
        for (Long produtoId : ordem) {
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: id " + produtoId));
            int quantidade = quantidadePorProduto.get(produtoId);

            // O estoque só dá baixa no fechamento, mas já avisamos aqui se a loja
            // não tem como cobrir o que está saindo.
            if (produto.getQuantidadeEstoque() < quantidade) {
                throw new BusinessException("Estoque insuficiente para o produto: " + produto.getNome()
                        + " (disponível: " + produto.getQuantidadeEstoque() + ")");
            }

            itens.add(ItemConsignacao.builder()
                    .consignacao(consignacao)
                    .produto(produto)
                    .quantidadeEnviada(quantidade)
                    .quantidadeVendida(0)
                    .precoUnitario(produto.getPrecoVenda())
                    .percentualComissao(percentualComissaoPara(produto))
                    .build());
        }

        consignacao.setItens(itens);
        return consignacaoRepository.save(consignacao);
    }

    /**
     * Acerto da consignação: informa quanto foi vendido de cada item e dá baixa
     * no estoque dessa quantidade. O restante (não vendido) volta para a loja e
     * não mexe no estoque.
     */
    @Transactional
    public Consignacao fechar(Long id, FecharConsignacaoRequest request) {
        Consignacao consignacao = buscarPorId(id);
        if (consignacao.getStatus() == StatusConsignacao.FECHADA) {
            throw new BusinessException("Esta consignação já foi fechada");
        }

        Map<Long, Integer> vendidoPorItem = new HashMap<>();
        for (FecharConsignacaoRequest.ItemVendido v : request.itens()) {
            vendidoPorItem.put(v.itemId(), v.quantidadeVendida());
        }
        for (Long itemId : vendidoPorItem.keySet()) {
            boolean existe = consignacao.getItens().stream().anyMatch(i -> i.getId().equals(itemId));
            if (!existe) {
                throw new BusinessException("Item " + itemId + " não pertence a esta consignação");
            }
        }

        for (ItemConsignacao item : consignacao.getItens()) {
            int vendida = vendidoPorItem.getOrDefault(item.getId(), 0);
            Produto produto = item.getProduto();

            if (vendida > item.getQuantidadeEnviada()) {
                throw new BusinessException("Quantidade vendida de \"" + produto.getNome()
                        + "\" (" + vendida + ") é maior que a enviada (" + item.getQuantidadeEnviada() + ")");
            }
            if (produto.getQuantidadeEstoque() < vendida) {
                throw new BusinessException("Estoque insuficiente para dar baixa em \"" + produto.getNome()
                        + "\" (estoque: " + produto.getQuantidadeEstoque() + ", vendido: " + vendida + ")");
            }

            item.setQuantidadeVendida(vendida);
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - vendida);
            produtoRepository.save(produto);
        }

        consignacao.setStatus(StatusConsignacao.FECHADA);
        consignacao.setDataFechamento(LocalDateTime.now(ZoneOffset.UTC));
        return consignacaoRepository.save(consignacao);
    }

    public List<Consignacao> listarTodas() {
        return consignacaoRepository.findAllByOrderByDataHoraDesc();
    }

    public Consignacao buscarPorId(Long id) {
        return consignacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consignação não encontrada: id " + id));
    }

    /**
     * Exclui a consignação. Se já estava fechada, devolve ao estoque o que tinha
     * dado baixa (o inverso exato do fechamento).
     */
    @Transactional
    public void excluir(Long id) {
        Consignacao consignacao = buscarPorId(id);
        if (consignacao.getStatus() == StatusConsignacao.FECHADA) {
            for (ItemConsignacao item : consignacao.getItens()) {
                Produto produto = item.getProduto();
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidadeVendida());
                produtoRepository.save(produto);
            }
        }
        consignacaoRepository.delete(consignacao);
    }
}
