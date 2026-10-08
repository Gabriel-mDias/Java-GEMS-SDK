# Contrato de implementação Java SDK

## Superfície independente

Artefato `br.com.gems:gems-firebase-auth`, candidato3.4.0 se mudança aditiva. Não depende de adapter do provedor anterior, JpaMulti, GemsObs ou AWS; gems-security-authorization é permitido. Ports, token verifier e Admin/Spring podem funcionar sem DataSource; JDBC services/schema security são opt-in separado. No hiddenDDL, nenhuma SecurityFilterChain da SDK. Manager JDBC nomeado/qualificado separado do manager JPA; serviços externos não participam da transação SQL.

Propriedades base `gems.firebase.auth.enabled`, `project-id`, `check-revoked`, `tenant-header`(X-Tenant-Alias). Sample checkrevoked=true; verifyIdToken default não basta. FirebaseApp nomeada: ConditionalOnMissingBean e cleanup somente se criada/owned pela SDK. Clock injetado, eventos sanitizados, sem token/header secreto/cause remota bruto.

Emulator somente opt-in local/test/ci com demo-id; proibição real/prod. Unsigned tokens do emulator precisam consulta extra de revogação/disabled mesmo check-revoked=false. Modo real prova RSA+cert/http lookup stub sem projeto real no CI. Ausência/invalidade/revogação/disabled=401; negativa local=403; indisponibilidade do provider=503 e falha fechada.

## Autenticação e contexto

Authentication implementsAuthorizationContextAware; retorna JwtAuthorizationContext real(globalProfiles,globalActions,tenantProfiles,tenantActions,Optional alias). Authorities ROLE_ação derivam catálogo e scope efetivo. `EndpointAuthorizationScan.assertProtected` registrado e `TenantAuthorizationInterceptor` obrigatório. Uma única Spring registration do filtro stateless. O scanner não é contornado com PublicEndpoint em auth/context.

Fluxo: limpar contexto no início → verificar token/projeto → resolver usuário/provider/identidade SQL → comprovar membership+tenant+READY+revisão → instalar alias em Authentication → TenantScope.forTenant → abrir EM/transaction de negócio → fechar scope → limpar security/tenant/actor em finally. OSIV=false; nenhum EntityManager antes da autorização. Global JPA usa TenantScope.global deliberado; ausência de alias nunca default. Não switchscope dentro de EM/tx. Async quando usado captura security+tenant+actor e restaura/limpa na thread.

## REST

| Verbo/rota | Classificação/ação | Contrato |
| :--- | :--- | :--- |
| GET /api/auth/me | GlobalEndpoint; hasRole CONSULTAR_PROPRIA_CONTA | user{id,name,email}, tenants[{alias,name}],globalActions[]; sem tenant obrigatório; grant atômico no pré-provisionamento |
| GET /api/auth/context | TenantEndpoint; hasRole CONSULTAR_PROPRIO_CONTEXTO_TENANT | header X-Tenant-Alias; tenant{alias,name},profiles[],groups[],actions[]; grant atômico na criação membership |
| GET /api/demo/public | PublicEndpoint | demonstra classificação pública deliberada |
| GET /api/demo/global | GlobalEndpoint; CONSULTAR_TENANT | snapshot global sem header obrigatório |
| GET /api/demo/tenant/read | TenantEndpoint; CONSULTAR_DEMO | leitura isolada |
| POST /api/demo/tenant/write | TenantEndpoint; ALTERAR_DEMO | validação/tx isoladas |
| POST /api/demo-registros/search | TenantEndpoint; CONSULTAR_DEMO | FilterParams+Pageable; projeção/campo restrito no service |
| GET /api/demo-registros/{id} | TenantEndpoint; CONSULTAR_DEMO | UUID; mesmo PK de outro tenant não vaza |
| POST /api/demo-registros | TenantEndpoint; INSERIR_DEMO | 201, DTO, validação acumulada |
| PUT /api/demo-registros/{id} | TenantEndpoint; ALTERAR_DEMO | 200; payload não troca tenant |
| DELETE /api/demo-registros/{id} | TenantEndpoint; EXCLUIR_DEMO | 204, softdelete |

Campo `VISUALIZAR_VALOR_RESTRITO` prova leitura/busca/gravação de service. Catálogo deriva AcaoSistema via gerador oficial Java, não JSON manual. Nenhuma ação ADMIN genérica autoriza tudo. Endpoints admin são pequenos e orientados a ações (consultar/insert/alterar/suspender/membership/grants), todos classificados, auditados e testados; tenant admin não recebe mutação de identidade/global. Central usa açãoGLOBAL distinta para bootstrap do primeiro admin de tenant; bootstrap CLI exige project+UID verificado Admin, idempotência e auditoria, sem firstlogin automático.

## JPA opt-in aditivo

Generalizar helpers técnicos modelo a partir do contrato Meduc nomeado, sem copiar fora READ. `gems.tenant.validation-schema=modelo`; `gems.tenant.liquibase.startup-enabled` defaulttrue SDK/defaultfalse sample. Configs antigas sem propriedades continuam iguais. Missing sentinel é identificador inválido como alias e falha antes de obter conexão/SQL; getAnyConnection restaura schema original; migrations modelo precedem EMddlvalidate. Prefix tenant_ com NOVO validator opt-in ASCII/LocaleROOT max56bytes/reservados/immutabilidade, preservando sanitize e SchemaNaming legados na MINOR. Ports auth sem JpaDep e contrato sample prova derivação coincidente.

## Gate release

BOM/docs/release notes+full clean verify e Auth Emulator precedem publicação. O workflow `Release` (`.github/workflows/release.yml`) roda no CI Ubuntu e é disparado pelo push da tag imutável `v3.4.0`, após aprovação humana; confirmar que a tag aponta para o SHA atual revisado e que sua versão coincide com o POM. Nesse SHA, `mvn clean verify` e Auth Emulator devem passar antes de `mvn deploy`. O workflow `Main Validation` (`.github/workflows/publish.yml`) somente valida a branch main; nenhum dos dois oferece `workflow_dispatch`. Depois da publicação autorizada, consumidor Maven com repo M2 isolado/clean deve provar resolução do artefato remoto e smoke independente antes da adoção no sample. Instalação local não é aceite. Published authorization public API e full reactor atual precisam compatibilidade testada antes de declarar3.4.0 MINOR.
