# Especificação — Auditoria e administração Keycloak 3.2.0

**Branch**: `feature/auditoria-keycloak-3.2.0`
**Base**: `main@137f18f` (Java GEMS SDK 3.1.0)
**Origem**: D-P01/D-P02 da rodada `002-conformidade-gems` do sample e C10-SDK da rodada 010 do Meduc.

## Objetivo

Publicar uma evolução estritamente aditiva que permita a consumidores substituir implementações
locais de auditoria e administração do Keycloak sem perder identidade estável, correlação,
compensações, grupos ou composição de roles.

## User stories

### US1 — Contexto estável na auditoria (P1)

Como aplicação auditada, quero fornecer identidade estável e correlação à trilha, para consultar o
autor histórico sem depender do login atual e relacionar operações do mesmo fluxo.

**Aceite independente**: com o recurso explicitamente habilitado, o writer grava os dois valores na
mesma transação; sem provider ou sem habilitação, o comportamento 3.1.0 permanece.

### US2 — Ciclo administrativo completo (P1)

Como aplicação que administra identidades, quero criar, consultar, alterar, habilitar, suspender e
excluir usuários, com snapshot/restauração de grupos e compensação da criação, para retirar serviços
locais sem reduzir garantias.

**Aceite independente**: falha ao definir senha remove o usuário recém-criado; snapshot seguido de
restauração recompõe dados, situação e grupos.

### US3 — Realm roles idempotentes (P1)

Como bootstrap de autorização, quero criar roles e completar compostas sem apagar concessões extras,
para derivar o realm do catálogo de ações com segurança em inicializações concorrentes.

**Aceite independente**: segunda execução não escreve; composição acrescenta somente filhos diretos
ausentes e não confunde role efetiva com mapeamento direto.

## Requisitos funcionais

- **FR-001**: a entrega MUST preservar compilação e comportamento público da 3.1.0.
- **FR-002**: a versão planejada MUST ser 3.2.0 MINOR; quebra comprovada volta ao product owner.
- **FR-003**: auditoria MUST aceitar contexto opcional com identidade estável e correlação.
- **FR-004**: ausência do novo provider MUST produzir contexto vazio, sem fabricar identificador.
- **FR-005**: escrita das novas colunas MUST ser opt-in e desligada por padrão, para schemas 3.1.0.
- **FR-006**: quando habilitada, a trilha MUST usar a mesma conexão/transação da operação auditada.
- **FR-007**: correlação MUST ser resolvida uma vez por operação auditada.
- **FR-008**: sigilo, destino, nome/tipo do ator e rollback atuais MUST permanecer intactos.
- **FR-009**: o gateway 3.1.0 MUST permanecer com as mesmas assinaturas.
- **FR-010**: novas operações MUST viver em interfaces públicas especializadas, sem método abstrato
  novo em `KeycloakAdminGateway`.
- **FR-011**: ciclo de usuário MUST cobrir leitura/snapshot, alteração, enable/disable e exclusão.
- **FR-012**: criação MUST compensar falha de senha removendo o usuário e preservar falha de
  compensação como suprimida.
- **FR-013**: snapshot/restauração MUST preservar nome, username, email, situação, atributos e grupos.
- **FR-014**: operações de grupo MUST permitir listar, entrar e sair por identificador.
- **FR-015**: role MUST ser criada idempotentemente; conflito concorrente equivale a sucesso.
- **FR-016**: composta MUST acrescentar filhos diretos ausentes, sem remover filhos extras.
- **FR-017**: toda falha externa MUST continuar como `KeycloakAdminException` sanitizada.
- **FR-018**: BOM, POMs, documentação, consumer smoke, PR, CI, merge, publicação e resolução limpa
  MUST comprovar a release antes de liberar consumidores.

## Edge cases

- Provider de auditoria devolve `null`, contexto parcial ou strings em branco.
- Feature de colunas ligada sobre schema antigo: falha ruidosa, nunca fallback silencioso.
- Falha de senha seguida de falha na exclusão compensatória.
- Usuário ausente durante snapshot/update/delete.
- Grupo já presente/ausente em join/leave repetidos.
- Duas instâncias criam a mesma role simultaneamente.
- Filho solicitado não existe ao compor role.
- Role herdada aparece como efetiva, mas não como filho direto.

## Fora de escopo

- Alterar realm, ambiente ou modelo de autorização de consumidores.
- Auto-configurar credenciais administrativas do Keycloak.
- Fornecer migrations de schema do consumidor.
- Remover ou depreciar API 3.1.0.
- Implementar adapters nos consumidores.

## Critérios mensuráveis

- **SC-001**: consumer smoke 3.1.0 compila sem alteração contra 3.2.0.
- **SC-002**: mutações de identidade e correlação ficam vermelhas e são restauradas.
- **SC-003**: commit/rollback com contexto mantém operação e trilha atômicas.
- **SC-004**: mutações de create/enable/delete/snapshot/grupos/roles ficam vermelhas.
- **SC-005**: reator inteiro verde com contagem não inferior às 186 provas da base.
- **SC-006**: quatro coordenadas resolvem em repositório Maven limpo usando somente o BOM.

## Clarificações necessárias

Nenhuma antes do planejamento. O portão deve confirmar as assinaturas públicas dos dois contratos,
o opt-in das colunas e a estratégia de interfaces especializadas.
