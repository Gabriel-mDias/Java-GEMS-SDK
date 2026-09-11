# GEMS SDK Java `3.0.0`

> **Estado: pronto para publicação, pendente do ato do product owner.** Os três pontos que os gates
> levantaram estão **fechados** — não-regressão do ADACI (verde sob JDK 21), o consumidor do
> `apoia-workspace` (desconsiderado, fixo na `1.1.0`) e o critério de aditividade (resolvido subindo
> esta entrega para **MAJOR**). A publicação é ato do product owner, com credencial dele — nenhum
> agente a dispara.

`2.0.1` → `3.0.0`. **MAJOR**, e a razão é uma só, nomeada: `gems-jpa-multi-tenant` quebra a API
pública, e o critério de aditividade da rodada (FR-026) não admite exceção. Ver
"[Por que MAJOR](#por-que-major)".

Em **quinze dos dezesseis módulos** esta entrega é estritamente aditiva: nenhuma API pública existente
mudou de assinatura, saiu ou recebeu marca de descontinuação — conferido contra a linha de base `2.0.1`
(marco `c39d389`) antes do release. **Zero `@Deprecated`** foi acrescentado em toda a SDK, e
`gems-model-mapper` segue listado como opção suportada. Quem não usa `gems-jpa-multi-tenant`
atravessa o MAJOR sem tocar em código.

---

## Por que MAJOR

A decisão de versão foi tomada pelo product owner em 2026-09-10, sobre um achado da conferência de
superfície pública (T102).

O critério da rodada era sem exceção: *"Nenhuma API pública existente MUST mudar de assinatura, ser
removida ou receber marca de descontinuação"* (FR-026). O roteiro autorizava nominalmente a mudança de
**comportamento** de `gems-jpa-multi-tenant` numa MINOR, sob a medida de zero consumidores na faixa —
mas **não** dizia nada sobre assinatura, e a decisão 15 do roteiro chegava a afirmar o contrário.

A conferência contra o `BaseRef` `c39d389` encontrou três quebras, todas nesse módulo. Havia três
saídas: estender a autorização e publicar em MINOR; subir para MAJOR; ou restaurar a compatibilidade
de origem. **Escolhida a segunda**: o critério se aplica ao pé da letra, e a versão passa a dizer a
verdade sobre o que a entrega faz. Nenhuma linha de código foi reaberta para acomodar a versão — o
código estava provado e continua como estava.

O argumento contra o MAJOR — "ninguém consome o módulo" — é verdadeiro e não foi considerado
suficiente: a versão descreve o contrato, não a contagem de consumidores do momento.

---

## Módulos novos

### `gems-mapstruct`

A configuração de mapeamento **recomendada para código novo**: `GemsMappingConfig`, um `@MapperConfig`
compartilhado com `componentModel = "spring"` e `unmappedTargetPolicy = ERROR`.

```java
@Mapper(config = GemsMappingConfig.class)
public interface OrganizacaoMapper {
    OrganizacaoResponse toResponse(Organizacao origem);
}
```

Campo de destino sem origem **reprova a compilação**, nomeando o campo. Ao ser adotada num projeto
existente a política vai reprovar builds — cada reprovação é um campo que hoje chega nulo em silêncio.
Baixá-la para `WARN` desmonta a razão de o módulo existir.

**`gems-model-mapper` continua de primeira classe e suportado.** MapStruct é o recomendado, não o
obrigatório, e não há depreciação.

**Armadilha de adoção:** o módulo põe `mapstruct-processor` no classpath de compilação, então quem não
declara `annotationProcessorPaths` recebe a geração por descoberta. Quem **já** declara precisa
acrescentar lá `lombok-mapstruct-binding` (depois do Lombok) e `mapstruct-processor` (por último) — a
omissão não quebra a compilação, apenas deixa de gerar o mapeador.

### `gems-auditing`

Trilha de auditoria no nível do Hibernate, `gems.auditing.enabled=true`, **opt-in por entidade** com
`@Auditable`. `@SensitiveField` registra que o campo mudou sem registrar os valores.

**O domínio nunca chama o escritor.** Um `Integrator` do Hibernate instala o listener;
`TransactionalAuditWriter` é package-private de propósito, e um teste de arquitetura garante que nada
fora do módulo o alcance.

Dois pontos de extensão `@ConditionalOnMissingBean`: `AuditActorProvider` (padrão: autor `SISTEMA`) e
`AuditTrailDestination` (padrão: schema único vindo de `gems.auditing.schema`).

`gems.auditing.schema` é **obrigatória e não tem padrão embutido**. Um padrão faria a SDK escolher, por
omissão, entre gravar a trilha junto do dado da organização e gravá-la num schema global — a decisão
que ela não deve tomar pelo consumidor.

### `gems-keycloak-admin`

`KeycloakAdminGateway` — as operações administrativas sobre o provedor de identidade na linguagem de
quem as chama, com `KeycloakAdminRestClient` como transporte. Sem auto-configuração: o consumidor
declara o bean.

Toda falha sai como `KeycloakAdminException`, e portanto como **502** no envelope do `gems-exception` —
indisponibilidade de terceiro não chega ao consumidor como 500.

**Nenhum campo de `KeycloakAdminProperties` tem valor padrão**, e o segredo não existe em código:
`baseUrl`, `realm`, `clientId` e `clientSecret` ausentes ou em branco são recusados na construção, então
o ambiente mal configurado falha ao subir, não em produção.

### `gems-security-authorization`

Autorização por **ação concreta**, nunca por perfil genérico. `AuthorizationCatalog.of(<enum>)` deriva o
catálogo de um enum do consumidor: ação escrita errada não compila.

`@PublicEndpoint` / `@GlobalEndpoint` / `@TenantEndpoint` declaram intenção;
`EndpointAuthorizationScan.assertProtected` lança em endpoint não marcado, para ser ligado a um teste;
`TenantAuthorizationInterceptor` **falha fechado** com **403** quando um endpoint de organização chega
sem organização comprovada — não 401, porque quem chegou ali está autenticado e o que falta é escopo.

`FrontendActionCatalogGenerator` **gera** a lista de ações que o frontend consome, a partir do mesmo
enum, com `verify(...)` para ligar ao build. A lista deixa de ser uma terceira cópia a manter em paridade
à mão.

---

## `gems-exception` — aditivo

Envelope de erro uniforme, e três famílias de falha que antes caíam no catch-all:

| Exceção | Status |
| :--- | :--- |
| `MethodArgumentNotValidException` | 400 |
| `AccessDeniedException` | **403** |
| `ExternalServiceException` | **502** |

`ExceptionResponseDTO` ganha `codigo` e `detalhes`; os campos existentes não mudaram, e o construtor
da forma anterior foi preservado à mão porque o `@AllArgsConstructor` passou a gerar uma assinatura
maior. `ErrorTypeEnum` ganha `VALIDACAO`, `ACESSO_NEGADO` e `SERVICO_INDISPONIVEL`.

---

## `gems-jpa-multi-tenant` — o módulo que faz esta entrega ser MAJOR

### Comportamento observável que mudou

1. **Falha fechada é o padrão.** Persistir sem tenant no contexto lança `TenantContextMissingException`
   em vez de cair num schema padrão.
2. **`TenantScope`** é a forma suportada de entrar e sair de um tenant: fecha mesmo que o corpo lance, e
   o fechamento **restaura o escopo anterior** em vez de apagar o contexto.
3. **Um prefixo, num lugar só, e o padrão passou a ser `tenant_`.** Antes,
   `gems.tenant.schema-prefix` era lido em três lugares sob **dois padrões diferentes**
   (`instituicao_` e `client_tenant_`): a migração rodava num schema e o tráfego lia de outro, sem erro
   em lugar algum. `TenantSchemaNaming` passa a ser o único leitor.
4. **A migração roda no provisionamento**, não no primeiro uso. O caminho de persistência apenas
   verifica e recusa com `TenantSchemaNotReadyException`.
5. **O escopo global é explícito** e exige `gems.tenant.global-schema` — sem padrão.

### API pública que quebra

Código escrito contra a `2.0.x` deste módulo **não compila** na `3.0.0`.

| O que era | O que é | Consequência |
| :--- | :--- | :--- |
| `public class JpaTenantContext` | `public final class`, construtor privado | quem estendia ou instanciava não compila |
| `JpaTenantContext.DEFAULT_TENANT = "public"` | **removida** | falhar fechado não deixa padrão para nomear |
| `MultiTenantLiquibaseConfig(DataSource)` + campos `@Value` | construtor de quatro argumentos, e **não é mais `@Component`** | passa a ser ligado por configuração, não por varredura |

### Migrando de `2.0.x`

- Trocar `JpaTenantContext.DEFAULT_TENANT` por um valor do próprio consumidor, ou por
  `gems.tenant.global-schema` quando a intenção era o escopo global.
- Declarar `MultiTenantLiquibaseConfig` como bean, passando as quatro dependências, em vez de contar
  com a varredura de componentes.
- Definir `gems.tenant.global-schema` e revisar `gems.tenant.schema-prefix`. **Schemas criados sob
  `instituicao_` ou `client_tenant_` não são encontrados** pelo padrão novo `tenant_`, e o sintoma é
  dado que "some" sem erro: ou renomeie os schemas, ou fixe o prefixo antigo na configuração.
- Envolver toda escrita numa `TenantScope`; sem contexto, a persistência agora recusa.

---

## Compatibilidade

- Em **quinze dos dezesseis** módulos, zero API pública existente alterada ou removida. A exceção é
  `gems-jpa-multi-tenant`, nas tabelas acima — e é ela que motiva o MAJOR.
- **Zero** marca de descontinuação em toda a SDK.
- `gems-model-mapper` segue listado como opção suportada.
- Nenhum dos quatro módulos novos depende de `gems-observability`, direta ou transitivamente: o piso de
  observabilidade desta rodada é log estruturado. A regra é executável — `maven-enforcer-plugin` com
  `bannedDependencies`, provado por mutação.
- `gems-bom` declara os quatro módulos novos.
- Reator completo verde: `mvn -B clean install`, 17 módulos.

---

## Pontos levantados pelos gates — todos fechados

### A · Não-regressão do ADACI — **VERDE** ✅

Instalada a JDK 21 (`21.0.12.1`, em `D:\Program Files\java\jdk21`), o build foi repetido com
`JAVA_HOME` apontado para ela:

```
JAVA_HOME=D:\Program Files\java\jdk21
.\mvnw.cmd -B clean install "-Dgems.version=<esta versão>"   em adaci-project/backend
→ BUILD SUCCESS · 6 módulos · Tests run: 69, Failures: 0, Errors: 0
```

Os 69 erros da primeira tentativa eram **exatamente** as provas do `adaci-deploy` que não conseguiam
sequer instanciar proxy sob JDK 26. Confirmado: defeito de toolchain local, **não** regressão da SDK. O
ADACI constrói e passa **sem nenhuma alteração no repositório dele** (SC-008) — a versão da SDK entrou
pela linha de comando.

Fica registrado, para uma rodada própria: `modelmapper` 3.2.6 **não funciona sob JDK 26**. Quem subir
de JDK antes disso ser tratado encontra o mesmo erro.

### B · Consumidor de `gems-jpa-multi-tenant` — **desconsiderado por decisão** ✅

O único consumidor encontrado é o `apoia-workspace`, fixo na `1.1.0`. O product owner decidiu em
2026-09-10 desconsiderá-lo: ele não está na faixa 2.x, e em Maven a versão é exata.

> O alerta que a decisão não apaga: se o `apoia-workspace` subir para esta versão, encontra as cinco
> mudanças de comportamento **e** a quebra de API acima. A do prefixo de schema é a que não dá
> sintoma. A seção "Migrando de `2.0.x`" vale para ele também.

### C · Critério de aditividade (FR-026 / SC-007) — **resolvido pelo MAJOR** ✅

Era o ponto que restava. O product owner decidiu em 2026-09-10 aplicar o critério ao pé da letra e
subir a entrega para `3.0.0`, em vez de estender a autorização ou reabrir o código. Ver
"[Por que MAJOR](#por-que-major)".

---

## Registro histórico do ponto A

### O diagnóstico original — vermelho, e a causa **não** era esta rodada

`.\mvnw.cmd -B clean install` em `adaci-project/backend` contra esta rodada:
**431 provas, 0 falhas, 69 erros**, todos em `adaci-core`, todos `org.modelmapper.ConfigurationException:
Failed to instantiate proxied instance`.

A causa foi isolada, e ela **antecede a rodada**:

| Versão da SDK | Resultado no ADACI | `modelmapper` |
| :--- | :--- | :--- |
| `2.0.0` | **verde** — 0 erros | 3.2.4 |
| `2.0.1` (marco `c39d389`, base desta rodada) | **69 erros** | 3.2.6 |
| esta rodada | **69 erros**, os mesmos | 3.2.6 |

A subida de `modelmapper` 3.2.4 → 3.2.6 foi feita na **2.0.1, que já está publicada**. Esta rodada não
acrescenta nenhuma falha ao ADACI.

A causa raiz é o Byte Buddy embutido no `modelmapper` 3.2.6, que lança
`UnsupportedOperationException` em `JdkClassWriter.<init>` ao gerar proxy — incompatibilidade com o
formato de classe da JDK 26. Resolvido ao rodar sob JDK 21, que é o alvo da SDK.

### O consumidor que apareceu na reverificação

T097 existia para reverificar, imediatamente antes do release, a medida que sustentava o MINOR. A
reverificação encontrou `D:\repos\apoia-workspace\backend\apoia-core\pom.xml` declarando
`gems-jpa-multi-tenant` na versão `1.1.0`, fixa (último commit do repositório: 2026-07-10).

O roteiro (§10, risco 2) dizia que, se um consumidor aparecesse, a rodada pararia e a versão seria
reavaliada. O gatilho foi submetido ao product owner e **decidido em 2026-09-10: desconsiderar**, pela
versão fixa fora da faixa. A reavaliação de versão acabou acontecendo de outro modo, pelo ponto C.
