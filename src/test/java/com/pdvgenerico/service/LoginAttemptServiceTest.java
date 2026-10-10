package com.pdvgenerico.service;

import com.pdvgenerico.exception.TentativasExcedidasException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    // relógio controlável, pra simular a passagem do tempo sem esperar de verdade
    private static class RelogioFalso extends Clock {
        private Instant agora = Instant.parse("2026-01-01T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private RelogioFalso relogio;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        relogio = new RelogioFalso();
        service = new LoginAttemptService(relogio);
    }

    private void errarSenha(String login, int vezes) {
        for (int i = 0; i < vezes; i++) {
            service.registrarFalha(login);
        }
    }

    @Test
    void quatroFalhasAindaNaoBloqueiam() {
        errarSenha("emerson", 4);

        assertThatCode(() -> service.verificarBloqueio("emerson")).doesNotThrowAnyException();
    }

    @Test
    void quintaFalhaBloqueiaOLogin() {
        errarSenha("emerson", 5);

        assertThatThrownBy(() -> service.verificarBloqueio("emerson"))
                .isInstanceOf(TentativasExcedidasException.class)
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    void bloqueioExpiraDepoisDeCincoMinutos() {
        errarSenha("emerson", 5);
        relogio.avancar(Duration.ofMinutes(5).plusSeconds(1));

        assertThatCode(() -> service.verificarBloqueio("emerson")).doesNotThrowAnyException();
    }

    @Test
    void loginCorretoZeraOContador() {
        errarSenha("emerson", 4);
        service.registrarSucesso("emerson");
        errarSenha("emerson", 4);

        assertThatCode(() -> service.verificarBloqueio("emerson")).doesNotThrowAnyException();
    }

    @Test
    void falhasForaDaJanelaNaoSeAcumulam() {
        errarSenha("emerson", 4);
        relogio.avancar(Duration.ofMinutes(16));
        errarSenha("emerson", 4);

        assertThatCode(() -> service.verificarBloqueio("emerson")).doesNotThrowAnyException();
    }

    @Test
    void loginsDiferentesSaoIndependentes() {
        errarSenha("emerson", 5);

        assertThatCode(() -> service.verificarBloqueio("admin")).doesNotThrowAnyException();
    }

    @Test
    void maiusculasEEspacosNaoBurlamOBloqueio() {
        errarSenha("Emerson", 5);

        assertThatThrownBy(() -> service.verificarBloqueio("  emerson "))
                .isInstanceOf(TentativasExcedidasException.class);
    }
}
