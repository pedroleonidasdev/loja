package com.pdvgenerico.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Troca a categoria de vários produtos de uma vez. categoriaId nulo = "sem categoria". */
public record ProdutoCategoriaLoteRequest(
        @NotEmpty List<Long> produtoIds,
        Long categoriaId
) {
}
