package com.pdvgenerico.config;

import com.pdvgenerico.model.Perfil;
import com.pdvgenerico.model.Usuario;
import com.pdvgenerico.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria os usuários iniciais (admin e caixa) apenas quando o banco está vazio —
 * útil pra subir o sistema pela primeira vez sem precisar inserir dados na mão.
 *
 * As senhas podem (e devem, em produção) ser definidas por variável de ambiente
 * (ADMIN_SENHA_INICIAL / CAIXA_SENHA_INICIAL). Se não forem definidas, caímos em
 * valores padrão conhecidos publicamente (estão neste código-fonte), então
 * avisamos bem alto no log pra não passar despercebido — o ideal é trocar essas
 * senhas pelo sistema logo após o primeiro login.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SENHA_ADMIN_PADRAO = "admin123";
    private static final String SENHA_CAIXA_PADRAO = "caixa123";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-senha:" + SENHA_ADMIN_PADRAO + "}")
    private String senhaAdminInicial;

    @Value("${app.seed.caixa-senha:" + SENHA_CAIXA_PADRAO + "}")
    private String senhaCaixaInicial;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() == 0) {
            Usuario admin = Usuario.builder()
                    .login("admin")
                    .senha(passwordEncoder.encode(senhaAdminInicial))
                    .nome("Administrador")
                    .perfil(Perfil.ADMIN)
                    .ativo(true)
                    .build();

            Usuario caixa = Usuario.builder()
                    .login("caixa")
                    .senha(passwordEncoder.encode(senhaCaixaInicial))
                    .nome("Operador de Caixa")
                    .perfil(Perfil.CAIXA)
                    .ativo(true)
                    .build();

            usuarioRepository.save(admin);
            usuarioRepository.save(caixa);

            log.info("Usuários iniciais criados: admin (ADMIN) e caixa (CAIXA).");

            boolean usandoSenhasPadrao =
                    senhaAdminInicial.equals(SENHA_ADMIN_PADRAO) || senhaCaixaInicial.equals(SENHA_CAIXA_PADRAO);
            if (usandoSenhasPadrao) {
                log.warn("############################################################################");
                log.warn("# ATENÇÃO: usuários criados com senha padrão (admin123 / caixa123).        #");
                log.warn("# Essas senhas são públicas (estão no código-fonte) — troque-as agora pelo #");
                log.warn("# sistema, ou defina ADMIN_SENHA_INICIAL / CAIXA_SENHA_INICIAL antes do     #");
                log.warn("# primeiro deploy em produção.                                              #");
                log.warn("############################################################################");
            }
        }
    }
}
