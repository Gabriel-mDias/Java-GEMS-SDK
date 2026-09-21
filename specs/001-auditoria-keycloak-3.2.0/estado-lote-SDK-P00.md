# Estado — SDK-P00

| Controle | Valor |
| :-- | :-- |
| Checkpoint | SDK-P00 |
| Pré-requisitos | C09 verde; autorização inicial do product owner em 2026-09-21 |
| Política de sessão | nova |
| SHA anterior | `137f18f` |
| Estado inicial | aberto |
| Estado final | aguardando portão humano sobre os artefatos reais |
| Gate focado | sete módulos, exit 0, BUILD SUCCESS em 28,052 s |
| Ponto de recuperação | registrar aprovação no roteiro e preparar somente SDK-AUD |

## 1. Rollback

Antes de implementação, abandonar nomeadamente a branch `feature/auditoria-keycloak-3.2.0` remove
somente os artefatos documentais deste checkpoint. Nenhum módulo Java foi alterado.

## 2. Concluído

- Branch criada de `main@137f18f`.
- `spec`, checklist, plan, research, dois contratos, quickstart, 28 tasks, roteiro e analyze criados.
- Índice regenerável validado na fase 2 (Plan).
- Baseline focado verde: auditing 27, Keycloak 10 e authorization 24, além das dependências.

## 3. Em curso

- **Tarefa:** portão SDK-P00 real.
- **Arquivo:** `roteiro.md` §8.
- **Feito:** documentos e evidências existem.
- **Falta:** aprovação explícita das assinaturas e decisões; zero código antes dela.

## 4. Não iniciado

T001–T028; SDK-AUD, SDK-KC e SDK-REL.

## 5. Arquivos tocados

Somente `.specify/feature.json` e `specs/001-auditoria-keycloak-3.2.0/**`.

## 6. Decisões fora do pacote

A aprovação anterior foi dada antes de existir a evidência indicada no dossiê central. Ela foi
preservada como autorização para preparar SDK-P00, não convertida silenciosamente em aceite de
assinaturas ainda inexistentes.

## 7. O que está quebrado agora

Nada. Módulos de produção estão sem diff e o baseline focado está verde.

## 8. Retomada

Após o portão, preparar um único pacote fechado SDK-AUD; não iniciar SDK-KC ou release junto.
