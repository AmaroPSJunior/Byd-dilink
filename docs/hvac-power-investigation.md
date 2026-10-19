# Energia HVAC sem habilitação de acessibilidade

Investigação em 19/10/2026. Base: `effeac19fadc48aa54d04e7cd7fdf0afcf94757c`.
Nenhuma escrita veicular, toque, instalação, concessão de permissão ou mudança de segurança foi executada nesta investigação. Nenhum merge/release é autorizado por este relatório. A condição de veículo parado não foi aferida por uma fonte de marcha/velocidade nesta sessão; por isso nenhum ensaio de atuação foi iniciado.

## História e regressão delimitada

- [a38339e](https://github.com/AmaroPSJunior/Byd-dilink/commit/a38339ef866947873b50e8c743cd2003f431f4ef): introduziu `ClimateAccessibilityService`, procura de `com.byd.airconditioning:id/front_ac_power_id`, gesto e verificação da seleção OEM.
- [b9af395](https://github.com/AmaroPSJunior/Byd-dilink/commit/b9af395f7b4792261ab0a0578a35ba196121f676): mudou `lineTo(x,y)` para `lineTo(x+1f,y)` num gesto de 90 ms. A alteração continua no main; não houve remoção desse clique.
- [c35549e](https://github.com/AmaroPSJunior/Byd-dilink/commit/c35549eae5531a698e46ea923b9d1fa21e44e3e2): passou a abrir painel manual quando o controle está restrito, sem abrir configurações. Isso explica a experiência atual, mas não prova quando/por que a autorização anterior mudou.
- [bd16acb](https://github.com/AmaroPSJunior/Byd-dilink/commit/bd16acb60a5e0ed2dd98a3dbb4eb71e378d8dfbb) priorizou o serviço já autorizado; [effeac1](https://github.com/AmaroPSJunior/Byd-dilink/commit/effeac19fadc48aa54d04e7cd7fdf0afcf94757c) acrescentou polling de 350 ms. Nenhum deles permite operar com serviço desabilitado.

Há ainda um caminho distinto: o registro local de 28/09 contém `adb shell input tap` pelo Termux e observações OEM. O ensaio e a restauração constam em [adb-readonly-probe.md](adb-readonly-probe.md#controle-reversível-verificado-pela-interface-do-veículo). Esse shell não era executado pelo APK. O título de um commit ou uma chamada de gesto aceita não comprovam efeito físico histórico do APK. Não foi demonstrada uma regressão de código que tenha retirado um caminho automático independente de acessibilidade.

## Evidência atual e alternativas

| Caminho | Evidência | Capacidade e limite |
| --- | --- | --- |
| Acessibilidade própria | App 1.0.125, serviço declarado mas ausente de enabled/bound/binding; MagicSentry bound e MagicManager binding | O proprietário relata bloqueio de habilitação pelo firmware. Não é solução para esta instalação. Não foi tentada habilitação. |
| Manager `airconditioning` | `processAcPowerButtonClicked(boolean)` existe; negativa histórica `BYDACQUISITION_SEND_BUFFER` documentada | `pm list permissions -f` atual identifica `BYDAUTO_AC_SET` e `BYDACQUISITION_SEND_BUFFER` como `signature`. O APK não possui concessões. O código agora consulta concessões reais antes de tentar power. |
| SDK BYDAuto | Fonte SDK extraída localmente: `BYDAutoAcDevice` exige `BYDAUTO_AC_SET` nos setters | Reflexão ou manifesto não concedem a permissão. Nenhum wrapper que suprime prechecks foi acrescentado. |
| Intent OEM | Manifesto atual e `FullScreenMainActivity.setFragmentIdFromIntelligentVoice`: `OPEN_AIR_CONDITIONING`, `...PM2P5`, `...FUNCTION` selecionam fragmentos | Nenhum contrato público para power on/off foi identificado nessas entradas. Não foram enviados extras especulativos ou broadcasts. Abrir painel não comprova power. |
| MagicManager 2.8.08 | Provider extraído é exportado com `INTERACT_ACROSS_USERS_FULL`; runtime confirma pacote/provider | A permissão `magiccore.permission.API` não remove a proteção do provider. API interna JavaScript/getters não equivale a API externa de controle. Busca por documentação pública não encontrou contrato de delegação HVAC utilizável. Não foi invocado provider protegido. |
| ADB autorizado no Termux | Transporte device, modelo `BYD AUTO`, Android 10; `id` retorna UID 2000(shell), grupo input | Pode oferecer contexto distinto, mas não foi testado toque nesta sessão. Não existe no produto transporte autenticado HTML/APK → Termux → ADB, nem validação atual do nó/estado. Executar `Runtime.exec` no APK não transforma seu UID em shell. |

O [SDK V1.0.5 no domínio BYD](https://oip.byd.com/uploads/20210824/46cd2d7c2e878b0a1c6b066967e7f6fd.pdf) foi localizado; a obtenção integral falhou (timeout), portanto não é usado como prova de permissão ou capacidade deste firmware. A fonte SDK local e o Package Manager são a evidência aplicável. A [documentação Android de AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) descreve a conexão do serviço e a capacidade `canPerformGestures`; a [documentação ADB](https://developer.android.com/tools/adb) descreve o shell externo. Nenhuma dessas APIs transfere privilégios de shell para um app comum.

As leituras desta sessão incluíram `devices -l`, `getprop`, settings GET, dumpsys accessibility/package/activity, lista de permissões, `id`, ajuda de input e logcat limitado por tags. O filtro de logcat não retornou negativa relevante; ausência de log não prova autorização. Os dados brutos ficam privados no Termux, fora do Git, sem URL/token de pareamento. Não foi certificada identidade binária do APK 1.0.125 com o checkout.

## Correção de software

- Power direto exige concessões reais das duas permissões OEM; não tenta escrita sabidamente sem autorização.
- Sem caminho disponível, a central explica a interação manual e só abre o painel após o motorista escolher **Abrir painel original** no diálogo. Nenhum power pendente é criado nesse caminho. Nenhum estado físico é inferido da abertura.
- HTML recebe HTTP 409 com `accepted=false`, `confirmed=false`, `method=manual`, `manualInteractionRequired=true`. Não abre painel remotamente nem recomenda habilitar acessibilidade bloqueada.
- Aceite direto sem estado normalizado gera HTTP 202/`confirmed=false`, sem retry. O valor bruto de energia não é transformado em ligado/desligado.
- Onde o serviço já está autorizado **e conectado**, preserva-se o caminho existente. Power usa confirmação booleana explícita registrada após comparação com o nó OEM, em vez de inferir sucesso de uma mensagem. Isso não habilita o serviço nem valida fisicamente esse caminho nesta instalação.
- Vidros e persiana não foram alterados. Reverter o commit restaura a implementação anterior; nenhum dado ou configuração de veículo foi migrado.

## Validação e próximo passo

Testes offline cobrem recusa sem executar callbacks, ausência de retry após erro incerto, aceite sem confirmação, confirmação explícita e tratamento HTML inclusive contra backend antigo. CI de PR executa os testes Kotlin, testes JavaScript e assembleDebug. CI não comprova autorização nem efeito HVAC na central. Não instalar/mesclar/publicar antes da revisão dos testes e limitações.

Não há caminho automático permitido demonstrado para o APK instalado com as permissões atuais. O fluxo implementável agora é interação explícita no painel OEM, com estado não confirmado no controle remoto. Uma integração automática futura exige contrato OEM autorizado ou projeto separado de gateway Termux autenticado, com allowlist, identidade da central, exclusão de comandos concorrentes, confirmação atual de imobilidade, nó/posição/estado validados, alvo idempotente, prazo curto e nenhuma repetição cega. Este PR não cria nem ativa esse gateway e não promete sua viabilidade física.

#1576 continua `running`/resultado nulo na fila, apesar da resposta final persistida. Nenhum executor antigo foi visto na árvore de processos; o lock antigo continua preservado e não houve prova suficiente para removê-lo. Não foi reiniciado Code Bridge. Toda execução foi Codex, sem fallback de provider.
