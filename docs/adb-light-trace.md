# Captura de logs OEM de iluminação via ADB

## Por que existe

O Inspector dentro do APK não conseguiu manter uma captura de logcat nesta central: o pacote não tem `READ_LOGS` concedida e a fonte encerrou com código 1. Uma captura iniciada pelo ADB usa o usuário shell do Android e pode ver logs OEM adicionais, dependendo das regras da central.

Durante uma leitura ADB recente apareceram callbacks `onDataEventChanged(eventType, eventValue)` de `LogUtils-AbsBYDAutoSettingListener`, inclusive o tipo bruto `0x2420002D` com valores `1` e `0`. O significado desse tipo ainda é desconhecido; esta captura foi acrescentada para correlacioná-lo com ações manuais em um experimento. Não trate o ID como estado da luz até repetir e validar a correlação.

O script só executa `adb logcat` com uma lista de tags de leitura, salva linhas localmente e copia `events.jsonl`/`metadata.json` da sessão para consulta. Não limpa os buffers do log, não chama setters, não transmite comandos ao carro e não altera arquivos da sessão no carro.

## Passo a passo

1. Conecte o ADB ao carro e confirme `adb devices` mostra `device`.
2. No app BYD DiLink, abra **INSPECTOR → EXPERIMENTO → Iniciar experimento**.
3. No Termux, na raiz deste repositório, rode:

   ```sh
   python3 scripts/adb-light-trace.py
   ```

   Sem argumento, o script seleciona o experimento ativo mais recente. Também aceita um ID explícito ou serial:

   ```sh
   python3 scripts/adb-light-trace.py 20260930_014413_254 --serial 172.20.227.181:5555
   ```

4. Enquanto a captura está rodando, faça o teste parado, uma luz por vez. Marque cada ação no Inspector e anote o que observou no carro.
5. Finalize o experimento no app e pressione `Ctrl+C` no Termux.
6. A saída fica em `~/storage/shared/Download/BydDilink/Inspector/adb-captures/<session-id>/`. Ela contém `adb-logcat.jsonl` e cópias dos dois arquivos da sessão do Inspector.

Cada linha do arquivo ADB é JSON e inclui timestamp em Unix/UTC, tag, PID, texto original e, quando disponível, `eventTypeDecimal`, `eventTypeHex` e `eventValue`. O timestamp permite comparar com `ACTION_MARKER` em `events.jsonl`.

## Como validar

- Procure no JSONL por `eventTypeHex`, especialmente `0x2420002D`, e compare valor/timestamp com `ACTION_MARKER` e observação física.
- Repita ao menos dois ciclos ligado/desligado, variando a ordem e criando um marcador para cada transição.
- Trate correlação temporal como hipótese. Um evento só ganha interpretação de “luz interna ligada/desligada” depois de transições repetíveis e coerentes com a lâmpada observada.
- Se o callback não aparecer, isso significa que esta rota de log não o expôs neste ensaio; não prova que o evento não exista em outra interface.

## Limites do dispositivo atual

- `dumpsys sensorservice` não lista um sensor Android de iluminância (`TYPE_LIGHT`), então o Inspector não tem atualmente um sensor de lux local para usar como verificação óptica indireta.
- A lista de serviços não inclui o `car_service` padrão do Android Automotive; aparece `byd_car_service`, cujo dump observado só mostrou o serviço de estado de condução. A propriedade Android Automotive `CABIN_LIGHTS_STATE` seria interessante em outro equipamento, mas exige `Car.PERMISSION_READ_INTERIOR_LIGHTS`, uma permissão signature/privileged. Não adicionamos essa API ao APK sem serviço disponível e permissão concedida.
- Os seis FIDs de luzes de leitura foram recusados pelo firmware no ensaio anterior. Não tentamos contornar permissões nem enviar chamadas desconhecidas.
- O provider `content://carsettings/settings` foi consultável pelo ADB shell, mas negou acesso ao UID comum do app; seus valores são configuração persistida e não são telemetria física.

## Referências pesquisadas

- [Android Automotive `VehiclePropertyIds`](https://developer.android.com/reference/android/car/VehiclePropertyIds): propriedades de cabine/leitura e permissões necessárias.
- [BYD-dev, referência comunitária de `BYDAutoSettingDevice`](https://github.com/ID-VerNe/BYD-dev/blob/master/docs/modules/17-setting.md): métodos e callbacks documentados; não lista callback de estado de energia da lâmpada interna.
- [BYD Dashboard, projeto comunitário DiLink](https://github.com/rewin0087/byd_dashboard_auto): exemplifica leitura do HAL BYD por app persistente, mas depende de certificado/permissões OEM para operação completa.
