package com.pdvgenerico.exception;

/** Lançada quando um login erra a senha vezes demais seguidas e fica bloqueado por um tempo. */
public class TentativasExcedidasException extends RuntimeException {

    private final long segundosRestantes;

    public TentativasExcedidasException(long segundosRestantes) {
        super("Muitas tentativas de login. Aguarde " + formatar(segundosRestantes) + " e tente novamente.");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }

    private static String formatar(long segundos) {
        long minutos = (segundos + 59) / 60;
        return minutos <= 1 ? "1 minuto" : minutos + " minutos";
    }
}
