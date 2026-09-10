# GEMS SDK Java `2.1.0`

> **Estado: pronto para revisão, não liberado para publicação.** Dois pontos abertos, ambos
> decisão do product owner, estão nomeados ao fim deste documento. A publicação é ato dele, com
> credencial dele — nenhum agente a dispara.

`2.0.1` → `2.1.0`. MINOR: quatro módulos novos, nenhuma API pública existente alterada, removida ou
depreciada. Um módulo muda de comportamento observável — ver "A ressalva do MINOR".

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

`ExceptionResponseDTO` ganha `codigo` e `detalhes`; os campos existentes não mudaram.
`ErrorTypeEnum` ganha `VALIDACAO`, `ACESSO_NEGADO` e `SERVICO_INDISPONIVEL`.

---

## A ressalva do MINOR — `gems-jpa-multi-tenant`

Este módulo **muda de comportamento observável**, e a versão é MINOR sob uma medida explícita: ele não
tinha consumidor declarado. Ver o ponto aberto **B** ao fim deste documento — a reverificação encontrou
um consumidor, e a decisão é do product owner.

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

---

## Compatibilidade

- **Zero** API pública existente alterada, removida ou depreciada.
- `gems-model-mapper` segue listado como opção suportada.
- Nenhum dos quatro módulos novos depende de `gems-observability`, direta ou transitivamente: o piso de
  observabilidade desta rodada é log estruturado.
- `gems-bom` declara os quatro módulos novos.
- Reator completo verde: `mvn -B clean install`, 17 módulos.

---

## Pontos abertos — decisão do product owner

Os dois vieram dos gates da própria rodada. Nenhum é opinião: os dois têm medida.

### A · Não-regressão do ADACI não está verde nesta máquina, e a causa **não** é esta rodada

`.\mvnw.cmd -B clean install "-Dgems.version=2.1.0"` em `adaci-project/backend`:
**431 provas, 0 falhas, 69 erros**, todos em `adaci-core`, todos `org.modelmapper.ConfigurationException:
Failed to instantiate proxied instance`.

A causa foi isolada, e ela **antecede a rodada**:

| `gems.version` | Resultado no ADACI | `modelmapper` |
| :--- | :--- | :--- |
| `2.0.0` | **verde** — 0 erros | 3.2.4 |
| `2.0.1` (marco `c39d389`, base desta rodada) | **69 erros** | 3.2.6 |
| `2.1.0` (esta rodada) | **69 erros**, os mesmos | 3.2.6 |

A subida de `modelmapper` 3.2.4 → 3.2.6 foi feita na **2.0.1, que já está publicada**. Esta rodada não
acrescenta nenhuma falha ao ADACI.

A causa raiz do erro é o Byte Buddy embutido no `modelmapper` 3.2.6, que lança
`UnsupportedOperationException` em `JdkClassWriter.<init>` ao gerar proxy — incompatibilidade com o
formato de classe da **JDK 26**, que é a instalada nesta máquina. **O CI do ADACI roda JDK 21**, e a SDK
tem como alvo a 21; é provável que lá não ocorra. **Não foi possível provar isso aqui**: não há JDK 21
instalada nesta máquina.

Decisão pedida — uma das três:

1. rodar o build do ADACI em JDK 21 (CI ou local) e, se verde, fechar o ponto como defeito de toolchain;
2. baixar `modelmapper.version` para 3.2.4 nesta 2.1.0, corrigindo por cima um problema que nasceu na
   2.0.1;
3. publicar a 2.1.0 com o ponto registrado e tratar a incompatibilidade com JDK 26 numa rodada própria.

### B · Apareceu um consumidor de `gems-jpa-multi-tenant`

T097 existia para reverificar, imediatamente antes do release, a medida que sustenta o MINOR. A
reverificação encontrou:

`D:\repos\apoia-workspace\backend\apoia-core\pom.xml` declara `gems-jpa-multi-tenant`, **versão
`1.1.0`**, fixa. Último commit do repositório: 2026-07-10.

O que isso significa, sem interpretação a favor:

- O consumidor **não está na faixa 2.x**. Em Maven a versão é exata, então a 2.1.0 não o alcança
  sozinha — o MINOR não muda nada para ele **hoje**.
- Se ele subir para a 2.x em qualquer momento, encontra **as cinco** mudanças de comportamento acima.
  A do prefixo é a mais perigosa: os schemas dele foram criados sob o padrão antigo, e a 2.1.0 passa a
  procurar `tenant_*`. O sintoma é dado que "some", sem erro.

O roteiro (§10, risco 2 e T097) diz que, se um consumidor aparecer, **a rodada para e a versão é
reavaliada**. Se esse gatilho vale para um consumidor fixo na 1.1.0 é decisão do product owner, não do
executor.
