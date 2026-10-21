package com.pdvgenerico.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdvgenerico.dto.LeituraPaginaResponse;
import com.pdvgenerico.exception.BusinessException;
import com.pdvgenerico.model.Consignacao;
import com.pdvgenerico.model.ItemConsignacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lê a foto de UMA página do relatório de consignação (com as vendas anotadas à mão) e devolve,
 * para cada produto reconhecido pelo código de barras, a quantidade vendida lida.
 * Só sugere valores: quem confere e fecha a consignação continua sendo a pessoa, na tela.
 */
@Service
public class LeituraConferenciaService {

    private static final Logger log = LoggerFactory.getLogger(LeituraConferenciaService.class);

    private static final long TAMANHO_MAXIMO_BYTES = 4L * 1024 * 1024;
    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png", "image/webp");

    private static final String PROMPT_SISTEMA =
            "Você lê fotos de relatórios impressos de mercadorias em consignação que têm anotações feitas à mão. "
                    + "Responda SOMENTE com JSON válido, sem nenhum texto antes ou depois e sem cercas de código.";

    private static final String PROMPT_USUARIO =
            "A imagem é uma página do 'Relatório de mercadorias em consignação'. A tabela tem as colunas: "
                    + "COD. (código de barras impresso), PRODUTO, QTDE ENVIADA, VLR UNITÁRIO, VALOR TOTAL, "
                    + "QTDE VENDIDA (número escrito à mão) e VLR TOTAL VENDIDO (valor escrito à mão).\n"
                    + "Para CADA linha de produto da tabela devolva: "
                    + "cod (somente os dígitos do código impresso), "
                    + "produto (texto impresso), "
                    + "qtdVendida (inteiro escrito à mão na coluna QTDE VENDIDA; null se a célula estiver vazia), "
                    + "valorVendido (número em reais escrito à mão na coluna VLR TOTAL VENDIDO, com ponto decimal; null se vazio), "
                    + "incerto (true se a letra estiver ilegível ou se você tiver dúvida real em algum dos números).\n"
                    + "Você lê SOMENTE QTDE VENDIDA e VLR TOTAL VENDIDO. Ignore números riscados ou corrigidos nas colunas impressas "
                    + "(QTDE ENVIADA, VLR UNITÁRIO, VALOR TOTAL). Ignore as linhas de título de categoria (ex.: 'MEDALHAS:') e a linha TOTAL.\n"
                    + "Se houver um total manuscrito da página (número escrito à mão logo abaixo da tabela, normalmente a soma da coluna "
                    + "VLR TOTAL VENDIDO), devolva em totalManuscritoPagina; caso contrário null. Não confunda com anotações de fechamento "
                    + "do relatório (total vendido, porcentagens, total repassado): se existirem, resuma em observacoes.\n"
                    + "Formato exato: {\"linhas\":[{\"cod\":\"2000000000000\",\"produto\":\"...\",\"qtdVendida\":0,"
                    + "\"valorVendido\":null,\"incerto\":false}],\"totalManuscritoPagina\":null,\"observacoes\":\"\"}\n"
                    + "Não invente: use null quando não conseguir ler.";

    private record ItemRef(Long id, String nome, String cod, int enviada, BigDecimal preco) {
    }

    private final ConsignacaoService consignacaoService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final RestClient restClient;
    private final String apiKey;
    private final String modelo;

    public LeituraConferenciaService(ConsignacaoService consignacaoService,
                                     ObjectMapper objectMapper,
                                     PlatformTransactionManager transactionManager,
                                     @Value("${app.anthropic.api-key:}") String apiKey,
                                     @Value("${app.anthropic.model:claude-sonnet-5-5}") String modelo) {
        this.consignacaoService = consignacaoService;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setReadOnly(true);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = modelo;

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .requestFactory(fabrica)
                .build();
    }

    public LeituraPaginaResponse lerPagina(Long consignacaoId, MultipartFile imagem) {
        if (apiKey.isEmpty()) {
            throw new BusinessException("A leitura por foto ainda não está ativada no servidor (falta a chave ANTHROPIC_API_KEY).");
        }
        if (imagem == null || imagem.isEmpty()) {
            throw new BusinessException("Envie a foto da página do relatório.");
        }
        String tipo = imagem.getContentType() == null ? "" : imagem.getContentType().toLowerCase();
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new BusinessException("Formato de imagem não aceito. Use foto JPG, PNG ou WEBP.");
        }
        if (imagem.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new BusinessException("A foto é grande demais (máximo 4 MB). Tire outra ou reduza o tamanho.");
        }

        // carrega os dados dos itens numa transação curta, ANTES de chamar o serviço externo
        // (o banco tem poucas conexões e a leitura pode demorar alguns segundos)
        List<ItemRef> itens = tx.execute(status -> {
            Consignacao c = consignacaoService.buscarPorId(consignacaoId);
            List<ItemRef> lista = new ArrayList<>();
            for (ItemConsignacao i : c.getItens()) {
                lista.add(new ItemRef(
                        i.getId(),
                        i.getProduto().getNome(),
                        somenteDigitos(i.getProduto().getCodigoBarras()),
                        i.getQuantidadeEnviada(),
                        i.getPrecoUnitario()));
            }
            return lista;
        });
        Map<String, ItemRef> porCodigo = new HashMap<>();
        if (itens != null) {
            for (ItemRef i : itens) {
                if (!i.cod().isEmpty()) {
                    porCodigo.put(i.cod(), i);
                }
            }
        }

        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(imagem.getBytes());
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o arquivo enviado.");
        }

        JsonNode resultado = chamarModelo(tipo, base64);
        return montarResposta(resultado, porCodigo);
    }

    private JsonNode chamarModelo(String tipo, String base64) {
        Map<String, Object> imagemBloco = Map.of(
                "type", "image",
                "source", Map.of("type", "base64", "media_type", tipo, "data", base64));
        Map<String, Object> textoBloco = Map.of("type", "text", "text", PROMPT_USUARIO);
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "max_tokens", 8000,
                "system", PROMPT_SISTEMA,
                "messages", List.of(Map.of("role", "user", "content", List.of(imagemBloco, textoBloco))));

        String resposta;
        try {
            resposta = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .body(corpo)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("Leitura de foto recusada pelo serviço: status {} - {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            int codigo = e.getStatusCode().value();
            if (codigo == 401 || codigo == 403) {
                throw new BusinessException("A chave do serviço de leitura foi recusada. Confira ANTHROPIC_API_KEY no servidor.");
            }
            if (codigo == 429) {
                throw new BusinessException("O serviço de leitura está ocupado. Espere um minuto e tente de novo.");
            }
            if (codigo == 400 && e.getResponseBodyAsString().toLowerCase().contains("credit")) {
                throw new BusinessException("Acabou o crédito da conta do serviço de leitura. Adicione crédito e tente de novo.");
            }
            throw new BusinessException("O serviço de leitura não conseguiu ler esta foto (código " + codigo + ").");
        } catch (RestClientException e) {
            log.warn("Falha ao falar com o serviço de leitura: {}", e.getMessage());
            throw new BusinessException("Não foi possível falar com o serviço de leitura agora. Tente de novo.");
        }

        try {
            JsonNode raiz = objectMapper.readTree(resposta);
            JsonNode conteudo = raiz.path("content");
            String texto = conteudo.isArray() && conteudo.size() > 0 ? conteudo.get(0).path("text").asText("") : "";
            int ini = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (ini < 0 || fim <= ini) {
                throw new BusinessException("Não consegui entender a resposta da leitura desta foto. Tente de novo ou tire outra foto.");
            }
            return objectMapper.readTree(texto.substring(ini, fim + 1));
        } catch (IOException e) {
            throw new BusinessException("Não consegui entender a resposta da leitura desta foto. Tente de novo ou tire outra foto.");
        }
    }

    private LeituraPaginaResponse montarResposta(JsonNode json, Map<String, ItemRef> porCodigo) {
        List<LeituraPaginaResponse.Linha> linhas = new ArrayList<>();
        List<String> naoEncontradas = new ArrayList<>();
        BigDecimal soma = BigDecimal.ZERO;
        boolean algumValor = false;

        for (JsonNode l : json.path("linhas")) {
            String cod = somenteDigitos(l.path("cod").asText(""));
            if (cod.isEmpty()) {
                continue;
            }
            JsonNode qtdNode = l.path("qtdVendida");
            if (!qtdNode.isNumber()) {
                continue; // célula em branco: não sugere nada para este item
            }
            int qtd = qtdNode.asInt();
            BigDecimal valor = l.path("valorVendido").isNumber()
                    ? l.path("valorVendido").decimalValue().setScale(2, RoundingMode.HALF_UP)
                    : null;
            boolean incerto = l.path("incerto").asBoolean(false);
            if (valor != null) {
                soma = soma.add(valor);
                algumValor = true;
            }

            ItemRef item = porCodigo.get(cod);
            if (item == null) {
                String nome = l.path("produto").asText("");
                naoEncontradas.add(nome.isEmpty() ? cod : cod + " - " + nome);
                continue;
            }

            BigDecimal esperado = item.preco().multiply(BigDecimal.valueOf(qtd)).setScale(2, RoundingMode.HALF_UP);
            List<String> avisos = new ArrayList<>();
            if (qtd < 0) {
                avisos.add("Quantidade negativa lida.");
            }
            if (qtd > item.enviada()) {
                avisos.add("Vendida (" + qtd + ") maior que a enviada (" + item.enviada() + ").");
            }
            if (valor != null && valor.subtract(esperado).abs().compareTo(new BigDecimal("0.01")) > 0) {
                avisos.add("Valor escrito à mão (" + moeda(valor) + ") difere de quantidade x preço do sistema (" + moeda(esperado) + ").");
            }
            if (incerto) {
                avisos.add("Leitura incerta: confira o número na foto.");
            }

            linhas.add(new LeituraPaginaResponse.Linha(
                    item.id(), item.nome(), item.cod(), item.enviada(), qtd, valor, esperado, incerto, avisos));
        }

        BigDecimal totalManuscrito = json.path("totalManuscritoPagina").isNumber()
                ? json.path("totalManuscritoPagina").decimalValue().setScale(2, RoundingMode.HALF_UP)
                : null;
        String obs = json.path("observacoes").asText("");
        return new LeituraPaginaResponse(
                linhas,
                naoEncontradas,
                totalManuscrito,
                algumValor ? soma.setScale(2, RoundingMode.HALF_UP) : null,
                obs.isBlank() ? null : obs);
    }

    private static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    private static String moeda(BigDecimal v) {
        return "R$ " + v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
