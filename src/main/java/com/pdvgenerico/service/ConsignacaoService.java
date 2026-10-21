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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsignacaoService {

    /** Comissão sobre Livros, Bíblias e Paulinas (conforme o Termo de Responsabilidade). */
    public static final BigDecimal COMISSAO_LIVROS_BIBLIAS = new BigDecimal("15.00");
    /** Comissão sobre os demais artigos religiosos. */
    public static final BigDecimal COMISSAO_DEMAIS_ARTIGOS = new BigDecimal("20.00");

    private final ConsignacaoRepository consignacaoRepository;
    private final ProdutoRepository produtoRepository;

    /**
     * Livros, Bíblias e Paulinas pagam 15%; todo o resto, 20%. A decisão é pela categoria:
     * qualquer categoria cujo nome contenha "livro", "bíblia" ou "paulinas" (sem diferenciar
     * acento/maiúscula) paga 15%.
     */
    public static BigDecimal percentualComissaoPara(Produto produto) {
        if (produto.getCategoria() == null || produto.getCategoria().getNome() == null) {
            return COMISSAO_DEMAIS_ARTIGOS;
        }
        String nome = Normalizer.normalize(produto.getCategoria().getNome(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        return (nome.contains("livro") || nome.contains("biblia") || nome.contains("paulina"))
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
        Map<Long, BigDecimal> precoInformado = new HashMap<>();
        List<Long> ordem = new ArrayList<>();
        for (ConsignacaoRequest.ItemConsignacaoRequest itemReq : request.itens()) {
            if (!quantidadePorProduto.containsKey(itemReq.produtoId())) {
                ordem.add(itemReq.produtoId());
            }
            quantidadePorProduto.merge(itemReq.produtoId(), itemReq.quantidade(), Integer::sum);
            if (itemReq.precoUnitario() != null) {
                precoInformado.put(itemReq.produtoId(), itemReq.precoUnitario());
            }
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
                    .precoUnitario(precoInformado.getOrDefault(produtoId, produto.getPrecoVenda()))
                    .percentualComissao(percentualComissaoPara(produto))
                    .build());
        }

        consignacao.setItens(itens);
        return consignacaoRepository.save(consignacao);
    }

    /**
     * Edita uma consignação AINDA ABERTA: dados do cabeçalho e a lista de itens
     * (acrescentar produtos, mudar quantidades, remover). A lista recebida é a lista
     * final: o que não vier nela é removido. Itens que já existiam mantêm o preço e a
     * comissão gravados na criação, a menos que a tela mande um preço novo para a linha; os produtos
     * novos usam o preço informado ou, na falta dele, o preço de hoje do cadastro.
     * Como o estoque só dá baixa no fechamento, nada muda no estoque aqui.
     */
    @Transactional
    public Consignacao atualizar(Long id, ConsignacaoRequest request) {
        Consignacao consignacao = buscarPorId(id);
        if (consignacao.getStatus() != StatusConsignacao.ABERTA) {
            throw new BusinessException("Só é possível editar uma consignação em aberto.");
        }

        // junta linhas repetidas do mesmo produto, mantendo a ordem em que vieram
        Map<Long, Integer> quantidadePorProduto = new LinkedHashMap<>();
        Map<Long, BigDecimal> precoInformado = new HashMap<>();
        for (ConsignacaoRequest.ItemConsignacaoRequest itemReq : request.itens()) {
            quantidadePorProduto.merge(itemReq.produtoId(), itemReq.quantidade(), Integer::sum);
            if (itemReq.precoUnitario() != null) {
                precoInformado.put(itemReq.produtoId(), itemReq.precoUnitario());
            }
        }

        // 1) remove o que saiu da lista
        consignacao.getItens().removeIf(i -> !quantidadePorProduto.containsKey(i.getProduto().getId()));

        // 2) atualiza a quantidade dos que ficaram (preço e comissão originais são mantidos)
        Map<Long, ItemConsignacao> existentes = new HashMap<>();
        for (ItemConsignacao item : consignacao.getItens()) {
            existentes.put(item.getProduto().getId(), item);
        }
        for (Map.Entry<Long, Integer> e : quantidadePorProduto.entrySet()) {
            ItemConsignacao existente = existentes.get(e.getKey());
            if (existente != null) {
                // só cobra estoque de quem AUMENTOU: item que ficou igual ou diminuiu não pode
                // travar o salvamento porque o estoque de hoje é menor que a quantidade já enviada
                if (e.getValue() > existente.getQuantidadeEnviada()) {
                    validarEstoque(existente.getProduto(), e.getValue());
                }
                existente.setQuantidadeEnviada(e.getValue());
                // preço da linha: se a tela mandou um preço, vale (é só desta consignação)
                BigDecimal novoPreco = precoInformado.get(e.getKey());
                if (novoPreco != null) {
                    existente.setPrecoUnitario(novoPreco);
                }
            }
        }

        // 3) acrescenta os produtos novos, com o preço e a comissão de hoje
        for (Map.Entry<Long, Integer> e : quantidadePorProduto.entrySet()) {
            if (existentes.containsKey(e.getKey())) continue;
            Produto produto = produtoRepository.findById(e.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: id " + e.getKey()));
            validarEstoque(produto, e.getValue());
            consignacao.getItens().add(ItemConsignacao.builder()
                    .consignacao(consignacao)
                    .produto(produto)
                    .quantidadeEnviada(e.getValue())
                    .quantidadeVendida(0)
                    .precoUnitario(precoInformado.getOrDefault(e.getKey(), produto.getPrecoVenda()))
                    .percentualComissao(percentualComissaoPara(produto))
                    .build());
        }

        consignacao.setConsignatario(request.consignatario().trim());
        consignacao.setPrazoDevolucao(request.prazoDevolucao());
        consignacao.setObservacao(request.observacao());
        return consignacaoRepository.save(consignacao);
    }

    private void validarEstoque(Produto produto, int quantidade) {
        if (produto.getQuantidadeEstoque() < quantidade) {
            throw new BusinessException("Estoque insuficiente para o produto: " + produto.getNome()
                    + " (disponível: " + produto.getQuantidadeEstoque() + ")");
        }
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
        Map<Long, BigDecimal> precoPorItem = new HashMap<>();
        for (FecharConsignacaoRequest.ItemVendido v : request.itens()) {
            vendidoPorItem.put(v.itemId(), v.quantidadeVendida());
            if (v.precoUnitario() != null) {
                precoPorItem.put(v.itemId(), v.precoUnitario());
            }
        }
        for (Long itemId : vendidoPorItem.keySet()) {
            boolean existe = consignacao.getItens().stream().anyMatch(i -> i.getId().equals(itemId));
            if (!existe) {
                throw new BusinessException("Item " + itemId + " não pertence a esta consignação");
            }
        }

        for (ItemConsignacao item : consignacao.getItens()) {
            // preço corrigido na hora do acerto (vale só para esta consignação)
            BigDecimal precoNovo = precoPorItem.get(item.getId());
            if (precoNovo != null) {
                item.setPrecoUnitario(precoNovo);
            }
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

    /**
     * Corrige a conferência de uma consignação JÁ FECHADA: troca as quantidades vendidas (e, se vier, o preço da linha) e ajusta o
     * estoque só pela diferença (vendeu mais = baixa a diferença; vendeu menos = devolve a diferença).
     * Itens que não vierem na lista ficam como estão.
     */
    @Transactional
    public Consignacao corrigirConferencia(Long id, FecharConsignacaoRequest request) {
        Consignacao consignacao = buscarPorId(id);
        if (consignacao.getStatus() != StatusConsignacao.FECHADA) {
            throw new BusinessException("Só é possível corrigir a conferência de uma consignação já fechada.");
        }

        Map<Long, Integer> vendidoPorItem = new HashMap<>();
        Map<Long, BigDecimal> precoPorItem = new HashMap<>();
        for (FecharConsignacaoRequest.ItemVendido v : request.itens()) {
            vendidoPorItem.put(v.itemId(), v.quantidadeVendida());
            if (v.precoUnitario() != null) {
                precoPorItem.put(v.itemId(), v.precoUnitario());
            }
        }
        for (Long itemId : vendidoPorItem.keySet()) {
            boolean existe = consignacao.getItens().stream().anyMatch(i -> i.getId().equals(itemId));
            if (!existe) {
                throw new BusinessException("Item " + itemId + " não pertence a esta consignação");
            }
        }

        for (ItemConsignacao item : consignacao.getItens()) {
            // preço corrigido: não mexe no estoque, só nos valores (vendido, comissão, líquido)
            BigDecimal precoNovo = precoPorItem.get(item.getId());
            if (precoNovo != null) {
                item.setPrecoUnitario(precoNovo);
            }
            Integer nova = vendidoPorItem.get(item.getId());
            if (nova == null) {
                continue;
            }
            Produto produto = item.getProduto();
            if (nova > item.getQuantidadeEnviada()) {
                throw new BusinessException("Quantidade vendida de \"" + produto.getNome()
                        + "\" (" + nova + ") é maior que a enviada (" + item.getQuantidadeEnviada() + ")");
            }
            int diferenca = nova - item.getQuantidadeVendida();
            if (diferenca > 0 && produto.getQuantidadeEstoque() < diferenca) {
                throw new BusinessException("Estoque insuficiente para dar baixa em \"" + produto.getNome()
                        + "\" (estoque: " + produto.getQuantidadeEstoque() + ", a mais vendido: " + diferenca + ")");
            }
            if (diferenca != 0) {
                item.setQuantidadeVendida(nova);
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - diferenca);
                produtoRepository.save(produto);
            }
        }
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
