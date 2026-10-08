# Firebase Auth 3.4.0 — candidato independente

`gems-firebase-auth` verifica identidade Firebase e resolve permissões locais por ações concretas.
O módulo não depende de JPA multi-tenant, auditing, AWS, observability ou outro provedor de identidade.
`AuthorizationResolver`, `IdTokenVerifier` e `FirebaseAdminGateway` podem ser substituídos sem DataSource.
Firebase Admin está fixado em 9.11.0. A aplicação Firebase tem nome explícito; a SDK fecha somente a app que criou.

```yaml
gems:
  firebase:
    auth:
      enabled: true
      project-id: produto-ambiente
      check-revoked: true
      tenant-header: X-Tenant-Alias
      app-name: gems-firebase-auth
      jdbc-enabled: false
      minimum-applied-migration: 0
```

O consumidor declara sua `SecurityFilterChain` stateless, instala `FirebaseAuthenticationFilter`
uma vez e ativa method security. O registro servlet da SDK está desabilitado para evitar dupla execução.
O filtro limpa SecurityContext no início e finally, não armazena token/claims no principal e fornece
`AuthorizationContextAware`/`JwtAuthorizationContext` e authorities `ROLE_<AÇÃO>` do snapshot local.
Token inválido, revogado ou disabled resulta em 401; recusa local em 403; indisponibilidade remota em 503 sanitizado.
Endpoint sem Bearer segue sem autenticação; a chain do consumidor decide o acesso público ou 401.

O opt-in exige `AuthorizationCatalog`, inclusive com resolver customizado e sem DataSource.
Com MVC habilitado a SDK registra `TenantAuthorizationInterceptor` e obrigatoriamente
`EndpointAuthorizationScan.assertProtected` verifica todos os handlers, exceto o `BasicErrorController`
exato do Spring Boot. Controllers do consumidor exigem a classificação canônica e ações do catálogo.
OSIV deve estar desabilitado. O adapter empresarial instala `TenantScope.forTenant(alias)` somente após
resolução e limpa seus contextos no início e finally; a SDK de identidade não depende do contexto JPA.

## JDBC e migrations explícitas

Ative `gems.firebase.auth.jdbc-enabled=true` somente após aplicar
`classpath:db/changelog/gems-firebase-auth/master.xml` pelo Liquibase do consumidor. A SDK não executa DDL.
Todas as consultas usam o schema `security` qualificado e UUID/Instant com Clock injetado.
O consumidor fornece `AuthorizationCatalog` e pode fornecer `DelegationPolicy`; o default delega nada.
Provider/projeto e catálogo são validados no arranque: mudança de projeto ou escopo falha.
Ações desconhecidas preservam histórico, geram drift e não viram authority.

O JDBC é configurado depois da `DataSourceAutoConfiguration` do Boot. Os beans
`firebaseSecurityDataSource`, `firebaseSecurityJdbcClient`, `firebaseSecurityTransactionManager` e
`firebaseSecurityTransactions` são nomeados e têm `defaultCandidate=false`: a SDK os injeta por
`@Qualifier`. O Boot continua criando o manager empresarial JDBC ou JPA; `@Transactional` sem
qualificador usa esse manager empresarial. Não é necessário declarar um manager JPA manualmente.
JPA/Hibernate são dependências somente de teste deste módulo, sem transitividade de produção.

`firebaseSecurityDataSource` delega ao pool do consumidor com uma chave de recurso transacional
distinta. Assim, uma chamada de segurança dentro de uma transação empresarial usa outra conexão,
mantém seu commit/rollback e outbox independentes, e preserva a conexão, EntityManager e schema
empresariais. Uma suspensão local confirmada permanece vigente mesmo se o negócio fizer rollback.
Operações aninhadas usando o mesmo manager de segurança continuam na mesma transação REQUIRED,
preservando a atomicidade de grants/bootstrap e eventos. Dimensione o pool para as duas conexões
quando houver chamadas de segurança durante transações empresariais.
A resolução automática aceita os providers físicos exatos `HikariDataSource`, `PGSimpleDataSource`,
`DriverManagerDataSource` e `SimpleDriverDataSource`, e cadeias das classes exatas
`TransactionAwareDataSourceProxy` e `DelegatingDataSource` simples. Remove apenas esses adapters
transparentes antes de criar a chave privada de segurança; preserva o pool e seu lifecycle, sem
fechá-lo. Cadeias nulas, cíclicas ou com mais de 32 níveis falham no arranque antes da aquisição
pela infraestrutura de segurança. Configure os adapters antes do arranque e não altere seus alvos
quando o contexto estiver ativo. O datasource físico deve adquirir conexões independentes, nunca
retornar um `ConnectionHolder` empresarial ou uma conexão única compartilhada.

`LazyConnectionDataSourceProxy`, routing, subclasses de adapters e proxies JDK não têm suporte
automático e falham explicitamente no arranque. O Lazy permite um alvo read-only sem getter público;
a SDK não descarta essa semântica, não inspeciona campos privados e não clona pools/adapters.
Outros adapters opacos de métricas, credenciais ou transações também precisam de override explícito;
não declare um wrapper opaco como se fosse um pool/driver físico. O contrato v1 usa um único pool
físico com schemas, sem promessa de seleção entre bancos ou destinos read-only.

Para esses consumidores, declare `firebaseSecurityDataSource` com `defaultCandidate=false`
apontando diretamente ao mesmo pool seguro por um wrapper não proprietário e com chave própria.
O consumidor é responsável por provar aquisição física independente, schema e cleanup inclusive
quando há rollback empresarial, e por manter a semântica necessária de seus adapters.
Overrides do manager/client de segurança devem usar essa DataSource qualificada e manter
`defaultCandidate=false` e isolamento físico; um override não autoriza confirmar a conexão empresarial.
A SDK não substitui nem ignora silenciosamente uma declaração customizada incompatível.
Garanta dependência de inicialização entre migrations e
`JdbcSecurityCatalog`, por exemplo declarando um override desse bean com `@DependsOn("securityLiquibase")`.
Uma configuração JDBC sem migrations já aplicadas falha deliberadamente.

`JdbcUserService` pré-provisiona Google ou agenda CREATE por UID novo sem senha.
UID já conhecido nunca tenta email; UID encerrado recusa. Um UID novo liga apenas `google.com`,
email verificado e exatamente um cadastro `GOOGLE_PENDING`, sob lock e constraints.
`JdbcTenantService` cria tenants PENDING e memberships novas.
As variantes com ação mínima criam própria conta/contexto na mesma transação do cadastro/membership.
Os beans padrão concedem CONSULTAR_PROPRIA_CONTA/CONSULTAR_PROPRIO_CONTEXTO_TENANT quando essas ações
existem no catálogo; variantes explícitas com nomes diferentes são exclusivas da capacidade de bootstrap.
`JdbcGrantService` gerencia perfis, grupos, heranças e grants imediatos com operações explícitas de concessão/revogação.
O consumidor autoriza a chamada administrativa com ações concretas na fronteira de serviço.
A SDK também exige ator confiável, tenant correspondente ao contexto autenticado e membership vigente;
recalcula suas ações no SQL a cada mutação. Grants diretos e todos os caminhos de perfil/grupo são
limitados à interseção catálogo ∩ delegáveis ∩ ações efetivas do ator. Alterações de containers no mesmo
tenant são serializadas para impedir que mudanças concorrentes contornem essa verificação.
Fim igual ao início é aceito e representa intervalo vazio; grants encerrados não reabrem.

Consuma as portas `UserAdministration`, `TenantAdministration`, `GrantAdministration` e
`SecurityAdministrationReader`, com records `AdministrationViews`, sem importar tipos JDBC/Firebase.
O reader oferece find/list de usuários, tenants, perfis, grupos, memberships e grants diretos/indiretos,
incluindo histórico opcional. `Query(afterId,limit,includeHistory)` usa cursor UUID e limite 1–1000;
`Page.nextCursor` é nulo ao final. Consultas de usuário/tenant/global-grants exigem ações globais
`CONSULTAR_USUARIO`, `CONSULTAR_TENANT`, `CONSULTAR_ACAO_GLOBAL`; consultas tenant exigem scope comprovado.
`Resolution.me()` e `tenantContext()` (também no `FirebaseAuthenticationToken`) fornecem nomes de tenants,
perfis/grupos e ações no mesmo snapshot autenticado, para `/me` e `/context`, sem queries duplicadas.

`SecurityActorProvider` é uma porta de backend, nunca DTO HTTP. O default aceita somente
`FirebaseAuthenticationToken`; ações recebidas da UI ou authorities antigas não substituem a consulta SQL.
Identidade/global são centrais: `INSERIR_USUARIO`, `ALTERAR_USUARIO`, `SUSPENDER_USUARIO`,
`REATIVAR_USUARIO`, `INSERIR_TENANT`, `ALTERAR_TENANT`, `SUSPENDER_TENANT`,
`CONCEDER_ACAO_GLOBAL` e `REVOGAR_ACAO_GLOBAL` precisam existir no catálogo GLOBAL e no ator vigente.
`TenantAdministratorBootstrap.initialize()` exige a ação central distinta `CRIAR_ADMINISTRADOR_TENANT`,
usuário BOUND com identidade/provider vigentes e ações tenant delegáveis. É idempotente e auditado;
não cria privilégios no primeiro login. A capacidade `trustedBootstrap(operatorId)` é exclusiva de
instâncias dedicadas de CLI/job verificadas pelo operador, não deve ser registrada no backend HTTP.
O bootstrap inicial por CLI verifica projeto/UID usando `FirebaseAdminGateway.getUser` antes de fornecer
essa capacidade; nenhuma capacidade é inferida de e-mail, payload ou uma role genérica ADMIN.

`JdbcIdentityCommandWorker.runOnce()` é chamado por job explícito do consumidor, fora de HTTP.
Suspensão fecha acesso e concessões localmente antes de DISABLE/REVOKE remoto. Falha externa mantém o bloqueio.
Comandos têm lease, idempotência por UID/versão, recuperação após restart e ACK com CAS e lease vigente.
Falhas usam `Clock` e backoff exponencial de 2 segundos até 1 hora, persistido em `dt_proxima_tentativa`.
A seleção ordena por próxima tentativa; comando falho não monopoliza a fila nem cria busy-loop.
REVOKE é uma barreira independente do estado desejado: uma nova versão ENABLE nunca o descarta.
ENABLE só é elegível após ACK de todas as revogações anteriores e só reativa SQL depois do próprio
ACK da versão atual; memberships/grants antigos permanecem encerrados.
Reatribuição requer linhas novas. Locks por usuário serializam mutations e chamadas remotas.
Durante PASSWORD_PROVISIONING, `update` recusa nome/e-mail com `ACCOUNT_PROVISIONING_IN_PROGRESS`.
Assim, CREATE remoto concluído antes de falha do ACK é repetido com o mesmo UID/e-mail/nome; o adapter
verifica o UID existente e reconhece apenas o objetivo original. A edição local pode ocorrer depois
do ACK/BOUND, sem transferir UID. Não se persiste senha ou link para recuperar o comando.
Envio de primeiro acesso/reset é operação nativa do cliente Firebase. Gerar link Admin não envia email;
links, passwords, tokens e segredos não são gravados na persistência de segurança.

Eventos SQL, delivery outbox e comandos são atômicos sob o manager de segurança.
`SecurityEventPublisher` é a porta de publicação sanitizada. A entrega afterCommit é uma tentativa;
`JdbcSecurityEventOutbox.publishNext()` recupera mensagens por UUID/lease e requer deduplicação do consumidor.
Os eventos são append-only e não precisam de `gems-auditing`.
Eventos carregam ator, tipo/ID do alvo, IDs das duas pontas da relação e ação, além de usuário/tenant/instante.
A delivery outbox tem tentativas, backoff por Clock, lease e CAS; eventos falhos não bloqueiam os seguintes.
Entrega após send antes de ACK pode repetir o mesmo UUID após restart: o publisher consumidor deve deduplicar.
O publisher padrão escreve argumentos SLF4J estruturados e sanitizados, sem MDC nem stack remoto.
Recusas de token/local/tenant são auditadas com códigos estáveis; o filtro retorna `X-Correlation-ID` UUID.
Eventos de recusa não incluem Bearer, valor de alias, payload, senha, token ou link de ação.

`JdbcTenantProvisioner.provision()` mantém advisory lock de sessão por alias, aplica o adapter `Migration`
e exige leitura da revisão efetivamente aplicada antes de READY. O adapter deve usar a conexão fornecida
sem fechá-la. Falha registra FAILED; unlock/restauração falhos abortam a conexão. O inventário
`JdbcTenantService.expectedSchemas()` contém nomes `tenant_<alias>` READY inclusive tenants suspensos.

## Bootstrap empresarial aditivo

```yaml
gems:
  tenant:
    enabled: true
    strict-alias: true
    validation-schema: modelo
    global-schema: administracao
    liquibase:
      changelog: classpath:db/changelog/tenant-master.xml
      startup-enabled: false
```

`validation-schema` prepara e migra o schema técnico antes do customizer Hibernate/ddl validate.
Spring Data inicializa sem tenant fictício; contexto ausente retorna sentinel inválido no bootstrap
e recusa conexão empresarial antes de SQL. Metadata e conexões de negócio restauram o schema original.
`strict-alias` exige ASCII, Locale.ROOT, reservados e máximo de 63 bytes contando o prefixo.
Sem essas propriedades o resolver/naming/sanitizer antigos continuam disponíveis.
`liquibase.startup-enabled` tem default true na SDK; false desliga ambos os caminhos de lote no arranque.
As migrations técnicas de validation-schema continuam precedendo o EntityManagerFactory.

## Gates sem projeto Firebase real

Full reactor: JDK 21 e `mvn -B clean verify`, com PostgreSQL 16 Testcontainers e transporte RSA/cert/lookup
fechado a rede real. Emulator gate: `powershell -NoProfile -File scripts/verify-firebase-auth.ps1`.
Pins: Node 24.16.0, npm 11.17.0, Firebase CLI 15.32.1 e projeto `demo-gems`, Auth local 127.0.0.1:9099.
O hostname de serviço Docker `auth-emulator:9099` também é aceito quando host/variável coincidem,
projeto é `demo-*` e todos os perfis são local/test/ci/demo; produção/projeto real continuam recusados.
Forneça a CLI local por `GEMS_FIREBASE_CLI_JS`; o script não instala ferramentas nem usa Firebase/gcloud global.
O gate usa `test-compile failsafe:integration-test failsafe:verify` com o perfil `firebase-emulator`,
sem flags de skip: executa a classe Failsafe `FirebaseEmulatorIT`. Todas as unitárias/PG/MVC/RSA são
executadas por `clean verify` sem emulator; RSA é prova separada
executada no full reactor sem FIREBASE_AUTH_EMULATOR_HOST. O gate usa emulators:exec e encerra só o processo que criou.
Publicação, consumidor remoto Maven com M2 isolado e review independente permanecem gates externos.

Referências consultadas: [verify ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens),
[Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth),
[manage sessions](https://firebase.google.com/docs/auth/admin/manage-sessions),
[manage users](https://firebase.google.com/docs/auth/admin/manage-users).
