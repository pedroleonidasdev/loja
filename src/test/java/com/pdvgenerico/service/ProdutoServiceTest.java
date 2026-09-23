package com.pdvgenerico.service;

import com.pdvgenerico.dto.ProdutoRequest;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.model.Produto;
import com.pdvgenerico.repository.CategoriaRepository;
import com.pdvgenerico.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProdutoService produtoService;

    @Test
    void deveRecusarCriacaoComCodigoDeBarrasJaExistente() {
        when(produtoRepository.findByCodigoBarras("7891234567895"))
                .thenReturn(Optional.of(Produto.builder().id(1L).build()));

        ProdutoRequest request = new ProdutoRequest(
                "Vela Aromática", "7891234567895", null,
                new BigDecimal("15.00"), new BigDecimal("8.00"), 10, 2
        );

        assertThatThrownBy(() -> produtoService.criar(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("código de barras");
    }

    @Test
    void deveListarApenasProdutosComEstoqueIgualOuAbaixoDoMinimo() {
        Produto estoqueBaixo = Produto.builder()
                .id(1L).nome("Terço").quantidadeEstoque(2).estoqueMinimo(5).ativo(true).build();
        Produto estoqueOk = Produto.builder()
                .id(2L).nome("Bíblia").quantidadeEstoque(20).estoqueMinimo(5).ativo(true).build();
        Produto semMinimoDefinido = Produto.builder()
                .id(3L).nome("Vela").quantidadeEstoque(0).estoqueMinimo(null).ativo(true).build();

        when(produtoRepository.findByAtivoTrue())
                .thenReturn(List.of(estoqueBaixo, estoqueOk, semMinimoDefinido));

        List<Produto> resultado = produtoService.listarComEstoqueBaixo();

        assertThat(resultado).containsExactly(estoqueBaixo);
    }

    @Test
    void deveLancarErroAoBuscarProdutoInexistente() {
        when(produtoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.buscarPorId(404L))
                .isInstanceOf(com.pdvgenerico.exception.ResourceNotFoundException.class);
    }
}
