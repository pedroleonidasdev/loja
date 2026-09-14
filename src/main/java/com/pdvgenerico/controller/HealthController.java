package com.pdvgenerico.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint público e leve, sem autenticação e sem tocar no banco, usado por um
// serviço externo de ping (cron-job.org, UptimeRobot etc.) pra manter a
// aplicação acordada no plano free do Render, que hiberna após ~15min sem tráfego.
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
