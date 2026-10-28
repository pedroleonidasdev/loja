package com.pdvgenerico.service;

import com.pdvgenerico.dto.ContatoSuporteResponse;
import com.pdvgenerico.exception.ResourceNotFoundException;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.model.MensagemSuporte;
import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.MensagemSuporteRepository;
import com.pdvgenerico.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuporteServiceTest {

    private final MensagemSuporteRepository repository = mock(MensagemSuporteRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final SuporteService service = new SuporteService(repository, usuarioRepository);

    private final Usuario eu = usuario(1L, "pedro", "Pedro", true);
    private final Usuario outro = usuario(2L, "padrinho", "Padrinho", true);

    private static Usuario usuario(Long id, String login, String nome, boolean ativo) {
        return Usuario.builder().id(id).login(login).nome(nome).perfil(Perfil.ADMIN).ativo(ativo).build();
    }

    private void salvarDevolvendoComId(long id) {
        when(repository.save(any(MensagemSuporte.class))).thenAnswer(inv -> {
            MensagemSuporte m = inv.getArgument(0);
            m.setId(id);
            return m;
        });
    }

    @Test
    void deveEnviarParaOOutroUsuarioSemEspacosSobrando() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));
        salvarDevolvendoComId(1L);

        MensagemSuporte m = service.enviar(eu, 2L, "  oi  ");

        assertThat(m.getTexto()).isEqualTo("oi");
        assertThat(m.getRemetenteId()).isEqualTo(1L);
        assertThat(m.getDestinatarioId()).isEqualTo(2L);
        assertThat(m.isLida()).isFalse();
    }

    @Test
    void naoDeveAceitarMensagemSoComEspacos() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));

        assertThatThrownBy(() -> service.enviar(eu, 2L, "   ")).isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void naoDeveConversarConsigoMesmo() {
        assertThatThrownBy(() -> service.enviar(eu, 1L, "oi")).isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void naoDeveConversarComUsuarioInativo() {
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario(3L, "ex", "Ex", false)));

        assertThatThrownBy(() -> service.enviar(eu, 3L, "oi")).isInstanceOf(BusinessException.class);
    }

    @Test
    void semUltimoIdDeveDevolverAsRecentesEmOrdemCronologica() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));
        MensagemSuporte nova = MensagemSuporte.builder().id(2L).build();
        MensagemSuporte antiga = MensagemSuporte.builder().id(1L).build();
        when(repository.recentes(eq(1L), eq(2L), any(Pageable.class))).thenReturn(List.of(nova, antiga));

        assertThat(service.listar(eu, 2L, null)).extracting(MensagemSuporte::getId).containsExactly(1L, 2L);
    }

    @Test
    void comUltimoIdDeveBuscarSoAsMaisNovasDaConversa() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));
        when(repository.depois(eq(1L), eq(2L), eq(5L), any(Pageable.class)))
                .thenReturn(List.of(MensagemSuporte.builder().id(6L).build()));

        assertThat(service.listar(eu, 2L, 5L)).extracting(MensagemSuporte::getId).containsExactly(6L);
        verify(repository, never()).recentes(any(), any(), any());
    }

    @Test
    void contatosDevemExcluirOProprioUsuarioEOsInativosEContarNaoLidas() {
        when(usuarioRepository.findAll()).thenReturn(List.of(eu, outro, usuario(3L, "ex", "Ex", false)));
        when(repository.contarNaoLidasPorRemetente(1L)).thenReturn(List.<Object[]>of(new Object[]{2L, 3L}));

        List<ContatoSuporteResponse> contatos = service.contatos(eu);

        assertThat(contatos).hasSize(1);
        assertThat(contatos.get(0).id()).isEqualTo(2L);
        assertThat(contatos.get(0).naoLidas()).isEqualTo(3L);
    }

    @Test
    void marcarComoLidasDeveAtingirSoMensagensRecebidasDaqueleContato() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));

        service.marcarComoLidas(eu, 2L);

        verify(repository).marcarComoLidas(1L, 2L);
    }

    // ---------- funções estilo WhatsApp ----------

    private MensagemSuporte mensagem(Long id, Long remetente, Long destinatario, String texto, Instant criadaEm) {
        return MensagemSuporte.builder()
                .id(id).remetenteId(remetente).destinatarioId(destinatario).texto(texto).criadaEm(criadaEm).build();
    }

    @Test
    void digitandoDeveAparecerParaOOutroLadoEPassarSozinho() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(eu));

        service.avisarDigitando(eu, 2L);

        assertThat(service.status(outro, 1L).digitando()).isTrue();   // o outro vê que eu estou digitando
        assertThat(service.status(eu, 2L).digitando()).isFalse();     // eu não vejo a mim mesmo
    }

    @Test
    void deveEnviarRespostaCitandoMensagemDaMesmaConversa() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));
        when(repository.findById(7L)).thenReturn(Optional.of(mensagem(7L, 2L, 1L, "oi", Instant.now())));
        salvarDevolvendoComId(8L);

        MensagemSuporte m = service.enviar(eu, 2L, "tudo bem", null, 7L);

        assertThat(m.getRespostaAId()).isEqualTo(7L);
    }

    @Test
    void naoDeveCitarMensagemDeOutraConversa() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outro));
        when(repository.findById(9L)).thenReturn(Optional.of(mensagem(9L, 5L, 6L, "alheia", Instant.now())));

        assertThatThrownBy(() -> service.enviar(eu, 2L, "oi", null, 9L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void reacaoDeveFicarNoLadoDeQuemReagiu() {
        when(repository.findById(7L)).thenReturn(Optional.of(mensagem(7L, 2L, 1L, "oi", Instant.now())));
        when(repository.save(any(MensagemSuporte.class))).thenAnswer(inv -> inv.getArgument(0));

        MensagemSuporte m = service.reagir(eu, 7L, "\uD83D\uDC4D");

        assertThat(m.getReacaoDestinatario()).isEqualTo("\uD83D\uDC4D");
        assertThat(m.getReacaoRemetente()).isNull();
    }

    @Test
    void reacaoComTextoDeveSerRecusada() {
        when(repository.findById(7L)).thenReturn(Optional.of(mensagem(7L, 2L, 1L, "oi", Instant.now())));

        assertThatThrownBy(() -> service.reagir(eu, 7L, "abc")).isInstanceOf(BusinessException.class);
    }

    @Test
    void soDeveEditarMensagemPropriaDeTextoRecente() {
        when(repository.findById(7L)).thenReturn(Optional.of(mensagem(7L, 1L, 2L, "oi", Instant.now())));
        when(repository.findById(8L)).thenReturn(Optional.of(mensagem(8L, 2L, 1L, "dele", Instant.now())));
        when(repository.findById(9L)).thenReturn(Optional.of(
                mensagem(9L, 1L, 2L, "velha", Instant.now().minus(Duration.ofMinutes(30)))));
        when(repository.save(any(MensagemSuporte.class))).thenAnswer(inv -> inv.getArgument(0));

        MensagemSuporte editada = service.editar(eu, 7L, "  oi, tudo bem?  ");
        assertThat(editada.getTexto()).isEqualTo("oi, tudo bem?");
        assertThat(editada.isEditada()).isTrue();

        assertThatThrownBy(() -> service.editar(eu, 8L, "x")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.editar(eu, 9L, "x")).isInstanceOf(BusinessException.class);
    }

    @Test
    void apagarParaTodosDeveLimparOConteudoSoDeQuemEnviou() {
        MensagemSuporte minha = mensagem(7L, 1L, 2L, "segredo", Instant.now());
        MensagemSuporte dele = mensagem(8L, 2L, 1L, "dele", Instant.now());
        when(repository.findById(7L)).thenReturn(Optional.of(minha));
        when(repository.findById(8L)).thenReturn(Optional.of(dele));

        service.apagarMensagem(eu, 7L, true);

        assertThat(minha.isApagadaParaTodos()).isTrue();
        assertThat(minha.getTexto()).isEmpty();
        assertThatThrownBy(() -> service.apagarMensagem(eu, 8L, true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void apagarParaMimDeveEsconderSoDoMeuLado() {
        MensagemSuporte recebida = mensagem(8L, 2L, 1L, "dele", Instant.now());
        when(repository.findById(8L)).thenReturn(Optional.of(recebida));

        service.apagarMensagem(eu, 8L, false);

        assertThat(recebida.isApagadaDestinatario()).isTrue();
        assertThat(recebida.isApagadaRemetente()).isFalse();
        verify(repository, never()).delete(any(MensagemSuporte.class));
    }

    @Test
    void mensagemEscondidaPorMimNaoDeveSerEncontrada() {
        MensagemSuporte escondida = mensagem(8L, 2L, 1L, "dele", Instant.now());
        escondida.setApagadaDestinatario(true);
        when(repository.findById(8L)).thenReturn(Optional.of(escondida));

        assertThatThrownBy(() -> service.reagir(eu, 8L, "\uD83D\uDC4D")).isInstanceOf(ResourceNotFoundException.class);
    }
}
