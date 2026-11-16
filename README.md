<div align="center">

# 🎁 PDV Presente de Deus — Backend

**API REST do sistema de gestão e ponto de venda (PDV) da livraria católica e loja de presentes _Presente de Deus_.**

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)
![Render](https://img.shields.io/badge/Deploy-Render-46E3B7?logo=render&logoColor=black)

[🖥️ Frontend](https://github.com/pedroleonidasdev/pdvlojapresentededeus-frontend) · [🌐 Sistema em produção](https://pdvlojapresentededeus-frontend.vercel.app)

</div>

---

## 📖 Sobre o projeto

Este repositório contém o **backend** do sistema usado no dia a dia da loja *Presente de Deus – Livraria Católica e Presentes*. Ele expõe a API consumida pelo [frontend](https://github.com/pedroleonidasdev/pdvlojapresentededeus-frontend) e cuida de autenticação, vendas, caixa, estoque, consignações, trocas, finanças e impressão de etiquetas.

---

## ✨ Funcionalidades

- 🔐 **Autenticação** por token (`Bearer`) com perfis `ADMIN` e `CAIXA`
- 🛒 **Vendas (PDV):** registro de vendas, formas de pagamento e desconto percentual
- 💰 **Caixa:** abertura, fechamento e reabertura
- 📦 **Estoque:** produtos, preços, quantidades, código de barras e categorias
- 🤝 **Consignações:** controle de mercadorias em consignação e comissão por categoria
- 🔁 **Trocas e devoluções**
- 📊 **Relatórios** de vendas e faturamento
- 💸 **Finanças:** despesas e fluxo financeiro
- 👥 **Usuários:** cadastro, perfis e encerramento de sessões
- 🔔 **Notificações push** para administradores
- 🏷️ **Etiquetas de preço** com suporte a impressora térmica Elgin L42 Pro (ZPL)

---

## 🧰 Tecnologias

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 21 |
| Framework | Spring Boot 3 |
| Persistência | Spring Data JPA + Hibernate |
| Banco de dados | MySQL |
| Build | Maven (wrapper `mvnw` incluso) |
| Container | Docker |
| Hospedagem | Render (API) e Clever Cloud (MySQL) |

---

## 🏗️ Arquitetura

```text
 Navegador ──► Frontend (Next.js, Vercel) ──► API Spring Boot (Render) ──► MySQL (Clever Cloud)
                                                     │
                                                     └──► Agente de impressão na loja (CUPS / ZPL)
```

---

## 🗂️ Estrutura do projeto

```text
├── src/main/java/       # Código da aplicação
├── src/main/resources/  # application.properties e recursos
├── scripts/             # Scripts auxiliares (ex.: impressão de etiquetas ZPL)
├── Dockerfile           # Imagem usada no deploy
├── pom.xml
└── mvnw / mvnw.cmd      # Maven Wrapper
```

---

## 🚀 Como rodar localmente

### Pré-requisitos

- [Java 21 (JDK)](https://adoptium.net/)
- MySQL 8 rodando localmente
- Maven (ou use o `mvnw`, que já está no repositório)

### Passo a passo

```bash
# 1. Clone o repositório
git clone https://github.com/pedroleonidasdev/loja.git
cd loja

# 2. Configure o banco (veja a seção abaixo)

# 3. Suba a API
./mvnw spring-boot:run        # Linux / macOS
.\mvnw.cmd spring-boot:run    # Windows (PowerShell)
```

A API sobe em `http://localhost:8080`.

### Configuração do banco

Por padrão, a aplicação usa o MySQL local. Ajuste em `src/main/resources/application.properties` se necessário:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pdv_generico?useSSL=false&serverTimezone=America/Sao_Paulo&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=root
```

O banco `pdv_generico` é criado na primeira conexão (`createDatabaseIfNotExist=true`) e as tabelas são geradas pelo Hibernate (`ddl-auto=update`).

---

## ⚙️ Variáveis de ambiente

Em produção (Render), as credenciais do banco **não ficam no código**: são definidas como variáveis de ambiente.

| Variável | Descrição |
| --- | --- |
| `DB_USERNAME` | Usuário do MySQL |
| `DB_PASSWORD` | Senha do MySQL |
| `ETIQUETA_DRIVER` | Driver de impressão de etiquetas (ex.: `zpl`) |
| `ELGIN_CUPS_QUEUE` | Fila CUPS da impressora Elgin no PC da loja (ex.: `L42PRO-FULL`) |

> Confira em `application.properties` os nomes exatos e demais variáveis usadas (URL do banco, segredo do token etc.).

> 💡 **Dica:** no usuário do banco, cuidado com caracteres parecidos, como a letra `l` e o número `1`. Esse é um erro comum que causa `Access denied` no deploy.

---

## 🔑 Usuários iniciais

Na primeira execução, o sistema cria automaticamente dois usuários:

| Login | Perfil | Acesso |
| --- | --- | --- |
| `admin` | `ADMIN` | Acesso total |
| `caixa` | `CAIXA` | Venda e consulta de estoque |

As senhas iniciais são definidas no código de inicialização do sistema.

> ⚠️ **Troque as senhas imediatamente** na tela **Usuários** do sistema, principalmente em produção. Não deixe senhas padrão em um sistema exposto à internet.

---

## 🔌 Principais endpoints

Autenticação via header `Authorization: Bearer <token>`, obtido no login.

| Método | Rota | Quem acessa |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Público |
| `GET` `POST` `PUT` `DELETE` | `/api/produtos` | ADMIN (escrita) / ADMIN + CAIXA (leitura) |
| `GET` `POST` `PUT` `DELETE` | `/api/categorias` | ADMIN (escrita) / ADMIN + CAIXA (leitura) |
| `POST` `GET` | `/api/vendas` | ADMIN + CAIXA |
| `GET` | `/api/relatorios/vendas?inicio=...&fim=...` | ADMIN |
| `GET` `POST` `PUT` `DELETE` | `/api/usuarios` | ADMIN |

Exemplo de login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login": "admin", "senha": "SUA_SENHA"}'
```

---

## 🐳 Docker

```bash
docker build -t pdv-loja-backend .
docker run -p 8080:8080 \
  -e DB_USERNAME=usuario \
  -e DB_PASSWORD=senha \
  pdv-loja-backend
```

---

## ☁️ Deploy

- **API:** Render, via `Dockerfile`, conectado à branch `main`
- **Banco:** MySQL no Clever Cloud
- **Frontend:** Vercel (veja o [repositório do frontend](https://github.com/pedroleonidasdev/pdvlojapresentededeus-frontend))

Configure as variáveis de ambiente no painel do Render antes do primeiro deploy.

---

## 🏷️ Impressão de etiquetas (Elgin L42 Pro)

O suporte à impressora usa **ZPL** e um agente que roda no PC da loja (Linux Mint, com CUPS):

- Script: `scripts/elgin_l42.py`
- Driver: `ETIQUETA_DRIVER=zpl`
- Fila CUPS do tipo *raw* (ex.: `L42PRO-FULL`)

---

## 🗺️ Roadmap

- [ ] Testes automatizados
- [ ] Documentação da API (OpenAPI/Swagger)
- [ ] Migrações de banco com Flyway (em vez de `ddl-auto=update`)

---

## 👨‍💻 Autor

**Pedro Leonidas de Oliveira Campos**

[![GitHub](https://img.shields.io/badge/GitHub-pedroleonidasdev-181717?logo=github)](https://github.com/pedroleonidasdev)
[![Site](https://img.shields.io/badge/Site-pedroleonidasdev.com.br-0a7?logo=googlechrome&logoColor=white)](https://pedroleonidasdev.com.br)

---

<div align="center">

Feito com ☕ e fé para a **Presente de Deus – Livraria Católica e Presentes**

</div>
