package com.pdvgenerico.service;

import com.pdvgenerico.dto.PushSubscriptionRequest;
import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.PushSubscription;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Envia notificações push (Web Push / VAPID) para o navegador/celular dos
 * usuários ADMIN sempre que uma venda é registrada ou o caixa é
 * aberto/fechado. Não lança exceção se o envio falhar — notificação é um
 * "extra", nunca deve derrubar a operação principal (a venda já foi salva
 * de qualquer forma).
 */
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushService pushService;
    private final boolean configurado;

    public NotificacaoService(
            PushSubscriptionRepository pushSubscriptionRepository,
            @Value("${app.vapid.public-key:}") String vapidPublicKey,
            @Value("${app.vapid.private-key:}") String vapidPrivateKey,
            @Value("${app.vapid.subject:mailto:contato@lojapresentededeus.com.br}") String vapidSubject
    ) throws GeneralSecurityException {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.configurado = !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();

        Security.addProvider(new BouncyCastleProvider());

        if (configurado) {
            PushService service = new PushService();
            service.setPublicKey(vapidPublicKey);
            service.setPrivateKey(vapidPrivateKey);
            service.setSubject(vapidSubject);
            this.pushService = service;
        } else {
            this.pushService = null;
        }
    }

    @PostConstruct
    void avisarSeNaoConfigurado() {
        if (!configurado) {
            log.warn("Notificações push desativadas: defina VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY " +
                    "nas variáveis de ambiente para habilitar (veja instruções no README).");
        }
    }

    @Transactional
    public void inscrever(PushSubscriptionRequest request, Usuario usuarioLogado) {
        pushSubscriptionRepository.findByEndpoint(request.endpoint()).ifPresentOrElse(
                existente -> {
                    // mesmo endpoint podendo re-inscrever (ex: trocou de usuário no mesmo
                    // navegador) — atualiza o dono em vez de duplicar
                    existente.setUsuario(usuarioLogado);
                    existente.setP256dh(request.keys().p256dh());
                    existente.setAuth(request.keys().auth());
                    pushSubscriptionRepository.save(existente);
                },
                () -> pushSubscriptionRepository.save(PushSubscription.builder()
                        .usuario(usuarioLogado)
                        .endpoint(request.endpoint())
                        .p256dh(request.keys().p256dh())
                        .auth(request.keys().auth())
                        .criadoEm(LocalDateTime.now())
                        .build())
        );
    }

    @Transactional
    public void desinscrever(String endpoint) {
        pushSubscriptionRepository.findByEndpoint(endpoint).ifPresent(pushSubscriptionRepository::delete);
    }

    /**
     * Resultado de uma rodada de envios (usado nos logs e no botão de teste).
     * Cada item de "detalhes" descreve uma inscrição: de quem é, qual serviço
     * de push do navegador a atende (ex.: web.push.apple.com) e o resultado.
     */
    public record ResultadoEnvio(boolean configurado, int totalInscricoes, int enviadas, int removidas, int falhas, List<String> detalhes) {}

    /**
     * Envia uma notificação para todos os ADMINs ativos, em todos os
     * dispositivos que cada um tiver inscrito. Se usuarioIgnorarId for
     * informado, as inscrições desse usuário ficam de fora (hoje as chamadas
     * passam null: todo ADMIN recebe, inclusive quem fez a ação).
     */
    @Transactional
    public void notificarAdmins(String titulo, String corpo, String url, Long usuarioIgnorarId) {
        dispararParaAdmins(titulo, corpo, url, usuarioIgnorarId);
    }

    /** Dispara uma notificação de teste para todos os ADMINs e devolve o que aconteceu com cada inscrição. */
    @Transactional
    public ResultadoEnvio enviarTeste() {
        return dispararParaAdmins("Teste de notificação", "Se você está vendo isto, as notificações estão funcionando.", "/inicio", null);
    }

    private ResultadoEnvio dispararParaAdmins(String titulo, String corpo, String url, Long usuarioIgnorarId) {
        if (!configurado) {
            return new ResultadoEnvio(false, 0, 0, 0, 0,
                    List.of("Chaves VAPID não configuradas no servidor (VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY)."));
        }

        List<PushSubscription> inscricoes = pushSubscriptionRepository
                .findByUsuario_PerfilAndUsuario_AtivoTrue(Perfil.ADMIN);

        List<PushSubscription> destinatarios = new ArrayList<>();
        for (PushSubscription inscricao : inscricoes) {
            if (usuarioIgnorarId != null && inscricao.getUsuario().getId().equals(usuarioIgnorarId)) {
                continue;
            }
            destinatarios.add(inscricao);
        }

        List<String> detalhes = new ArrayList<>();
        if (destinatarios.isEmpty()) {
            // é este o aviso que explica "parei de receber": nenhum aparelho de ADMIN inscrito
            log.warn("Push '{}': nenhuma inscrição de ADMIN para notificar (inscrições de admin no banco: {}).",
                    titulo, inscricoes.size());
            detalhes.add("Nenhum ADMIN tem aparelho inscrito. Abra o app como ADMIN e toque em \"Ativar notificações\".");
            return new ResultadoEnvio(true, 0, 0, 0, 0, detalhes);
        }

        String payload = montarPayloadJson(titulo, corpo, url);
        int enviadas = 0;
        int removidas = 0;
        int falhas = 0;

        for (PushSubscription inscricao : destinatarios) {
            String quem = inscricao.getUsuario().getLogin() + " (" + hostDe(inscricao.getEndpoint()) + ")";
            int status = enviar(inscricao, payload);

            if (status >= 200 && status < 300) {
                enviadas++;
                detalhes.add(quem + ": enviada (HTTP " + status + ")");
            } else if (status == 404 || status == 410) {
                removidas++;
                detalhes.add(quem + ": inscrição expirada, removida (HTTP " + status + ")");
                log.warn("Push '{}': inscrição de {} expirou (HTTP {}) e foi removida.", titulo, quem, status);
            } else {
                falhas++;
                String motivo = status < 0 ? "erro de conexão/envio" : "HTTP " + status;
                detalhes.add(quem + ": falhou (" + motivo + ")");
                log.warn("Push '{}': falha ao enviar para {} ({}).", titulo, quem, motivo);
            }
        }

        log.info("Push '{}': {} enviada(s), {} removida(s), {} falha(s) de {} inscrição(ões).",
                titulo, enviadas, removidas, falhas, destinatarios.size());
        return new ResultadoEnvio(true, destinatarios.size(), enviadas, removidas, falhas, detalhes);
    }

    /** Devolve o status HTTP do serviço de push, ou -1 se nem chegou a obter resposta. */
    private int enviar(PushSubscription inscricao, String payload) {
        try {
            Subscription subscription = new Subscription(
                    inscricao.getEndpoint(),
                    new Subscription.Keys(inscricao.getP256dh(), inscricao.getAuth())
            );
            Notification notification = new Notification(subscription, payload);
            var resposta = pushService.send(notification);

            int status = resposta.getStatusLine().getStatusCode();
            // 404/410: o navegador cancelou essa inscrição (app desinstalado, permissão
            // revogada etc.) — não faz sentido continuar tentando enviar pra ela
            if (status == 404 || status == 410) {
                pushSubscriptionRepository.delete(inscricao);
            }
            return status;
        } catch (Exception e) {
            // notificação é um "extra" — uma falha de envio (rede, provedor fora do ar
            // etc.) nunca deve derrubar a venda/fechamento de caixa que já foi salvo
            log.warn("Falha ao enviar notificação push (endpoint {}): {}", inscricao.getEndpoint(), e.getMessage());
            return -1;
        }
    }

    private String hostDe(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            return host != null ? host : "desconhecido";
        } catch (Exception e) {
            return "desconhecido";
        }
    }

    private String montarPayloadJson(String titulo, String corpo, String url) {
        // JSON simples montado na mão pra não puxar mais uma dependência só pra isso;
        // os três campos são texto controlado pelo próprio backend (não vem do usuário
        // final), mas escapamos aspas/quebras de linha por segurança mesmo assim.
        return "{\"titulo\":\"" + escapar(titulo) + "\",\"corpo\":\"" + escapar(corpo) + "\",\"url\":\"" + escapar(url) + "\"}";
    }

    private String escapar(String texto) {
        return texto == null ? "" : texto
                .replaceAll("\\p{Cntrl}", " ") // quebra de linha, tab etc. quebrariam o JSON
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
