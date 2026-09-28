# Engenharia reversa do BYD DiLink

**Responsável pelo projeto:** AmaroPedroJr  
**Veículo observado:** BYD Dolphin Plus, central DiLink 3.0, Android 10 / API 29  
**Registro consolidado:** 28 de setembro de 2026

## 1. Objetivo e método

O trabalho mapeia caminhos de leitura e controle presentes no firmware e separa três níveis de evidência:

1. **Descoberta estática:** classe, interface, constante ou recurso encontrado em APK, framework ou manifesto.
2. **Chamada executada:** o app chamou o método e obteve retorno.
3. **Efeito confirmado:** uma leitura posterior ou a tela OEM mostrou o estado solicitado no veículo.

Somente o terceiro nível confirma controle físico. Um retorno 0, true ou “comando enviado” pode significar apenas que uma camada aceitou a solicitação. Para cada recurso, registre pacote, classe, assinatura do método, FID/área, permissões, estado anterior, retorno, estado posterior e comportamento da tela OEM.

O procedimento empregado foi inspecionar pacotes e providers instalados, consultar manifestos e árvores de acessibilidade, usar ADB para observação e chamadas de leitura, comparar APIs com a tela OEM e confirmar resultados sem enviar comandos de protocolo especulativos. O ADB conectado à central não transforma o UID do APK em UID de sistema: shell e app têm permissões diferentes.

## 2. Componentes do repositório

| Componente | Papel |
| --- | --- |
| `MainActivity.kt` | Diagnóstico, controles Android, estado das leituras, QR code e inicialização explícita do servidor local. |
| `discovery/` e `*Inspector.kt` | Inventário/reflexão e relatórios sobre classes, transportes e recursos encontrados. Descoberta não equivale a escrita autorizada. |
| `BydDrivingStateReader.kt` | Leitura do estado bruto pelo provider SPI/Binder do AppServer. |
| `BydVehicleSpeedReader.kt` | Leitura do FID de velocidade usado pelas verificações dos controles. |
| `BydWindowControl.kt` | Leitura e presets de vidro, com verificações de velocidade, marcha e permit do HAL. |
| `BydSunshadeControl.kt` | Posição/comandos da persiana com verificações de movimento, P, inicialização e bloqueio. |
| `BydClimateAdjustment.kt` | Snapshot HVAC e chamadas do manager OEM para energia, ventilação e temperatura. |
| `ClimateAccessibilityService.kt` | Fallback pela tela HVAC OEM com IDs de view e confirmação por leitura. |
| `BydInteriorLightControl.kt` / `BydInteriorLightReader.kt` | Escrita candidata e leituras brutas da iluminação interna. Aceitação do HAL e confirmação física são campos diferentes. |
| `BydSeatbeltReader.kt` | Estados brutos e normalização do cinto cuja área foi validada. |
| `SeatbeltVoiceAnnouncer.kt` | Aviso sonoro local usando recursos de áudio do veículo. |
| `LocalCarWebService.kt` | Serviço foreground HTTP, pareamento, estado agregado e despacho de comandos web. |
| `app/src/main/assets/web/index.html` | Painel responsivo servido pela central; seus botões chamam a API real do serviço. |
| `docs/openapi.yaml` | Contrato legível por Swagger UI/Editor. |

O app também mantém transportes genéricos para diagnosticar Settings, HAL reflection, Binder, intents, SPI, CAN, sockets, JNI e providers. A presença de um transporte na matriz de diagnóstico não significa que exista um comando implementado ou autorizado por ele. O modo exploratório permanece separado dos controles específicos abaixo.

## 3. AppServer, SPI e Binder

O pacote `com.byd.appserver` expõe o provider `content://com.byd.car.server.provider.CarServiceProvider/sync_binder`. O provider devolve no cursor um Binder serializado como `BinderParcelable`, com a interface solicitada em `selectionArgs[0]`.

Para `com.byd.car.driving.IDrivingStateService` foram confirmados:

- descriptor igual ao nome da interface;
- `getDrivingState()` na transação `IBinder.FIRST_CALL_TRANSACTION`;
- retorno inteiro lido por `Parcel.readInt()`;
- chamada real executada pelo app em múltiplas leituras.

O valor dessa interface deve permanecer **bruto**. Leituras históricas retornaram `1`, enquanto outro dump de serviço mostrou estado diferente. A enumeração não foi identificada; não se deve afirmar que `1` significa P. O bootstrap do provider também pode sincronizar dados internos, portanto a análise não deve ser descrita como isenta de qualquer efeito colateral de inicialização.

O relatório detalhado de AppServer, ContentProviders, Magic Manager e Magic Sentry está em [adb-readonly-probe.md](adb-readonly-probe.md). O provider do Magic Manager exige `INTERACT_ACROSS_USERS_FULL`; possuir a permissão MagicCore `API` não remove esse bloqueio. Os estados mostrados pela interface dele não provam que um app externo tenha o mesmo acesso.

## 4. Classes BYDAuto e controles específicos

### 4.1 Velocidade

O leitor usa `android.hardware.bydauto.speed.BYDAutoSpeedDevice`, obtido por `getInstance(Context)`, e `get(int[], Class)` com feature `0x94400008` (`SPEED_AUTO_SPEED`). O valor é convertido para km/h. A central é a fonte; o acelerômetro Android não deve ser usado como substituto de velocidade.

### 4.2 Vidros

Classe: `android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice`.

| Área | Vidro | Feature identificado para setter |
| ---: | --- | ---: |
| 1 | Motorista dianteiro | `0x4C119010` |
| 2 | Passageiro dianteiro | `0x4C119020` |
| 3 | Motorista traseiro | `0x4C119018` |
| 4 | Passageiro traseiro | `0x4C119028` |

Leituras consultadas: `getWindowOpenPercent(area)`, `getWindowState(area)` e `getWindowPermitState()`. A UI web aceita alvo 0, 50 ou 100. Isso corresponde aos comandos OEM `WINDOW_CLOSE=2`, `WINDOW_OPEN_FULL=1` e `WINDOW_OPEN_HALF=4`. Não foi confirmado posicionamento arbitrário. O método genérico `set(int[], BYDAutoEventValue)` e FIDs foram identificados, mas não substituem os presets.

Antes da escrita, a implementação exige velocidade disponível com `abs(speed) <= 0.5`, marcha `3` (P conforme inspeção do controle OEM) e `permit != 1`. A rota web chama o mesmo `BydWindowControl` para não duplicar controle sem validação.

### 4.3 Persiana panorâmica

Usa `BYDAutoBodyworkDevice`, área `6` e `getWindowOpenPercent(6)` para leitura. O caminho OEM observado usa `setSunshadeState(percent)`; a tela da central confirmou sentinel de parada `254` e limpeza do alvo `255` após 200 ms.

A operação verifica velocidade zero, marcha P, `getWindowPermitState()` e `getWindoblindInitState() == 1`. `permit == 1` ou persiana ainda não inicializada bloqueiam a escrita. Retorno zero do HAL não confirma que a persiana chegou ao alvo: a leitura posterior é comparada e `accepted`/`confirmed` são reportados separadamente.

### 4.4 Ar-condicionado

A leitura e controle diretos usam o serviço `airconditioning` e o manager OEM. Métodos observados:

- `getWindLevel()` e `processWindLevelButtonClicked(int)`;
- `getAutoAcTemperatureUnit()`, `getMainTemperatureValue()` e `processMainTemperatureChanged(String)`;
- `hasAcHeatingFeature()`;
- `getAcPowerButtonState()` e `processAcPowerButtonClicked(boolean)`.

`BydClimateAdjustment` converte Fahrenheit quando necessário, limita a temperatura a 17–33 °C (ou 17–27 °C se há aquecimento) e usa passo OEM de 0,5 °C ou 1 °C. Os getters foram lidos no carro.

Um POST de temperatura recente foi recusado pelo firmware com `Neither user 10178 nor current process has android.permission.BYDACQUISITION_SEND_BUFFER`. Essa permissão é diferente do precheck local `BYDAUTO_*` simulado por `BydAutoReadContext`: esse wrapper não concede a permissão real do backend. A versão 1.0.76 tenta encaminhar os comandos de energia, temperatura e ventilação pela tela HVAC OEM com AccessibilityService e verificar a leitura posterior. Esse fallback ainda precisa de ensaio físico parado; não é considerado validado.

IDs vistos em `com.byd.airconditioning`: `front_ac_power_id`, `main_arrow_plus_img`, `main_arrow_minus_img`, `wind_level_id`, `wind_min_id` e `wind_max_id`. Os IDs e posições dependem da versão da tela/firmware.

### 4.5 Iluminação interna

O comando identificado usa `android.hardware.bydauto.setting.BYDAutoSettingDevice` e `turnOffInsideLight(int)`, com `INSIGHT_LIGHT_ON` ou `INSIGHT_LIGHT_OFF~. Apesar do nome do método, o SDK valida ambos os estados e encaminha `SET_INSIDE_LIGHT_STATE_SET`. Não confundir com `setInsideLightDoorState`, que altera o comportamento ligado à porta.

O FID observado para leitura do estado da ação é `0x42E0002D`. Também são consultados estado de porta e FIDs de luzes de leitura. O app informa aceitação do HAL, valor bruto observado e leituras relacionadas; isso não prova que a lâmpada física do teto mudou em toda execução.

### 4.6 Cintos

Classe: `android.hardware.bydauto.safetybelt.BYDAutoSafetyBeltDevice`. Getter: `getSafetyBeltStatus(area)`. Valores de lock/unlock/invalid vêm dos campos da própria classe. A área `SAFETY_BELT_AREA_MAIN` foi correlacionada com o cinto do motorista. Códigos das outras áreas variaram sem mudança física correspondente e não foram aceitos como leituras confiáveis.

O serviço web consulta o motorista periodicamente e pode reproduzir aviso conforme preferência e limiar de velocidade (padrão 10 km/h). Ocupação por peso dos outros bancos não está validada; não se deve anunciar passageiro baseado nos valores não confirmados.

## 5. Permissões e fronteiras de execução

O APK declara permissões `BYDAUTO_*_GET/SET`, permissões BYD e MagicCore. No Package Manager, permissões importantes de escrita não foram concedidas ou são protegidas por assinatura. Declarar permissão no manifesto não a concede.

`BydAutoReadContext` intercepta apenas prechecks locais com prefixo `android.permission.BYDAUTO_` de alguns clientes SDK. Não muda UID, não engana o sistema sobre permissões de outros processos e não autoriza comandos bloqueados pelo serviço remoto do carro. Não amplie essa exceção para `BYDACQUISITION_SEND_BUFFER`.

Trate `SecurityException`, retorno do HAL, estado lido e confirmação física como resultados distintos. Não contorne permissões `signature`, provider protegido do Magic Manager, SELinux, interlocks da central ou verificações de janela/teto.

## 6. Como adicionar um recurso novo

1. Identifique pacote OEM, classe/serviço, método público e manifesto.
2. Compare com a tela OEM que já controla o recurso.
3. Registre permissões declaradas, concedidas e exigidas pelo serviço remoto.
4. Implemente primeiro o getter e capture valor bruto, unidade, área e intervalo.
5. Localize interlocks OEM de velocidade, marcha, inicialização, permit e ignição.
6. Escreva somente por método documentado/observado da interface OEM; não adivinhe transações, broadcasts, FIDs, enums ou frames CAN.
7. Faça leitura antes, chamada controlada, leitura depois e inspeção OEM; reporte aceite e confirmado em separado.
8. Adicione função Kotlin reutilizável pela UI Android e pelo HTTP. Não replique lógica veicular em JavaScript.
9. Registre versão do firmware/app, condições, retorno literal e nível de evidência.
10. Atualize `docs/openapi.yaml` se a API web mudar.

## 7. Matriz de evidência

| Recurso/caminho | Confirmado | Ainda não confirmado |
| --- | --- | --- |
| AppServer `getDrivingState()` | Provider, Binder, descriptor, transação de leitura e retorno bruto real. | Enumeração e equivalência com marcha atual. |
| Velocidade | FID `0x94400008` usado nas leituras e interlocks. | Calibração fora dos estados observados. |
| HVAC getters | Energia, nível e temperatura foram lidos. | Escrita direta de temperatura negada por permissão; fallback da versão 1.0.76 precisa ensaio. |
| Vidros | Áreas, getters, presets e verificações de velocidade/P/permit estão no código. | Escrita física atual de cada janela após últimas versões. |
| Persiana | Área, posição, stop/reset e verificações OEM documentados. | Novo ensaio físico após últimas versões web. |
| Luz interna | Método e FID correlacionado; resposta separa aceite/observação. | Confirmação física repetível de cada luz do teto. |
| Cinto motorista | Getter correlacionado e aviso por voz. | Peso/presença e estados confiáveis dos outros bancos. |
| Web | Página, API LAN, token, leitura de estado e salvamento de alerta testados. | Escritas HVAC pela acessibilidade da 1.0.76 e atuadores após sua instalação. |

## 8. Histórico resumido

Foram acrescentados leitores de cinto, velocidade, estado de condução, persiana, vidros, clima e iluminação; controles de vidro com presets e botão aplicar; abrir/fechar/parar e posição da persiana; ajustes HVAC e unidade/resolução; tratamento de recusa HVAC sem abrir configurações genéricas; retorno detalhado da iluminação; alerta de cinto com voz, liga/desliga e limiar; servidor HTTP local, QR, token, página web e leitura de estado; preservação dos sliders durante atualização periódica; tentativa de fallback HVAC via acessibilidade.

O histórico detalhado permanece no Git e em [adb-readonly-probe.md](adb-readonly-probe.md). Este documento sintetiza resultados sem apresentar hipóteses como capacidades garantidas.

