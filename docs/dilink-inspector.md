# DiLink Inspector

## O que é coletado

O Inspector é uma infraestrutura de observação do próprio processo do aplicativo. Inicie Monitoramento para uma coleta passiva, ou Experimento para registrar ações manuais. Selecione LOW, NORMAL ou DEEP. Em Experimento, o botão `MARCAR AÇÃO` só habilita após a captura inicial; ele grava a descrição e os tempos de relógio de parede, monotônico em milissegundos e monotônico em nanossegundos. O padrão atual de correlação é uma janela de 5 s antes e depois da marcação. `CORRELATION` guarda score heurístico e evidência; `causalityConfirmed` é sempre `false`.

Os eventos são JSONL: cada linha de `events.jsonl` é um objeto independente. `metadata.json` registra a sessão, build/dispositivo, intensidade, destino de armazenamento, coletores e erros conhecidos. Mudanças de snapshots contêm `oldValue` e `newValue`; eventos sem estado comparável mantêm o valor observado em `rawValue`. Na tela Sessões é possível exportar um pacote JSON (metadata + todos os eventos) e um relatório legível pelo menu de compartilhamento. A captura original permanece em Downloads; o arquivo exportado é entregue ao destino escolhido no compartilhamento.

## Armazenamento

No Android 10/API 29 ou posterior, a gravação primária usa MediaStore Downloads (scoped storage, sem permissão ampla de armazenamento). O caminho visível é:

```text
/sdcard/Download/BydDilink/Inspector/sessions/<session-id>/
    events.jsonl
    metadata.json
```

No código, o caminho relativo MediaStore é `Download/BydDilink/Inspector/sessions/<session-id>/`. Ao iniciar, o app cria os dois arquivos e testa a abertura de escrita e append. Se qualquer etapa falhar, remove os registros parciais e grava em:

```text
/data/user/0/com.byd.carcontrol/files/Inspector/sessions/<session-id>/
```

Em versões anteriores à API 29, o Inspector escolhe diretamente esse fallback privado. O estado da tela mostra o destino ativo; `metadata.json` registra `storage.mode`, `storage.location`, `storage.verifiedWritableAtStart` e o último erro. O fallback é exportável pelos botões de compartilhamento e via `run-as` (somente em builds debuggable).

## Comandos ADB

No computador, com ADB conectado à central, liste as sessões públicas:

```sh
adb shell ls -lR /sdcard/Download/BydDilink/Inspector/sessions
```

Verifique os metadados e o começo do JSONL de uma sessão:

```sh
adb shell cat /sdcard/Download/BydDilink/Inspector/sessions/<session-id>/metadata.json
adb shell head -n 20 /sdcard/Download/BydDilink/Inspector/sessions/<session-id>/events.jsonl
```

Copie uma sessão pública para o diretório atual do computador:

```sh
adb pull /sdcard/Download/BydDilink/Inspector/sessions/<session-id> ./<session-id>
```

Confira sessões privadas usando o UID do pacote (disponível quando o APK é debuggable):

```sh
adb shell run-as com.byd.carcontrol ls -lR files/Inspector/sessions
```

Copie o fallback privado sem conceder permissões adicionais ao app:

```sh
adb exec-out run-as com.byd.carcontrol tar -cf - files/Inspector/sessions > InspectorSessions.tar
```

O `tar` contém todas as sessões privadas; extraia-o no computador. Em um APK release não-debuggable, use os botões Compartilhar dentro da tela Sessões.

## Fontes e limites reais

| Fonte | Instrumentação feita pelo Inspector | Limite relevante |
|---|---|---|
| Inventário BYDAuto por reflexão | Tenta carregar, sem inicialização, classes candidatas; registra assinaturas de métodos e nomes/tipos de campos relacionados a luz, sem ler valores estáticos | É inventário estático/runtime do class loader acessível. Não instancia classes nem executa métodos. Classes em APKs não visíveis ou protegidas podem não carregar. |
| BYDAuto light/setting | A cada 2 s lê por `get(int[], Class)` FIDs já usados pelo app: estado de alimentação da luz interna, estado/configuração ligado à porta, indicadores/configurações de disponibilidade e seis luzes de leitura; emite apenas a primeira leitura e mudanças, além de erros por FID | Não cobre automaticamente todos os tipos de luz; alguns FIDs representam disponibilidade/configuração, não estado físico. Os valores são brutos e precisam ser correlacionados com ações manuais; o backend ainda pode negar leituras. Nenhum setter é chamado. |
| Binder/ServiceManager | Consulta a allowlist Java conhecida; inventaria `service list` e `dumpsys -l`, tenta `dumpsys` para serviços relevantes descobertos e registra estados acessíveis em snapshots ajustados por intensidade | Não observa tráfego Binder, chamadas ou parâmetros de outros processos. `service list`/`dumpsys` podem ser negados. Serviço presente não implica permissão para chamar transações. |
| System properties e Settings | Além da allowlist Java, `getprop` e leitura de Settings.System/Secure/Global em snapshots | `getprop`/Settings podem falhar por política. Os valores são lidos como estado, sem escrita. |
| Inventário de sistema | PackageManager coleta pacotes, permissões `BYDAUTO_*`, services/receivers/providers; para APK BYD legível enumera nomes DEX `android.hardware.bydauto.*` sem carregar classes. Tenta `ps -A`, `lshal`, `dmesg`, `id -Z`, `getenforce` e inventário filtrado de `/dev`, `/sys` e `/proc` | Package visibility limita resultados. DEX/APK, processos e interfaces kernel podem ser inacessíveis. Comandos e erros são preservados; a tentativa não eleva UID nem contorna SELinux. |
| logcat | `logcat -b all -v epoch -T 1` em fluxo sem filtro de conteúdo; guarda a linha e tenta decompor tag, PID/TID, prioridade, números e assinatura normalizada | Android comum restringe `READ_LOGS`; pode haver apenas logs deste APK ou nenhum OEM. `all` significa buffers aceitos pelo logcat do dispositivo, não garantia de acesso. |
| Permissões BYDAUTO | Lê permissões declaradas pelo app e resultado de `checkSelfPermission`; tenta obter metadados de proteção | Mede o UID deste APK. Uma permissão privilegiada, signature ou OEM pode constar como negada mesmo que o app OEM tenha acesso. |
| Broadcasts BYD | O receiver do app registra broadcasts recebidos para quatro ações declaradas: cinto, luz, janela e porta; extras primitivos são preservados | Só registra intents entregues a este receiver. Não consegue bisbilhotar broadcasts protegidos, privados ou direcionados a outro pacote. A declaração de uma action não prova que a central a emite. |
| Sensores Android | Inventaria todos os sensores publicados; listener em tipos selecionados, com amostragem LOW/NORMAL/DEEP de 5 s/1 s/0,5 s. Experimentos persistem amostras iguais também | Sensores do Android da central, não sensores automotivos garantidos. Disponibilidade e calibração variam por hardware. |
| logcat | Lê o stream disponível e persiste linhas que casam com termos de iluminação/veículo; linhas filtradas também podem ser correlacionadas temporalmente com marcadores | Em Android comum, `READ_LOGS` é privilegiada. O processo pode receber apenas seus próprios logs ou nenhum log OEM; falhas ficam em erros/fontes da sessão. |
| Aplicativo em foreground | Grava ações marcadas pelo usuário, início/fim, falhas e alterações do próprio fluxo | Não instrumenta outros APKs nem intercepta chamadas de métodos/callbacks em processos OEM. |

A nova leitura de iluminação usa somente FIDs/getters já conhecidos pelo app. O Inspector não implementa captura de HAL arbitrária, CAN, FIDs desconhecidos, buffers de câmera, shared memory ou Binder callbacks OEM. Nenhum método desconhecido é invocado automaticamente. Para adicionar um coletor, implemente `InspectorCollector` e emita `InspectorObservation`; o controller normaliza as observações, gera transições/correlações, persiste JSONL e atualiza os metadados.

## Fontes de código

- `inspector/InspectorCollectors.kt`: interface de coletores e implementações de inventário, permissões, snapshots, leitura de estados de iluminação, sensores e logcat.
- `inspector/InspectorSessionController.kt`: sessão, polling, marcadores, transições e correlações temporais.
- `inspector/InspectorSessionStorage.kt`: MediaStore, teste de escrita, fallback privado, listagem e compartilhamento.
- `inspector/DiLinkInspectorService.kt`: foreground service para continuar a coleta fora da tela.
- `inspector/DiLinkInspectorActivity.kt`: Monitoramento, Experimento e Sessões.
- `BYDCarStateReceiver.kt`: ponte dos broadcasts entregues ao receiver existente.

