package com.pdvgenerico.controller;

import com.pdvgenerico.dto.DespesaRequest;
import com.pdvgenerico.dto.DespesaResponse;
import com.pdvgenerico.dto.EditarDespesaRequest;
import com.pdvgenerico.model.Despesa;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.service.DespesaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

// Controle de Finanças (despesas/custos + sangria/suprimento) é restrito a
// ADMIN de ponta a ponta — é uma tela do dono do negócio, não do operador de
// caixa, na mesma linha de Relatórios e Usuários.
@RestController
@RequestMapping("/api/despesas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DespesaController {

    private final DespesaService despesaService;

    @PostMapping
    public ResponseEntity<DespesaResponse> registrar(@Valid @RequestBody DespesaRequest request,
                                                       @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(DespesaResponse.fromEntity(despesaService.registrar(request, usuarioLogado)));
    }

    @GetMapping
    public List<DespesaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        List<Despesa> despesas = (inicio != null && fim != null)
                ? despesaService.listarPorPeriodo(inicio, fim)
                : despesaService.listarTodas();
        return despesas.stream().map(DespesaResponse::fromEntity).toList();
    }

    @PutMapping("/{id}")
    public ResponseEntity<DespesaResponse> editar(@PathVariable Long id,
                                                   @Valid @RequestBody EditarDespesaRequest request) {
        return ResponseEntity.ok(DespesaResponse.fromEntity(despesaService.editar(id, request)));
    }

    // Marca/desmarca como pago um lançamento à vista (sem parcelas detalhadas).
    @PatchMapping("/{id}/pago")
    public ResponseEntity<DespesaResponse> marcarPago(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "true") boolean pago) {
        return ResponseEntity.ok(DespesaResponse.fromEntity(despesaService.marcarPago(id, pago)));
    }

    // Marca/desmarca como paga uma parcela específica de um lançamento parcelado.
    @PatchMapping("/{id}/parcelas/{numero}/pago")
    public ResponseEntity<DespesaResponse> marcarParcelaPaga(@PathVariable Long id,
                                                               @PathVariable Integer numero,
                                                               @RequestParam(defaultValue = "true") boolean pago) {
        return ResponseEntity.ok(DespesaResponse.fromEntity(despesaService.marcarParcelaPaga(id, numero, pago)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        despesaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
