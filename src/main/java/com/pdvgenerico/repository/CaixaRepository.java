package com.pdvgenerico.repository;

import com.pdvgenerico.model.Caixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    // Não usar Optional aqui: se por qualquer motivo existir mais de um caixa
    // com aberto=true no banco, Optional<Caixa> faz o Spring Data lançar
    // IncorrectResultSizeDataAccessException (vira 500 em qualquer endpoint
    // que dependa de caixa aberto, inclusive lançar uma Despesa comum).
    List<Caixa> findByAbertoTrueOrderByDataAberturaDesc();

    List<Caixa> findByDataAberturaBetweenOrderByDataAberturaDesc(LocalDateTime inicio, LocalDateTime fim);

    // usado pela tela de Reabrir Caixa (só ADMIN): caixas fechados na janela de
    // hoje/ontem, candidatos a reabertura
    List<Caixa> findByDataFechamentoBetweenAndAbertoFalseOrderByDataFechamentoDesc(
            LocalDateTime inicio, LocalDateTime fim
    );
}
