package com.pdvgenerico.controller;

import com.pdvgenerico.dto.ContatoSuporteResponse;
import com.pdvgenerico.dto.EditarMensagemRequest;
import com.pdvgenerico.dto.MensagemSuporteRequest;
import com.pdvgenerico.dto.MensagemSuporteResponse;
import com.pdvgenerico.dto.ReacaoRequest;
import com.pdvgenerico.dto.StatusConversaResponse;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.AnexoSuporteService;
import com.pdvgenerico.service.AudioSuporteService;
import com.pdvgenerico.service.SuporteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chat privado entre usuários logados (ADMIN e CAIXA). Cada conversa é só de dois. */
@RestController
@RequestMapping("/api/suporte")
@RequiredArgsConstructor
public class SuporteController {

    private final SuporteService suporteService;
    private final AudioSuporteService audioSuporteService;
    private final AnexoSuporteService anexoSuporteService;

    @GetMapping("/contatos")
    public List<ContatoSuporteResponse> contatos(@AuthenticationPrincipal Usuario eu) {
        return suporteService.contatos(eu);
    }

    @GetMapping("/conversas/{outroId}/mensagens")
    public List<MensagemSuporteResponse> listar(@AuthenticationPrincipal Usuario eu,
                                                @PathVariable Long outroId,
                                                @RequestParam(required = false) Long depois) {
        return suporteService.comRespostas(suporteService.listar(eu, outroId, depois));
    }

    @PostMapping("/conversas/{outroId}/mensagens")
    public MensagemSuporteResponse enviar(@AuthenticationPrincipal Usuario eu,
                                          @PathVariable Long outroId,
                                          @Valid @RequestBody MensagemSuporteRequest request) {
        return suporteService.comResposta(
                suporteService.enviar(eu, outroId, request.texto(), request.tipo(), request.respostaAId()));
    }

    /** Aviso de "digitando…": o frontend chama enquanto a pessoa escreve; vale por poucos segundos. */
    @PostMapping("/conversas/{outroId}/digitando")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void digitando(@AuthenticationPrincipal Usuario eu, @PathVariable Long outroId) {
        suporteService.avisarDigitando(eu, outroId);
    }

    /** Estado do outro lado: digitando, online e há quanto tempo foi visto. */
    @GetMapping("/conversas/{outroId}/status")
    public StatusConversaResponse status(@AuthenticationPrincipal Usuario eu, @PathVariable Long outroId) {
        return suporteService.status(eu, outroId);
    }

    /** Reage a uma mensagem com um emoji (emoji vazio remove a reação). */
    @PutMapping("/mensagens/{mensagemId}/reacao")
    public MensagemSuporteResponse reagir(@AuthenticationPrincipal Usuario eu,
                                          @PathVariable Long mensagemId,
                                          @Valid @RequestBody ReacaoRequest request) {
        return suporteService.comResposta(suporteService.reagir(eu, mensagemId, request.emoji()));
    }

    /** Edita o texto de uma mensagem sua (primeiros 15 minutos). */
    @PutMapping("/mensagens/{mensagemId}")
    public MensagemSuporteResponse editar(@AuthenticationPrincipal Usuario eu,
                                          @PathVariable Long mensagemId,
                                          @Valid @RequestBody EditarMensagemRequest request) {
        return suporteService.comResposta(suporteService.editar(eu, mensagemId, request.texto()));
    }

    /** Apaga uma mensagem: só para mim ou, se for minha e recente, para todos. */
    @DeleteMapping("/mensagens/{mensagemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarMensagem(@AuthenticationPrincipal Usuario eu,
                               @PathVariable Long mensagemId,
                               @RequestParam(defaultValue = "false") boolean paraTodos) {
        suporteService.apagarMensagem(eu, mensagemId, paraTodos);
        audioSuporteService.limparAudios(); // áudios das mensagens que sumiram de vez
        anexoSuporteService.limparAnexos(); // arquivos das mensagens que sumiram de vez
    }

    @PostMapping("/conversas/{outroId}/lidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLidas(@AuthenticationPrincipal Usuario eu, @PathVariable Long outroId) {
        suporteService.marcarComoLidas(eu, outroId);
    }

    /** Apaga a conversa só para quem pediu; a outra pessoa continua vendo o histórico dela. */
    @DeleteMapping("/conversas/{outroId}/mensagens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarConversa(@AuthenticationPrincipal Usuario eu, @PathVariable Long outroId) {
        suporteService.apagarConversa(eu, outroId);
        audioSuporteService.limparAudios(); // áudios das mensagens que sumiram de vez
        anexoSuporteService.limparAnexos(); // arquivos das mensagens que sumiram de vez
    }
}
