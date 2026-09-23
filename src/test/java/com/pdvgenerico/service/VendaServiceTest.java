package com.pdvgenerico.service;

import com.pdvgenerico.dto.PagamentoVendaRequest;
import com.pdvgenerico.dto.VendaRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.model.FormaPagamento;
import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Produto;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.model.Venda;
import com.pdvgenerico.repository.ProdutoRepository;
import com.pdvgenerico.repository.UsuarioRepository;
import com.pdvgenerico.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Cobre as regras de negócio mais sensíveis de VendaService: cálculo de
 * desconto, baixa de estoque e a conferência de que a soma dos pagamentos
 * bate exatamente com o total da venda.
 */
@ExtendWith(MockitoExtension.class)
class VendaServiceTest {

    @Mock
    private VendaRepository vendaRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private VendaService vendaService;

    private Usuario usuarioLogado;
    private Produto produto;

    @BeforeEach
    void setUp() {
        usuarioLogado = Usuario.builder()
                .id(1L)
                .login("caixa")
                .perfil(Perfil.CAIXA)
                .ativo(true)
                .build();

        produto = Produto.builder()
                .id(10L)
                .nome("Bíblia de Estudo")
                .precoVenda(new BigDecimal("50.00"))
                .quantidadeEstoque(5)
                .ativo(true)
                .build();

        // devolve sempre o mesmo objeto salvo, como o JPA faria
        when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deveCalcularTotalComDescontoPercentualEEmDinheiro() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(produto));

        VendaRequest request = new VendaRequest(
                null,
                new BigDecimal("10"), // 10% de desconto sobre 100 = 10
                new BigDecimal("5"),  // + R$5 de desconto informado
                List.of(new PagamentoVendaRequest(FormaPagamento.DINHEIRO, new BigDecimal("85.00"))),
                List.of(new VendaRequest.ItemVendaRequest(10L, 2)) // 2 x 50 = 100
        );

        Venda venda = vendaService.registrarVenda(request, usuarioLogado);

        assertThat(venda.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(venda.getValorDesconto()).isEqualByComparingTo("15.00");
        assertThat(venda.getTotal()).isEqualByComparingTo("85.00");
    }

    @Test
    void naoDeveDeixarDescontoTornarTotalNegativo() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(produto));

        // desconto informado (200) maior que o subtotal (100) — não pode "dever" pro cliente
        VendaRequest request = new VendaRequest(
                FormaPagamento.DINHEIRO,
                BigDecimal.ZERO,
                new BigDecimal("200"),
                null,
                List.of(new VendaRequest.ItemVendaRequest(10L, 2))
        );

        Venda venda = vendaService.registrarVenda(request, usuarioLogado);

        assertThat(venda.getValorDesconto()).isEqualByComparingTo("100.00");
        assertThat(venda.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void deveRecusarVendaQuandoEstoqueInsuficiente() {
        produto.setQuantidadeEstoque(1);
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(produto));

        VendaRequest request = new VendaRequest(
                FormaPagamento.DINHEIRO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new VendaRequest.ItemVendaRequest(10L, 2))
        );

        assertThatThrownBy(() -> vendaService.registrarVenda(request, usuarioLogado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void deveDarBaixaNoEstoqueAoRegistrarVenda() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(produto));

        VendaRequest request = new VendaRequest(
                FormaPagamento.PIX, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new VendaRequest.ItemVendaRequest(10L, 3))
        );

        vendaService.registrarVenda(request, usuarioLogado);

        assertThat(produto.getQuantidadeEstoque()).isEqualTo(2);
    }

    @Test
    void deveRecusarQuandoSomaDosPagamentosNaoBateComOTotal() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(produto));

        VendaRequest request = new VendaRequest(
                null, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new PagamentoVendaRequest(FormaPagamento.DINHEIRO, new BigDecimal("30.00"))), // total é 100
                List.of(new VendaRequest.ItemVendaRequest(10L, 2))
        );

        assertThatThrownBy(() -> vendaService.registrarVenda(request, usuarioLogado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("soma dos pagamentos");
    }

    @Test
    void deveRecusarVendaSemFormaDePagamentoInformada() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(produto));

        VendaRequest request = new VendaRequest(
                null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new VendaRequest.ItemVendaRequest(10L, 1))
        );

        assertThatThrownBy(() -> vendaService.registrarVenda(request, usuarioLogado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forma de pagamento");
    }

    @Test
    void deveLancarErroQuandoProdutoNaoExiste() {
        when(produtoRepository.findById(99L)).thenReturn(Optional.empty());

        VendaRequest request = new VendaRequest(
                FormaPagamento.DINHEIRO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new VendaRequest.ItemVendaRequest(99L, 1))
        );

        assertThatThrownBy(() -> vendaService.registrarVenda(request, usuarioLogado))
                .isInstanceOf(com.pdvgenerico.exception.ResourceNotFoundException.class);
    }
}
