# GEMS SDK Java `3.1.0`

`3.0.0` → `3.1.0`. **MINOR**: um módulo muda — `gems-exception` — e a mudança é aditiva, com uma única
remoção de método interno de handler que corrige um status. Nenhuma API pública de exceção ou de DTO
mudou de assinatura, saiu ou recebeu marca de descontinuação. Os quinze demais módulos recebem só os
bumps de dependência abaixo.

Entregue pela rodada `010-habilitacao-de-agentes` do Meduc (bloco G6-SDK), com a autorização de
merge pelo agente registrada no roteiro daquela rodada.

---

## `gems-exception` — o que responde agora

| Exceção | 3.0.0 | **3.1.0** | `errorType` | `codigo` | Mensagem |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `AccessDeniedException` | 403 | 403 | `ACESSO_NEGADO` | — | fixa |
| `AuthorizationDeniedException` (`@PreAuthorize`) | 403 por ordem de advice — mas o `SecurityExceptionHandler` declarava **401** | **403**, e nenhum handler da SDK declara outra coisa | `ACESSO_NEGADO` | — | fixa |
| `BadCredentialsException` | 401 | 401 | `FALHA` | — | "Falha ao autenticar" |
| `NoResourceFoundException` | **500** | **404** | `FALHA` | `RECURSO_NAO_ENCONTRADO` | fixa — **não** repete o caminho |
| `MethodArgumentTypeMismatchException` | **500** | **400** | `VALIDACAO` | `PARAMETRO_INVALIDO` | nomeia o **parâmetro**, nunca o valor |
| `ConflictException` (nova) | — | **409** | o da exceção (`FALHA` por padrão) | o da exceção | a da exceção; `detalhes` acompanham |
| `BusinessException` | 400 | 400 | o da exceção | o da exceção | a da exceção |
| `MethodArgumentNotValidException` | 400 | 400 | `VALIDACAO` | — | violações acumuladas |
| `ExternalServiceException` | 502 | 502 | `SERVICO_INDISPONIVEL` | — | a da exceção |
| `IllegalArgumentException` | 400 | 400 | `FALHA` | — | a da exceção |
| `Exception` | 500 | 500 | `ERRO_NAO_ESPERADO` | — | código de rastreio |

### Corrigido: negação de autorização por `@PreAuthorize` declarava 401; responde 403

`SecurityExceptionHandler.handleClaimsException(AuthorizationDeniedException)` **saiu** — o método,
não a classe. `AuthorizationDeniedException` estende `AccessDeniedException`, e o
`AuthorizationExceptionHandler` já a cobre por herança com **403**. 401 diz "não sei quem você é";
403 diz "sei quem você é e isso não é seu" — devolver 401 a quem já está autenticado manda o
frontend a um login que não resolve nada.

**O que a prova mostrou ao ser escrita:** o `@Order(LOWEST_PRECEDENCE - 100)` do
`AuthorizationExceptionHandler` o punha antes na fila do resolver, e o Spring fica com o primeiro
advice que tem handler compatível — então, na 3.0.0, um consumidor com os três advices da SDK e
nenhum próprio **já recebia 403** por ordem. O 401 era o que a classe declarava, não o que o
runtime devolvia; a correção tira a declaração e a dependência da ordem. A rede da 3.1.0 fixa as
duas coisas: o status (`AuthorizationDeniedRespondeForbiddenTest`, com o `SecurityExceptionHandler`
na pilha) e a ausência do método (prova estrutural no mesmo teste — religar o método a derruba
nomeando `handleClaimsException`).

### Novo: 404 e 400 de requisição saem do catch-all

`NoResourceFoundException` (endereço não mapeado) e `MethodArgumentTypeMismatchException`
(parâmetro de caminho ou de consulta que não converte) caíam no `handleException(Exception)` — 500
com código de rastreio, para o que é erro do cliente. Agora são **404** e **400**, no envelope.

**Política de mensagem**, escrita no Javadoc do `GlobalExceptionHandler`: a resposta **não ecoa** o
caminho pedido nem o valor recebido — os dois são texto que o cliente enviou, e devolvê-los é
reflexão. O 404 tem mensagem fixa ("Recurso não encontrado."; o caminho vai no `path` do envelope e
no log); o 400 nomeia o parâmetro (`ex.getName()`) e nunca o valor. `RequestErrorsTest` prova os dois
lados.

### Novo: `ConflictException` → 409

```java
package br.com.gems.exception.exception;

public class ConflictException extends BusinessException {
    public ConflictException( String message, String codigo )                       // FALHA
    public ConflictException( String message, String codigo, List<String> detalhes ) // FALHA
    public ConflictException( ErrorTypeEnum errorType, String message, String codigo, List<String> detalhes )
}
```

400 diz "a requisição está errada, corrija e reenvie"; 409 diz "a requisição está certa, mas o
estado do recurso não a admite agora" — o último gestor habilitado de uma organização, uma chave que
outro registro já ocupa. Reenviar o mesmo corpo não resolve. Estende `BusinessException` para que o
envelope seja o mesmo: `codigo` e `detalhes` acompanham `message` (EX-1); só o status muda.
Consumidor que hoje mantém um `@RestControllerAdvice` local só para dar 409 a uma exceção própria
troca `extends BusinessException` por `extends ConflictException` e apaga o advice.

### `path` é `request.getRequestURI()` em todos os handlers

Era `getServletPath()`, que o MockMvc devolve vazio e que só coincide com a URI quando o dispatcher
está em `/`. Em execução real com o dispatcher em `/` os dois valores coincidem — nenhuma resposta
de produção muda.

### Precedência (EX-2) — preservada

`ExceptionHandlerConfig` continua `@ConditionalOnMissingBean(annotation = ControllerAdvice.class)`:
consumidor com advice próprio continua com o dele. `HandlerPrecedenceTest` segue verde e intocado.

### Provas

`gems-exception`: **28 provas** (eram 18). Novas: `AuthorizationDeniedRespondeForbiddenTest` (2),
`RequestErrorsTest` (2), `ConflictExceptionTest` (3); `ErrorStatusCoverageTest` ampliado (+3: 409,
404, 400 de parâmetro). Mutação executada e restaurada: religar `handleClaimsException` → 1 prova
cai, nomeando o método.

---

## Dependências (Dependabot, PRs #24, #26, #30, #31, #32)

| Dependência | 3.0.0 | 3.1.0 |
| :--- | :--- | :--- |
| `spring-boot-starter-parent` | 4.1.0 | **4.1.1** |
| `springdoc-openapi-starter-webmvc-ui` | 3.0.3 | **3.1.1** |
| `software.amazon.awssdk:s3` | 2.46.15 | **2.54.16** |
| `keycloak-admin-client` | 26.0.4 | **26.0.12** |
| `actions/setup-java` (CI) | 5 | **6** |

Reator verde com os cinco antes e depois do merge: 176 provas em `main` (`fe3bf64`), 186 nesta
versão.

---

## Migração a partir da 3.0.0

Nenhum passo obrigatório. Quem tinha handler local para 403, 404/400 ou 409 pode apagá-lo — o
Meduc faz isso na mesma rodada (bloco G6-Meduc), e a prova de equivalência dele é o
`ErrorEnvelopeIntegrationTest` com as asserções de status intocadas.

```xml
<dependency>
    <groupId>br.com.gems</groupId>
    <artifactId>gems-bom</artifactId>
    <version>3.1.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```
