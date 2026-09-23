package com.pdvgenerico.security;

import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // em produção esses valores vêm de application.properties (@Value); em teste
        // unitário, sem contexto Spring, setamos direto via reflection.
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "chave-de-teste-com-pelo-menos-32-bytes-para-hs256");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 60_000L);
    }

    private Usuario usuario(String login) {
        return Usuario.builder().login(login).perfil(Perfil.ADMIN).ativo(true).build();
    }

    @Test
    void deveGerarTokenValidoParaOUsuario() {
        Usuario usuario = usuario("admin");

        String token = jwtUtil.generateToken(usuario);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.isTokenValid(token, usuario)).isTrue();
    }

    @Test
    void tokenNaoDeveSerValidoParaOutroUsuario() {
        String token = jwtUtil.generateToken(usuario("admin"));

        assertThat(jwtUtil.isTokenValid(token, usuario("caixa"))).isFalse();
    }

    @Test
    void tokenJaExpiradoNaoDeveSerValido() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1_000L); // expira no passado
        Usuario usuario = usuario("admin");

        String token = jwtUtil.generateToken(usuario);

        assertThat(jwtUtil.isTokenValid(token, usuario)).isFalse();
    }
}
