package com.pdvgenerico.repository;

import com.pdvgenerico.model.ItemConsignacao;
import com.pdvgenerico.model.StatusConsignacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemConsignacaoRepository extends JpaRepository<ItemConsignacao, Long> {

    /** Itens de um produto em consignações com o status informado (ex.: ABERTA). */
    List<ItemConsignacao> findByProdutoIdAndConsignacaoStatus(Long produtoId, StatusConsignacao status);
}
