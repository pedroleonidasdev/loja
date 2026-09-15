package com.pdvgenerico.service;

import com.pdvgenerico.dto.DespesaRequest;
import com.pdvgenerico.dto.EditarDespesaRequest;
import com.pdvgenerico.dto.ParcelaRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.model.*;
import com.pdvgenerico.repository.DespesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final CaixaService caixaService;

    public List<Despesa> listarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return despesaRepository.findByDataHoraBetweenOrderByDataHoraDesc(inicio, fim);
    }

    public List<Despesa> listarTodas() {
        return despesaRepository.findAllByOrderByDataHoraDesc();
    }

    @Transactional
    public Despesa registrar(DespesaRequest request, Usuario usuarioLogado) {
        // SANGRIA e SUPRIMENTO são sempre movimento físico de dinheiro na gaveta,
        // então só fazem sentido com um caixa aberto no momento.
        boolean movimentoDeCaixa = request.tipo() != TipoDespesa.DESPESA;

        Optional<Caixa> caixaAberto = caixaService.buscarCaixaAberto();

        if (movimentoDeCaixa && caixaAberto.isEmpty()) {
            throw new BusinessException(
                    "Não há caixa aberto no momento. Sangria e suprimento só podem ser lançados com o caixa aberto.");
        }

        // força DINHEIRO para sangria/suprimento, ignorando o que vier no request —
        // essas duas operações são sempre físicas, na gaveta.
        FormaPagamento formaPagamento = movimentoDeCaixa ? FormaPagamento.DINHEIRO : request.formaPagamento();

        if (formaPagamento == null) {
            throw new BusinessException("Informe a forma de pagamento da despesa.");
        }

        if (request.tipo() == TipoDespesa.DESPESA && (request.categoria() == null || request.categoria().isBlank())) {
            throw new BusinessException("Informe a categoria da despesa (ex: Aluguel, Fornecedor, Energia).");
        }

        List<Parcela> parcelas = construirParcelas(request.parcelas());

        Despesa despesa = Despesa.builder()
                .tipo(request.tipo())
                .categoria(request.categoria())
                .fornecedor(request.fornecedor())
                .descricao(request.descricao())
                .valor(request.valor())
                .formaPagamento(formaPagamento)
                .dataHora(LocalDateTime.now(ZoneOffset.UTC))
                // só faz sentido preencher pra DESPESA à vista — pra parcelada, o vencimento
                // é por parcela (Parcela.dataVencimento); pra sangria/suprimento não existe
                // "vencimento", então ignoramos o que vier no request.
                .dataVencimento(!movimentoDeCaixa && parcelas.isEmpty() ? request.dataVencimento() : null)
                .usuario(usuarioLogado)
                // só vincula ao caixa quando o dinheiro realmente sai/entra da gaveta —
                // isso é o que a conferência de caixa em Relatórios usa depois
                .caixa(formaPagamento == FormaPagamento.DINHEIRO ? caixaAberto.orElse(null) : null)
                // Integer.valueOf() nos dois lados do ternário: se misturar int com Integer
                // aqui, o Java sempre faz unboxing do lado Integer (mesmo quando não é o
                // escolhido em tempo de execução) — se vier null (à vista, sem parcelas), estoura NPE.
                .numeroParcelas(!parcelas.isEmpty() ? Integer.valueOf(parcelas.size()) : request.numeroParcelas())
                .parcelas(parcelas)
                .build();

        return despesaRepository.save(despesa);
    }

    /**
     * Corrige categoria, fornecedor, descrição, valor, forma de pagamento e
     * parcelamento de um lançamento já registrado. O tipo do lançamento e o
     * vínculo com o caixa em que foi aberto não mudam aqui — ver EditarDespesaRequest.
     */
    @Transactional
    public Despesa editar(Long id, EditarDespesaRequest request) {
        Despesa despesa = despesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada."));

        boolean movimentoDeCaixa = despesa.getTipo() != TipoDespesa.DESPESA;
        FormaPagamento formaPagamento = movimentoDeCaixa ? FormaPagamento.DINHEIRO : request.formaPagamento();

        if (formaPagamento == null) {
            throw new BusinessException("Informe a forma de pagamento da despesa.");
        }
        if (despesa.getTipo() == TipoDespesa.DESPESA && (request.categoria() == null || request.categoria().isBlank())) {
            throw new BusinessException("Informe a categoria da despesa (ex: Aluguel, Fornecedor, Energia).");
        }

        List<Parcela> parcelas = construirParcelas(request.parcelas());

        despesa.setCategoria(request.categoria());
        despesa.setFornecedor(request.fornecedor());
        despesa.setDescricao(request.descricao());
        despesa.setValor(request.valor());
        despesa.setFormaPagamento(formaPagamento);
        // mesma regra do registrar(): só à vista guarda vencimento no próprio
        // lançamento; parcelada usa o vencimento de cada parcela.
        despesa.setDataVencimento(!movimentoDeCaixa && parcelas.isEmpty() ? request.dataVencimento() : null);
        // mesmo cuidado do registrar(): Integer.valueOf() nos dois lados evita NPE
        // de unboxing quando request.numeroParcelas() vem null (à vista).
        despesa.setNumeroParcelas(!parcelas.isEmpty() ? Integer.valueOf(parcelas.size()) : request.numeroParcelas());

        // limpa a coleção existente em vez de trocar a referência: é o jeito
        // seguro de fazer o Hibernate apagar as linhas antigas de despesa_parcelas
        // (@ElementCollection) antes de gravar as novas.
        despesa.getParcelas().clear();
        despesa.getParcelas().addAll(parcelas);

        return despesaRepository.save(despesa);
    }

    /**
     * Marca/desmarca como pago um lançamento à vista (sem parcelas detalhadas).
     * Para lançamentos parcelados, use marcarParcelaPaga por parcela — este
     * método recusa parcelados de propósito pra não deixar o "pago" geral e o
     * das parcelas dessincronizarem.
     */
    @Transactional
    public Despesa marcarPago(Long id, boolean pago) {
        Despesa despesa = despesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada."));

        if (!despesa.getParcelas().isEmpty()) {
            throw new BusinessException(
                    "Esse lançamento é parcelado — marque cada parcela como paga individualmente.");
        }

        despesa.setPago(pago);
        return despesaRepository.save(despesa);
    }

    /**
     * Marca/desmarca como paga uma parcela específica de um lançamento parcelado.
     */
    @Transactional
    public Despesa marcarParcelaPaga(Long id, Integer numeroParcela, boolean pago) {
        Despesa despesa = despesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada."));

        Parcela parcela = despesa.getParcelas().stream()
                .filter(p -> p.getNumero().equals(numeroParcela))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada."));

        parcela.setPago(pago);
        return despesaRepository.save(despesa);
    }

    private List<Parcela> construirParcelas(List<ParcelaRequest> parcelasRequest) {
        List<Parcela> parcelas = new ArrayList<>();
        if (parcelasRequest == null) {
            return parcelas;
        }
        int numero = 1;
        for (ParcelaRequest p : parcelasRequest) {
            parcelas.add(Parcela.builder()
                    .numero(numero++)
                    .dataVencimento(p.dataVencimento())
                    .valor(p.valor())
                    .build());
        }
        return parcelas;
    }

    @Transactional
    public void excluir(Long id) {
        if (!despesaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Despesa não encontrada.");
        }
        despesaRepository.deleteById(id);
    }
}
