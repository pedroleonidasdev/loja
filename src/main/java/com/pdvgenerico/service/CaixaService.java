package com.pdvgenerico.service;

import com.pdvgenerico.dto.CaixaRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.Caixa;
import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.CaixaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final NotificacaoService notificacaoService;

    // Usa a lista ordenada por data de abertura em vez de findByAbertoTrue()
    // (Optional): se existir mais de um caixa com aberto=true no banco (dado
    // inconsistente), pega o mais recente em vez de quebrar com 500.
    public Optional<Caixa> buscarCaixaAberto() {
        List<Caixa> abertos = caixaRepository.findByAbertoTrueOrderByDataAberturaDesc();
        return abertos.isEmpty() ? Optional.empty() : Optional.of(abertos.get(0));
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


    /**
     * Regra: quem opera o caixa aberto (vender, corrigir abertura, fechar) tem que ser
     * a mesma pessoa que o abriu. O usuário é sempre o do token (JWT) da requisição, nunca
     * o que o frontend diz. ADMIN é exceção, para poder resolver caixa esquecido/de outro
     * turno. Sem caixa aberto não há o que conferir (o PDV já bloqueia a venda).
     */
    @Transactional(readOnly = true)
    public void exigirOperadorDoCaixa(Usuario usuarioLogado) {
        buscarCaixaAberto().ifPresent(caixa -> exigirOperadorDoCaixa(caixa, usuarioLogado));
    }

    private void exigirOperadorDoCaixa(Caixa caixa, Usuario usuarioLogado) {
        if (usuarioLogado.getPerfil() == Perfil.ADMIN) {
            return;
        }
        Usuario quemAbriu = caixa.getUsuarioAbertura();
        boolean mesmo = quemAbriu != null && quemAbriu.getId().equals(usuarioLogado.getId());
        if (!mesmo) {
            String nome = quemAbriu != null ? quemAbriu.getNome() : "outro usuário";
            throw new BusinessException("O caixa foi aberto por " + nome + ". Entre com esse usuário para operar "
                    + "o caixa, ou peça a um administrador.");
        }
    }

    @Transactional
    public Caixa abrir(CaixaRequest request, Usuario usuarioLogado) {
        if (buscarCaixaAberto().isPresent()) {
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

        Caixa caixaSalvo = caixaRepository.save(caixa);

        notificacaoService.notificarAdmins(
                "Caixa aberto",
                usuarioLogado.getNome() + " abriu o caixa com R$ " + request.valorInicial(),
                "/pdv",
                null // notifica todo ADMIN, inclusive quem fez a acao
        );

        return caixaSalvo;
    }

    /**
     * Corrige o valor de abertura de um caixa AINDA ABERTO (ex.: digitou 100 em vez
     * de 10). Guarda o valor original na primeira correção, registra quem corrigiu e
     * quando, e avisa os administradores com o valor antigo e o novo. Em caixa já
     * fechado não é permitido — um ADMIN precisa reabri-lo antes (tela Reabrir Caixa).
     */
    @Transactional
    public Caixa editarValorInicial(Long id, CaixaRequest.EdicaoAberturaRequest request, Usuario usuarioLogado) {
        Caixa caixa = caixaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caixa não encontrado."));

        if (!caixa.isAberto()) {
            throw new BusinessException(
                    "Só é possível corrigir o valor de abertura de um caixa aberto. Peça a um administrador para reabrir o caixa.");
        }
        exigirOperadorDoCaixa(caixa, usuarioLogado);

        BigDecimal anterior = caixa.getValorInicial();
        BigDecimal novo = request.valorInicial();
        if (anterior.compareTo(novo) == 0) {
            throw new BusinessException("O novo valor é igual ao valor de abertura atual.");
        }

        if (caixa.getValorInicialOriginal() == null) {
            caixa.setValorInicialOriginal(anterior);
        }
        caixa.setValorInicial(novo);
        caixa.setUsuarioEdicaoAbertura(usuarioLogado);
        caixa.setDataEdicaoAbertura(LocalDateTime.now(ZoneOffset.UTC));

        Caixa caixaSalvo = caixaRepository.save(caixa);

        notificacaoService.notificarAdmins(
                "Abertura de caixa corrigida",
                usuarioLogado.getNome() + " corrigiu o valor de abertura de R$ " + anterior + " para R$ " + novo,
                "/relatorios",
                usuarioLogado.getId()
        );

        return caixaSalvo;
    }

    @Transactional
    public Caixa fechar(CaixaRequest.FechamentoRequest request, Usuario usuarioLogado) {
        Caixa caixa = buscarCaixaAberto()
                .orElseThrow(() -> new ResourceNotFoundException("Não há caixa aberto no momento."));
        exigirOperadorDoCaixa(caixa, usuarioLogado);

        caixa.setUsuarioFechamento(usuarioLogado);
        caixa.setValorFinal(request.valorFinal());
        caixa.setDataFechamento(LocalDateTime.now(ZoneOffset.UTC));
        caixa.setAberto(false);

        Caixa caixaSalvo = caixaRepository.save(caixa);

        notificacaoService.notificarAdmins(
                "Caixa fechado",
                usuarioLogado.getNome() + " fechou o caixa com R$ " + request.valorFinal(),
                "/relatorios",
                null // notifica todo ADMIN, inclusive quem fez a acao
        );

        return caixaSalvo;
    }

    // Reabertura é restrita a ADMIN (ver @PreAuthorize no controller) e só é
    // permitida para caixas fechados hoje ou ontem (fuso de Brasília) — evita
    // reabrir algo muito antigo e desalinhar o histórico/relatórios já fechados.
    @Transactional
    public Caixa reabrir(Long id, Usuario usuarioLogado) {
        if (buscarCaixaAberto().isPresent()) {
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

        Caixa caixaSalvo = caixaRepository.save(caixa);

        notificacaoService.notificarAdmins(
                "Caixa reaberto",
                usuarioLogado.getNome() + " reabriu o caixa #" + caixa.getId(),
                "/pdv",
                usuarioLogado.getId()
        );

        return caixaSalvo;
    }
}
