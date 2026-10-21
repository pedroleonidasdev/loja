package com.pdvgenerico.controller;

import com.pdvgenerico.dto.ConsignacaoRequest;
import com.pdvgenerico.dto.ConsignacaoResponse;
import com.pdvgenerico.dto.FecharConsignacaoRequest;
import com.pdvgenerico.dto.LeituraPaginaResponse;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.ConsignacaoService;
import com.pdvgenerico.service.LeituraConferenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/consignacoes")
@RequiredArgsConstructor
public class ConsignacaoController {

    private final ConsignacaoService consignacaoService;
    private final LeituraConferenciaService leituraConferenciaService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public ResponseEntity<ConsignacaoResponse> criar(@Valid @RequestBody ConsignacaoRequest request,
                                                      @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(ConsignacaoResponse.fromEntity(consignacaoService.criar(request, usuarioLogado)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public List<ConsignacaoResponse> listar() {
        return consignacaoService.listarTodas().stream().map(ConsignacaoResponse::fromEntity).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public ConsignacaoResponse buscarPorId(@PathVariable Long id) {
        return ConsignacaoResponse.fromEntity(consignacaoService.buscarPorId(id));
    }

    // Edição de consignação em aberto (acrescentar/remover produtos, mudar quantidades).
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public ConsignacaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ConsignacaoRequest request) {
        return ConsignacaoResponse.fromEntity(consignacaoService.atualizar(id, request));
    }

    @PostMapping("/{id}/fechar")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public ConsignacaoResponse fechar(@PathVariable Long id, @Valid @RequestBody FecharConsignacaoRequest request) {
        return ConsignacaoResponse.fromEntity(consignacaoService.fechar(id, request));
    }

    /** Lê a foto de UMA página do relatório com as vendas anotadas à mão. Só sugere: não grava nada. */
    @PostMapping(value = "/{id}/ler-pagina", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CAIXA')")
    public LeituraPaginaResponse lerPagina(@PathVariable Long id, @RequestParam("imagem") MultipartFile imagem) {
        return leituraConferenciaService.lerPagina(id, imagem);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        consignacaoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
