package com.pdvgenerico.repository;

import com.pdvgenerico.model.Consignacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsignacaoRepository extends JpaRepository<Consignacao, Long> {

    List<Consignacao> findAllByOrderByDataHoraDesc();
}
