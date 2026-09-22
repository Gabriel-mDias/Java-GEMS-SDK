# Roteiro — Java GEMS SDK 3.2.0

**Status**: planejamento aprovado; fase 3 liberada para execução sequencial, começando por SDK-AUD.

## 1. Objetivo e escopo

Entregar `gems-auditing` com identidade/correlação opt-in e `gems-keycloak-admin` com lifecycle,
compensações, snapshots, grupos e roles, preservando toda API 3.1.0.

## 2. Decisões

1. 3.2.0 MINOR somente com consumer smoke 3.1.0 verde.
2. `AuditActor` não muda; contexto novo é separado.
3. Colunas novas ficam desligadas por padrão.
4. `KeycloakAdminGateway` não recebe métodos abstratos.
5. Snapshot usa tipos próprios da SDK.
6. Roles compostas são aditivas e consultam filhos diretos.
7. Nenhuma credencial/auto-config Keycloak é acrescentada.

## 3. Contratos e impacto

- [Contrato de auditoria](contracts/auditing-3.2.0.md)
- [Contrato Keycloak](contracts/keycloak-admin-3.2.0.md)
- Consumidores 3.1.0 continuam compilando e executando sem migration/configuração nova.
- Consumidores que habilitam contexto devem migrar duas colunas antes de ligar a propriedade.
- C11/C12 do sample só abrem depois da publicação e resolução limpa.

## 4. Testes e mutações

Auditoria: superfície pública, schema antigo, opt-in, id, correlação, sigilo e rollback. Keycloak:
criação/compensação, lifecycle, snapshot/restauração, grupos, roles e composição. Toda rede nova é
mutada e restaurada. Reator inteiro e consumer smoke fecham a release.

## 5. Divisão e gates

28 tarefas: SDK-AUD 10, SDK-KC 12, SDK-REL 6. Contrato/aceite ficam no orquestrador; implementação
especializada pode ser delegada por pacotes fechados; verificação mecânica não substitui repetição
do gate pelo orquestrador. Nenhum delegado comita.

Baseline focado em 2026-09-21: reator de sete módulos verde em 28,052 s — utils 38,
exception 28, JPA 4, auditing 27, Keycloak Admin 10 e authorization 24; zero falhas/erros/skips.

## 6. Analyze incorporado

| Achado | Severidade | Decisão |
| :-- | :-- | :-- |
| Mudar componentes de `AuditActor` quebraria binário/fonte | CRITICAL | evitado por `AuditContext` separado |
| SQL sempre com colunas novas quebraria schema 3.1.0 | CRITICAL | opt-in desligado por padrão |
| Método abstrato novo no gateway quebraria implementores | CRITICAL | interfaces especializadas |
| Snapshot expondo `UserRepresentation` congelaria API externa | HIGH | record próprio da SDK |
| `listEffective` confunde herança e composição direta | HIGH | filhos diretos no contrato/teste |
| `restoreUser` não pode recriar id excluído | HIGH | contrato limita restauração a estado/grupos existentes |
| Falha de compensação poderia esconder causa primária | MEDIUM | registrada como suprimida |

Cobertura: 18/18 FR e 6/6 SC; zero achado CRITICAL pendente e zero violação das instruções locais.

## 7. Prompts SDD da rodada

| Ordem | Comando | Situação | Saída |
| :-- | :-- | :-- | :-- |
| 1 | `/speckit-specify` | concluído em 2026-09-21 | `spec.md`, 3 histórias, FR-001..018, SC-001..006 |
| 2 | `/speckit-clarify` | dispensado por escrito | zero ambiguidade material; decisões de API voltam no portão |
| 3 | `/speckit-plan` | concluído em 2026-09-21 | `plan.md`, `research.md`, dois contratos e quickstart |
| 4 | `/speckit-tasks` | concluído em 2026-09-21 | 28 tarefas: 10 AUD, 12 KC, 6 REL |
| 5 | `/speckit-analyze` | concluído em 2026-09-21 | 0 CRITICAL pendente; 18/18 FR e 6/6 SC cobertos |
| 6 | `roteiro.md` | aprovado em 2026-09-21 | SDK-AUD liberado; SDK-KC e SDK-REL mantêm a ordem dos checkpoints |
| 7 | `/speckit-implement` | em curso em 2026-09-21 | **SDK-AUD verde**; próximo checkpoint SDK-KC |

**Checkpoint SDK-AUD — verde em 2026-09-21:** T002–T010 concluídas. O contrato ganhou
`AuditContext`, provider público e default vazio; o SQL 3.1.0 permanece o caminho padrão e as duas
colunas novas só entram com opt-in. A revisão do orquestrador preservou também a assinatura pública
3.1.0 de `AuditingAutoConfiguration`. Test-first vermelho registrado em T002/T003/T005; mutações
independentes de id, correlação, opt-in e commit indevido ficaram vermelhas e foram restauradas.
Gate final: `mvn -B -pl gems-auditing -am test` com **35 testes**, zero falhas/erros/skips;
`git diff --check` sem erro. Nenhum arquivo fora do pacote foi alterado.

## 8. Critérios de aprovação

### Para liberar implementação

- [x] A superfície pública dos dois contratos está aceita.
- [x] O opt-in de colunas e o default 3.1.0 estão aceitos.
- [x] A divisão 10/12/6 e os gates/mutações são suficientes.
- [x] A classificação 3.2.0 MINOR condicionada ao smoke está aceita.

> ✅ **PORTÃO SDK-P00 APROVADO pelo product owner em 2026-09-21.** “aprovo” confirma as decisões
> da seção 2, a superfície pública aditiva dos contratos de auditoria e Keycloak, o opt-in seguro
> para schemas 3.1.0, o raio de impacto, a divisão 10/12/6 e a classificação 3.2.0 MINOR condicionada
> ao consumer smoke. **A fase 3 está liberada, começando somente por SDK-AUD.** Isto não aprova a
> release, a publicação, SDK-KC antecipadamente nem qualquer homologação do sample ou da rodada 010.

### Para concluir

- [ ] 28 tarefas concluídas.
- [ ] Consumer smoke 3.1.0 e reator inteiro verdes.
- [ ] PR/CI/merge/publish identificados.
- [ ] Quatro coordenadas resolvidas em cache limpo via BOM.
