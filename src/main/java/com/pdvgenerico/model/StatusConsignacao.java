package com.pdvgenerico.model;

public enum StatusConsignacao {
    /** Mercadoria está com o consignatário; o estoque do sistema ainda não foi alterado. */
    ABERTA,
    /** Acerto feito: a quantidade vendida já deu baixa no estoque. */
    FECHADA
}
