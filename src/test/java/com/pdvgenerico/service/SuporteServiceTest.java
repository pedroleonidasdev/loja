package com.pdvgenerico.service;

import com.pdvgenerico.dto.ContatoSuporteResponse;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.model.MensagemSuporte;
import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.MensagemSuporteRepository;
import com.pdvgenerico.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

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
}
