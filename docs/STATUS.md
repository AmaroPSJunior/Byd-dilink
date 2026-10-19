# Estado do projeto

Atualizado em 2026-10-19.

Objetivo ativo: corrigir a experiência de power HVAC sem depender de habilitar acessibilidade bloqueada pelo firmware.

Evidência: instalado 1.0.125; serviço Climate desabilitado/desconectado; permissões OEM de escrita exigem assinatura e não estão concedidas ao APK. Nenhum método externo permitido que execute power automaticamente foi demonstrado. Toques ADB históricos eram externos ao app.

Implementação em revisão: precheck real de permissões, resposta HTTP explícita de confirmação, fallback manual com escolha do motorista na central e orientação HTML sem pedir mudanças de segurança. Sem atuação física, instalação ou alteração de vidros/persiana.

Detalhes, commits e limites: [investigação HVAC](hvac-power-investigation.md). Próximo passo: revisar testes/CI do PR; usar fluxo manual nesta instalação. Automação exige interface OEM autorizada ou investigação separada de gateway Termux; não está resolvida por este patch.
