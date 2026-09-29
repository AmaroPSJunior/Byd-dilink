# DiLink Inspector

## O que é coletado

O Inspector é uma infraestrutura de observação do próprio processo do aplicativo. Inicie Monitoramento para uma coleta passiva, ou Experimento para registrar ações manuais. Em Experimento, `MARCAR AÇÃO` grava a descrição e os tempos de relógio de parede e monotônico. O coletor cria correlações com eventos até 20 s antes e 30 s depois da marcação. `CORRELATION` significa proximidade temporal; o campo `causalityConfirmed` é sempre `false`.

Os eventos são JSONL: cada linha de `events.jsonl` é um objeto independente. `metadata.json` registra a sessão, build/dispositivo, destino de armazenamento, coletores e erros conhecidos. Mudanças de snapshots contêm `oldValue` e `newValue`; eventos sem estado comparável mantêm o valor observado em `rawValue`.

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
| Inventário BYDAuto por reflexão | Tenta carregar, sem inicialização, classes candidatas; registra classes e assinaturas declaradas | É inventário estático/runtime do class loader acessível. Não instancia classes nem executa métodos. Classes em APKs não visíveis ou protegidas podem não carregar. |
| Binder/ServiceManager | A cada 5 s consulta uma allowlist de nomes conhecidos; registra disponibilidade, `isBinderAlive` e descriptor quando o acesso funciona | Não observa tráfego Binder, chamadas ou parâmetros de outros processos. Serviço presente não implica permissão para chamar suas transações. |
| System properties | Tenta ler três nomes previamente conhecidos por reflexão | Hidden API, SELinux e permissões podem bloquear a leitura. Não faz enumeração de propriedades. |
| Permissões BYDAUTO | Lê permissões declaradas pelo app e resultado de `checkSelfPermission`; tenta obter metadados de proteção | Mede o UID deste APK. Uma permissão privilegiada, signature ou OEM pode constar como negada mesmo que o app OEM tenha acesso. |
| Broadcasts BYD | O receiver do app registra broadcasts recebidos para quatro ações declaradas: cinto, luz, janela e porta; extras primitivos são preservados | Só registra intents entregues a este receiver. Não consegue bisbilhotar broadcasts protegidos, privados ou direcionados a outro pacote. A declaração de uma action não prova que a central a emite. |
| Sensores Android | Registra listener para acelerômetro, giroscópio, gravidade, aceleração linear e vetor de rotação se disponíveis; limita a amostra a ~1 Hz por sensor | Sensores do Android da central, não sensores automotivos garantidos. A disponibilidade e calibração variam por hardware. |
| logcat | Tenta acompanhar um conjunto pequeno de tags BYD/veículo com `logcat` | Em Android comum, `READ_LOGS` é privilegiada. O processo pode receber apenas seus próprios logs ou nenhum log OEM; falhas ficam em erros/fontes da sessão. |
| Aplicativo em foreground | Grava ações marcadas pelo usuário, início/fim, falhas e alterações do próprio fluxo | Não instrumenta outros APKs nem intercepta chamadas de métodos/callbacks em processos OEM. |

O Inspector não implementa captura de HAL arbitrária, CAN, FIDs, buffers de câmera, shared memory ou Binder callbacks OEM. Nenhum método desconhecido é invocado automaticamente. Para adicionar um coletor, implemente `InspectorCollector` e emita `InspectorObservation`; o controller normaliza as observações, gera transições/correlações, persiste JSONL e atualiza os metadados.

## Fontes de código

- `inspector/InspectorCollectors.kt`: interface de coletores e implementações de inventário, permissões, snapshots, sensores e logcat.
- `inspector/InspectorSessionController.kt`: sessão, polling, marcadores, transições e correlações temporais.
- `inspector/InspectorSessionStorage.kt`: MediaStore, teste de escrita, fallback privado, listagem e compartilhamento.
- `inspector/DiLinkInspectorService.kt`: foreground service para continuar a coleta fora da tela.
- `inspector/DiLinkInspectorActivity.kt`: Monitoramento, Experimento e Sessões.
- `BYDCarStateReceiver.kt`: ponte dos broadcasts entregues ao receiver existente.

