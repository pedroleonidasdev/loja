package com.pdvgenerico.service;

import com.pdvgenerico.dto.CaixaRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.Caixa;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.CaixaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CaixaService {

    // Brasília = UTC-3 (sem horário de verão desde 2019) — mesmo padrão usado no frontend
    private static final int OFFSET_BRASILIA_HORAS = 3;

    private final CaixaRepository caixaRepository;

    public Optional<Caixa> buscarCaixaAberto() {
        return caixaRepository.findByAbertoTrue();
    }

    // usado pela tela de Reabrir Caixa: lista os fechados hoje/ontem (mesma janela
    // que o service aceita reabrir), pra o ADMIN escolher qual reabrir
    public List<Caixa> listarFechadosRecentes() {
        LocalDateTime agoraUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate hojeBrasilia = agoraUtc.minusHours(OFFSET_BRASILIA_HORAS).toLocalDate();
        LocalDateTime inicioJanela = hojeBrasilia.minusDays(1).atStartOfDay().plusHours(OFFSET_BRASILIA_HORAS);
        LocalDateTime fimJanela = hojeBrasilia.plusDays(1).atStartOfDay().plusHours(OFFSET_BRASILIA_HORAS);

        return caixaRepository.findByDataFechamentoBetweenAndAbertoFalseOrderByDataFechamentoDesc(
                inicioJanela, fimJanela
        );
    }

    public List<Caixa> listarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return caixaRepository.findByDataAberturaBetweenOrderByDataAberturaDesc(inicio, fim);
    }

    @Transactional
    public Caixa abrir(CaixaRequest request, Usuario usuarioLogado) {
        if (caixaRepository.findByAbertoTrue().isPresent()) {
            throw new BusinessException("Já existe um caixa aberto. Feche o caixa atual antes de abrir um novo.");
        }

        Caixa caixa = Caixa.builder()
                .usuarioAbertura(usuarioLogado)
                .valorInicial(request.valorInicial())
                // grava sempre em UTC "cru", independente do fuso da máquina/servidor que roda o
                // backend — o frontend converte isso para o fuso de Brasília na hora de exibir.
                .dataAbertura(LocalDateTime.now(ZoneOffset.UTC))
                .aberto(true)
                .build();

        return caixaRepository.save(caixa);
    }

    @Transactional
    public Caixa fechar(CaixaRequest.FechamentoRequest request, Usuario usuarioLogado) {
        Caixa caixa = caixaRepository.findByAbertoTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Não há caixa aberto no momento."));

        caixa.setUsuarioFechamento(usuarioLogado);
        caixa.setValorFinal(request.valorFinal());
        caixa.setDataFechamento(LocalDateTime.now(ZoneOffset.UTC));
        caixa.setAberto(false);

        return caixaRepository.save(caixa);
    }

    // Reabertura é restrita a ADMIN (ver @PreAuthorize no controller) e só é
    // permitida para caixas fechados hoje ou ontem (fuso de Brasília) — evita
    // reabrir algo muito antigo e desalinhar o histórico/relatórios já fechados.
    @Transactional
    public Caixa reabrir(Long id, Usuario usuarioLogado) {
        if (caixaRepository.findByAbertoTrue().isPresent()) {
            throw new BusinessException("Já existe um caixa aberto. Feche o caixa atual antes de reabrir outro.");
        }

        Caixa caixa = caixaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caixa não encontrado."));

        if (caixa.isAberto()) {
            throw new BusinessException("Este caixa já está aberto.");
        }
        if (caixa.getDataFechamento() == null) {
            throw new BusinessException("Este caixa não tem data de fechamento registrada.");
        }

        LocalDate hojeBrasilia = LocalDateTime.now(ZoneOffset.UTC).minusHours(OFFSET_BRASILIA_HORAS).toLocalDate();
        LocalDate fechamentoBrasilia = caixa.getDataFechamento().minusHours(OFFSET_BRASILIA_HORAS).toLocalDate();

        if (fechamentoBrasilia.isBefore(hojeBrasilia.minusDays(1)) || fechamentoBrasilia.isAfter(hojeBrasilia)) {
            throw new BusinessException("Só é possível reabrir caixas fechados hoje ou ontem.");
        }

        caixa.setAberto(true);
        caixa.setDataFechamento(null);
        caixa.setValorFinal(null);
        caixa.setUsuarioFechamento(null);
        caixa.setUsuarioReabertura(usuarioLogado);
        caixa.setDataReabertura(LocalDateTime.now(ZoneOffset.UTC));

        return caixaRepository.save(caixa);
    }
}
