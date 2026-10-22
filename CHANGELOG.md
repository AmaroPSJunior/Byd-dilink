# Histórico de mudanças

Mudanças relevantes entregues no projeto, em ordem cronológica inversa.

## Não lançado

- O inicializador do Codex agora herda o `notify` global do usuário; removido o TTS específico do repositório, evitando duplicação entre projetos.

- Ampliada a coleta read-only do Inspector: logcat bruto sem filtro textual, Settings, propriedades, serviços/dumpsys/HAL, processos e inventários BYD/kernel sujeitos às permissões do UID.
- Adicionado modo de experimento com intensidades LOW/NORMAL/DEEP, baseline com indicador de prontidão, snapshots por marcador, diff, ranking heurístico, filtros de evidência e exportação JSON/relatório legível.
- Registrada a auditoria técnica e os limites em `docs/inspector-audit-expansion.md`.
- Adicionada estrutura de continuidade do projeto com instruções, estado atual e notas locais opcionais.
- Adicionado capturador ADB somente de leitura para correlacionar callbacks OEM de iluminação com sessões do Inspector.

## 2026-09-30 — Inspector: ampliação de sensores

- O snapshot agora enumera todos os sensores Android visíveis e seus metadados, em vez de listar apenas tipos acompanhados.
- O fluxo de leitura passiva inclui tipos ambientais e de luz/proximidade, mantendo amostragem limitada e sem executar comandos no veículo.
- Corrigido o encerramento do coletor logcat para aguardar o processo antes de consultar seu código de saída.
- Registradas referências públicas e limitações de permissões/FIDs em `docs/inspector-sensor-expansion.md`.
