package com.pdvgenerico.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Garante que a tabela de mensagens aceita emoji. Emoji ocupa 4 bytes em UTF-8 e
 * só cabe em colunas utf8mb4; tabelas criadas em utf8/latin1 recusam a gravação
 * (erro 500 ao enviar). Roda ao iniciar e só altera se a tabela ainda não estiver
 * em utf8mb4 — nas demais inicializações não faz nada.
 */
@Component
public class CorrecaoCharsetMensagens implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CorrecaoCharsetMensagens.class);

    private final JdbcTemplate jdbcTemplate;

    public CorrecaoCharsetMensagens(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> collations = jdbcTemplate.queryForList(
                    "select table_collation from information_schema.tables "
                            + "where table_schema = database() and table_name = 'mensagens_privadas'",
                    String.class);
            if (collations.isEmpty() || collations.get(0) == null
                    || collations.get(0).toLowerCase().startsWith("utf8mb4")) {
                return;
            }
            log.info("Convertendo mensagens_privadas de {} para utf8mb4 (suporte a emoji)", collations.get(0));
            jdbcTemplate.execute(
                    "alter table mensagens_privadas convert to character set utf8mb4 collate utf8mb4_unicode_ci");
            log.info("mensagens_privadas convertida para utf8mb4");
        } catch (DataAccessException e) {
            // não derruba a aplicação: no pior caso o emoji continua sem funcionar
            log.warn("Não foi possível converter mensagens_privadas para utf8mb4: {}", e.getMessage());
        }
    }
}
