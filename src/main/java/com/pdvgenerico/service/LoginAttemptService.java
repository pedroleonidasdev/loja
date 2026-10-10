package com.pdvgenerico.service;

import com.pdvgenerico.exception.TentativasExcedidasException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freio contra tentativas repetidas de senha: depois de MAX_FALHAS erros
 * dentro da JANELA, aquele login fica bloqueado por BLOQUEIO. Fica só em
 * memória (zera se o servidor reiniciar) — suficiente pra barrar tentativa
 * de adivinhar senha sem precisar de tabela nova no banco.
 *
 * O bloqueio é por nome de login, não por IP (atrás do proxy do Render o IP
 * real não é confiável). Contrapartida: alguém mal-intencionado consegue
 * deixar um login travado por alguns minutos de propósito — aceitável pra
 * um sistema de loja, e bem melhor do que permitir tentativas ilimitadas.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FALHAS = 5;
    static final Duration JANELA = Duration.ofMinutes(15);
    static final Duration BLOQUEIO = Duration.ofMinutes(5);
    private static final int LIMITE_ENTRADAS = 1000;

    private static final class Estado {
        int falhas;
        Instant primeiraFalha;
        Instant bloqueadoAte;
    }

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // usado nos testes, pra controlar a passagem do tempo
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Lança TentativasExcedidasException se este login estiver bloqueado agora. */
    public void verificarBloqueio(String login) {
        String chave = chave(login);
        Estado estado = estados.get(chave);
        if (estado == null) return;

        synchronized (estado) {
            if (estado.bloqueadoAte == null) return;
            Instant agora = clock.instant();
            if (agora.isBefore(estado.bloqueadoAte)) {
                throw new TentativasExcedidasException(Duration.between(agora, estado.bloqueadoAte).toSeconds());
            }
            // o bloqueio já acabou: começa do zero
            estados.remove(chave, estado);
        }
    }

    public void registrarFalha(String login) {
        limparExpirados();
        Estado estado = estados.computeIfAbsent(chave(login), k -> new Estado());

        synchronized (estado) {
            Instant agora = clock.instant();
            if (estado.primeiraFalha == null || agora.isAfter(estado.primeiraFalha.plus(JANELA))) {
                estado.falhas = 0;
                estado.primeiraFalha = agora;
            }
            estado.falhas++;
            if (estado.falhas >= MAX_FALHAS) {
                estado.bloqueadoAte = agora.plus(BLOQUEIO);
                estado.falhas = 0;
                estado.primeiraFalha = null;
            }
        }
    }

    public void registrarSucesso(String login) {
        estados.remove(chave(login));
    }

    private String chave(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }

    // evita o mapa crescer sem limite se alguém ficar tentando milhares de logins diferentes
    private void limparExpirados() {
        if (estados.size() <= LIMITE_ENTRADAS) return;
        Instant agora = clock.instant();
        estados.entrySet().removeIf(entrada -> {
            Estado estado = entrada.getValue();
            synchronized (estado) {
                boolean bloqueado = estado.bloqueadoAte != null && agora.isBefore(estado.bloqueadoAte);
                boolean contando = estado.primeiraFalha != null && !agora.isAfter(estado.primeiraFalha.plus(JANELA));
                return !bloqueado && !contando;
            }
        });
    }
}
