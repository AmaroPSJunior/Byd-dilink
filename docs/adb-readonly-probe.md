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
com os efeitos de sincronização que o próprio AppServer contém. Na execução
real do probe, a primeira consulta iniciou `com.byd.appserver:CarService`; essa
sincronização de inicialização ocorreu como comportamento interno do AppServer.
Não foi enviado nenhum setter pelo probe.

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

## Resultado de execução real (28/09/2026)

O APK foi instalado e executado na central `BYD_AUTO` / `DiLink3.0` por ADB.
O botão de leitura recebeu duas respostas reais:

```text
getDrivingState() = 1
getDrivingState() = 1
```

Isso confirma que o provider exportado entregou um Binder utilizável e que a
transação AIDL de leitura respondeu no processo do aplicativo. O valor segue
sem interpretação: a enumeração não foi identificada, e esse método lê o
feature `GEARBOX_AUTO_MODE_TYPE`, que não deve ser confundido com posição atual
da alavanca sem validação adicional. Não se deve inferir que o veículo está
estacionado a partir desse valor.

O Package Manager negou ao APK as permissões BYD de leitura/escrita solicitadas
(`BYDAUTO_*_GET/SET`, incluindo `BYDAUTO_SETTING_GET/SET`) e permissões de
controle de carroceria/luzes. Portanto, a resposta acima veio especificamente
da ponte Binder exportada, não de permissões concedidas ao aplicativo. Nenhuma
alteração física ou setter foi executado.

Uma nova leitura somente de dados persistidos em
`content://com.byd.carStatusProvider/car_status` retornou 26 pares. Entre eles,
`car_status_maintenance_mile=6193` e `set_car_status_maintenance_mile=20000`;
isso é informação de manutenção persistida, não odômetro ou telemetria ao vivo.
As séries `travel_points_*` também são dados históricos agregados e não foram
interpretadas como posição ou velocidade atuais.

## Limitações e próxima validação

1. A compilação remota do APK e a execução no head unit já foram concluídas.
2. Próximo passo técnico: inventariar as interfaces realmente expostas pelo
   provider e seus AIDLs, procurando getters documentados de TPMS, clima,
   autonomia e estado de portas. Validar um método de cada vez com checagem de
   descriptor/transação e registrar valor bruto, horário e repetibilidade.
3. Para interpretar `getDrivingState()`, comparar leituras com a indicação do
   painel durante estados naturais observados, sem provocar troca de marcha nem
   movimento do veículo. O valor `1` sozinho permanece desconhecido.
4. Não chamar setters, broadcasts especulativos ou transações Binder não
   documentadas. As permissões `signature` negadas não devem ser contornadas;
   uma escrita só poderá ser considerada após caminho oficial autorizado,
   validação do método e uma decisão explícita e segura sobre o efeito físico.

## Inventário estático confirmado do AppServer

O APK ativo foi copiado da central e descompilado localmente para inspeção. O
`DiCarService.registerService()` registra exatamente estes cinco contratos SPI
no provider genérico `sync_binder`:

| Interface AIDL | Métodos observados | Observação de segurança |
| --- | --- | --- |
| `com.byd.car.driving.IDrivingStateService` | `getDrivingState()`, listeners | getter validado em execução; registro de listener não foi chamado |
| `com.byd.car.carinfo.ICarInfoService` | `getDriverSeat()`, `getVehicleType(int)`, `getCarType(int)`, `getCarBodyConfig()`, `getVehicleVin()` | `getVehicleVin()` lê identificador sensível e não foi chamado; construtor sincroniza dados de modelo/volante |
| `com.byd.car.adas.ICarAdasService` | `getAdasVendor()` | propriedade de plataforma/configuração, não estado ADAS ao vivo |
| `com.byd.car.locale.ICarLocaleService` | `getCountryDomain()`, `getCountryCode()` | getters de configuração regional; o construtor sincroniza uma propriedade de sistema |
| `com.byd.car.collect2.ICollect2FileStoreService` | leitura e escrita de arquivos/diretórios | contém operações destrutivas de arquivo; nenhuma foi chamada |

Esse provider do AppServer não expõe diretamente getters identificáveis de
TPMS, temperatura do habitáculo, autonomia, portas ou janelas. Para esses dados,
o caminho provável continua sendo o SDK `BYDAuto*` sob permissões `signature`
ou serviços OEM específicos. A inspeção confirmou o limite do provider; ela
não prova que esses recursos inexistam em outras partes do firmware. O próximo
passo deve localizar contratos de leitura OEM documentados e avaliar as
permissões/exportação antes de invocar qualquer um. O ADB shell conectado não
concede automaticamente ao APK UID de sistema nem as permissões privilegiadas.

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
