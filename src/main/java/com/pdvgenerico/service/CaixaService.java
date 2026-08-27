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

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CaixaService {

    private final CaixaRepository caixaRepository;

    public Optional<Caixa> buscarCaixaAberto() {
        return caixaRepository.findByAbertoTrue();
    }

    @Transactional
    public Caixa abrir(CaixaRequest request, Usuario usuarioLogado) {
        if (caixaRepository.findByAbertoTrue().isPresent()) {
            throw new BusinessException("Já existe um caixa aberto. Feche o caixa atual antes de abrir um novo.");
        }

        Caixa caixa = Caixa.builder()
                .usuarioAbertura(usuarioLogado)
                .valorInicial(request.valorInicial())
                .dataAbertura(LocalDateTime.now())
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
        caixa.setDataFechamento(LocalDateTime.now());
        caixa.setAberto(false);

        return caixaRepository.save(caixa);
    }
}
