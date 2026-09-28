# Probe de leitura ADB / BYD AppServer

## Descoberta confirmada no framework e APK do veículo

O AppServer instalado (`com.byd.appserver`) declara o provider
`com.byd.car.server.provider.CarServiceProvider` como `exported=true`, sem
`readPermission` ou `permission` no manifesto extraído. A autoridade é
`com.byd.car.server.provider.CarServiceProvider`.

O provider é uma ponte SPI/Binder. A consulta usa:

```text
content://com.byd.car.server.provider.CarServiceProvider/sync_binder
selectionArgs[0] = com.byd.car.driving.IDrivingStateService
```

O cursor traz o Binder em `extras["binder"]`, como `BinderParcelable`. O AIDL
descompilado define o descriptor `com.byd.car.driving.IDrivingStateService` e
`getDrivingState()` como a transação Binder `FIRST_CALL_TRANSACTION` (código 1),
retornando `int`. Não se usa código de transação especulativo: ambos foram
confirmados no `ICarInfoService`/`IDrivingStateService` do APK.

No AppServer, `DrivingStateServiceImpl.getDrivingState()` lê o feature ID
`555745336` por `BYDAutoGearboxDevice.get(...)`. Em caso de exceção, esse
serviço captura o erro e retorna `0`; por isso, o inteiro é exibido como valor
bruto e não deve ser tratado como um estado conhecido sem correlação com o
painel/câmbio. O setter não faz parte deste probe.

Observação sobre bootstrap: o `onCreate()` do provider chama `DiCarService.init()`.
O construtor do `CarInfoServiceImpl` executa sincronizações de modelo/posição do
volante pelo framework BYD (`set`/propriedade de sistema). O getter solicitado
é de leitura, mas a primeira abertura do provider pode inicializar esse serviço
com os efeitos de sincronização que o próprio AppServer contém. Não foi enviada
nenhuma chamada ao provider nesta sessão; essa inicialização ocorrerá quando o
probe for executado no APK.

## Implementação neste app

- `BydDrivingStateReader` verifica package, exportação, permissão declarada,
  Binder vivo e descriptor antes de enviar somente a chamada de leitura.
- `BinderParcelable` replica o nome e formato do wrapper Parcelable do provider
  para que o `Bundle` de extras possa ser desserializado no processo do app.
- A tela de diagnóstico possui o botão **LER ESTADO DO VEÍCULO (API BYD)** e
  mostra o inteiro bruto com a ressalva de que a enumeração não foi mapeada.
- O diagnóstico geral não dispara mais `probeAllControls()` ao executar a
  varredura de reflexão. Isso evitava sondagens de controles desconhecidas
  misturadas com a leitura de diagnóstico.
- Os controles físicos permanecem bloqueados: uma assinatura encontrada não
  libera a execução, e o gerenciador agora exige uma lista vazia de métodos
  validados antes de aceitar qualquer escrita.
- Nenhuma permissão `BYDAUTO_*_SET` é necessária para esta consulta Binder; o
  acesso depende do provider exportado e continua sujeito à política real do
  firmware.

## Acesso por ADB já observado

Com `adb shell content query`, a central permitiu consultar:

```text
content://carsettings/settings
content://com.byd.carStatusProvider/car_status
```

O primeiro retornou 115 pares `key/value` de configurações persistidas. O
segundo retornou 26 registros, incluindo quilometragem de manutenção (`6193`)
e dados de viagem. Esses providers comprovam leitura de dados disponíveis; as
configurações persistidas não equivalem a telemetria instantânea.

## Limitações e próxima validação

1. A implementação Kotlin ainda precisa ser compilada e executada no head unit.
   Este ambiente não tem um Gradle wrapper funcional nem Android SDK instalado.
2. Após instalar o APK, pressionar o botão e guardar logcat/resultado, sem
   atribuir significado ao valor numérico inicialmente.
3. Comparar leituras repetidas com o estado exibido pelo carro e, apenas com o
   veículo parado em local seguro, registrar mudanças de estado natural para
   inferir a enumeração.
4. Para TPMS, clima ou autonomia, repetir a investigação do provider/API e
   selecionar primeiro getters sem efeitos físicos. Não chamar setters,
   broadcasts especulativos ou transações Binder não documentadas.

## Primeiro candidato de controle identificado (ainda bloqueado)

Ao descompilar o APK oficial `com.byd.carsettings`, encontrei a tela
`AmbientBrightness`. O caminho usado pela própria interface é:

```text
leitura: BYDAutoSettingDevice.getIALArea()
leitura: BYDAutoSettingDevice.getIALBrightness(area)
escrita: BYDAutoSettingDevice.setIALBrightness(area, valor, 0)
```

A UI define faixa mínima `0` e máxima `5`. Para a área `3`, normaliza a área
para `1`; para a tela, áreas `1` e `2` têm brilho independente. O listener
recebe `onIALBrightnessChanged(area, brightness)`. É um candidato concreto de
baixo impacto para controlar brilho da iluminação ambiente e, também, é um
getter para validar leitura de estado.

No Package Manager, `android.permission.BYDAUTO_SETTING_GET` e
`android.permission.BYDAUTO_SETTING_SET` são `prot=signature`. O app BYD de
configurações compartilha UID de sistema; o APK do nosso projeto não. Portanto,
o caminho direto via Reflection não deve ser executado nem apresentado como
funcional até que a instalação autorizada conceda essas permissões. O provider
`carsettings` só grava configurações persistidas; a UI observada aplica brilho
chamando diretamente `setIALBrightness`, então atualizar uma linha SQLite não
é prova de alteração física.

## Referências locais da análise

- `byd_analysis/decompiled/appserver/resources/AndroidManifest.xml`
- `byd_analysis/decompiled/appserver/sources/com/byd/car/server/provider/CarServiceProvider.java`
- `byd_analysis/decompiled/appserver/sources/com/byd/spi/ipc/provider/BinderProvider.java`
- `byd_analysis/decompiled/appserver/sources/com/byd/car/driving/IDrivingStateService.java`
- `byd_analysis/decompiled/appserver/sources/com/byd/car/server/driving/DrivingStateServiceImpl.java`
- `byd_analysis/reports/carsettings_dump.txt`
