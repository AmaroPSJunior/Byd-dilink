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

Na consulta independente `adb shell dumpsys byd_car_service`, o serviço exibiu
`Current Driving State: 3` e um histórico de transições entre os códigos `0`,
`1`, `2` e `3` (a transição mais recente listada foi para `3`). A saída não
documenta o significado dos números. Como ela foi obtida em outro momento que
as respostas `1` do app e não houve captura pareada com timestamp comum, isso
não demonstra divergência entre APIs nem permite mapear os códigos. O próximo
ensaio deve guardar, na mesma janela temporal, timestamp, getter Binder e dump;
qualquer mudança natural observada deve ser anotada junto com o que o painel
mostrava, sem provocar mudança de marcha/movimento.

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

## Magic Manager / Magic Sentry (28/09/2026)

Os pacotes instalados são `cc.omycar.magicmanager` (2.8.08, UID 10088) e
`cc.omycar.magicsentry` (2.8.08, UID 10089); ambos são apps comuns em
`/data/app`, não apps de sistema. Compartilham a mesma assinatura. O Manager
tem `cc.omycar.magiccore.permission.API` (dangerous) concedida; no Sentry ela
está negada. O provider `cc.omycar.magicmanager.Provider` é exportado, mas
exige `android.permission.INTERACT_ACROSS_USERS_FULL`; a concessão de API não
remove essa exigência para um app externo.

O código da tela Car Status no Magic Manager chama
`window.androidFunction.getAllCarStatusData()` e atualiza categorias com
`getACategoryCarStatusData(...)`; a tela repete a leitura a cada 1,5 s. O SDK
MagicCore também declara os getters `getAllCarStatusValue()` e
`getACarStatusValue(...)`. Isso confirma um caminho funcional dentro do
Manager. Não copiamos nem contornamos sua proteção de provider para dar esse
acesso ao UID do nosso app. Na tela do carro, a leitura mostrou estes valores
brutos:

- Carroceria: estado da trava da porta dianteira esquerda `1`; outras linhas
  visíveis de portas/capô/porta-malas `0`; tampa de combustível `-1`.
- Outros estados: direção do fluxo do ar `1`, limpador `1`, conexão do bocal
  de carga `1`, estado MCU `1`, aquecimento dos dois bancos `1` e ventilação
  dos bancos `-1`.
- Iluminação: farol baixo `0`, farol alto `0`, neblina dianteira/traseira `0`,
  e vários campos de setas/dupla indicação `1` ou `2`, conforme os rótulos.
  Alguns campos vieram como `-27` ou `-1` (por exemplo, intensidade de
  iluminação e estados marcados como “alguns modelos”); permanecem sem
  interpretação.

Os números são exatamente os que a UI exibiu; a enumeração ainda precisa ser
validada com o painel. A tela não prova que todos os campos sejam sensores
instantâneos: alguns podem ser eventos condicionais ou indisponíveis neste
modelo.

O `dumpsys sensorservice` registrou o processo do Magic Sentry
(`cc.omycar.magicsentry.i`, UID 10089) usando acelerômetro e giroscópio Bosch
em períodos de 10 ms durante sessões. O acelerômetro padrão `icm42670-accel`
produziu aproximadamente `(0.00, 9.80, 0.00) m/s²` em amostras consecutivas
da central. Isso é leitura inercial do head unit, útil para movimento/
orientação, mas não é velocidade, marcha ou telemetria CAN.

## Controle reversível verificado pela interface do veículo

Com temperatura `24 °C` e ventilador `0` visíveis inicialmente, usamos uma
vez a seta `+` da barra HVAC nativa: o valor passou a `1` e o primeiro segmento
da ventoinha acendeu. Em seguida abrimos o painel HVAC e desligamos a ventoinha;
a tela confirmou `OFF`, nível `0` e temperatura `24 °C`. O ventilador voltou
ao nível inicial. Nenhum ajuste de temperatura, trava, janela, iluminação,
marcha ou movimento foi enviado. A verificação usou a interface OEM do carro,
sem uma escrita Binder especulativa.

## Integração no app deste projeto

O APK agora inclui uma leitura única do sensor Android `TYPE_ACCELEROMETER`,
com timestamp monotônico e remoção do listener após a primeira amostra. O
diagnóstico identifica explicitamente que esse sensor pertence à central e
não deve ser apresentado como velocidade ou marcha. O getter BYD de estado de
condução continua disponível como valor bruto. A tela Car Status do Magic
Manager é, por enquanto, o caminho autorizado confirmado para estados de
portas e iluminação; o provider dela continua protegido para o UID do nosso
app.

## Referências locais da análise

- `byd_analysis/decompiled/appserver/resources/AndroidManifest.xml`
- `byd_analysis/decompiled/appserver/sources/com/byd/car/server/provider/CarServiceProvider.java`
- `byd_analysis/decompiled/appserver/sources/com/byd/spi/ipc/provider/BinderProvider.java`
- `byd_analysis/decompiled/appserver/sources/com/byd/car/driving/IDrivingStateService.java`
- `byd_analysis/decompiled/appserver/sources/com/byd/car/server/driving/DrivingStateServiceImpl.java`
- `byd_analysis/reports/carsettings_dump.txt`
