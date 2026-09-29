# Investigação das câmeras OEM / AVM — BYD Dolphin Plus DiLink 3.0

**Data:** 2026-09-28  
**Estado:** inspeção estática dos APKs/framework e observação ADB em repouso. A captura comparativa com a interface OEM 360 aberta ainda está pendente.  
**Escopo:** leitura apenas. Não foram enviados comandos de escrita Binder, FID/CAN, intents para iniciar a câmera nem comandos para abrir/fechar câmeras.

## Resumo confirmado

- O APK OEM que contém a interface de câmera panorâmica é **`com.byd.bydcamera`**, instalado em `/system/app/BydCamera/BydCamera.apk`. Sua `BydCameraActivity` trata o tipo de câmera 0 como panorama e seleciona `pano_h`, com fallback para `pano_l`. A tela é uma `SurfaceView`.
- Para panorama, o código de `BydCameraActivity` chama **`android.hardware.AVMCamera.open(cameraId)`**, associa a `Surface` do `SurfaceView` por `addPreviewSurface(surface, 0)` e `setPreviewSurface(surface, 0)`, depois chama `startPreview()`.
- O wrapper Java `AVMCamera` chama JNI (`JNIBMMCamera` / `libbmmcamera_jni.so`). A biblioteca nativa usa `IBMMCamServer`/`IBMMCamDevice`, `IGraphicBufferProducer` e `Surface`; strings e símbolos exportados confirmam suporte adicional a `SurfaceTexture`, buffers de preview e `MediaCodec`. O código da tela OEM 360 observado estaticamente escolhe `Surface`, não `TextureView`/`SurfaceTexture` nem o callback de bytes.
- `android.hardware.bydauto.panorama.IBYDAutoPanoService` é uma API Binder de estado/configuração/eventos do panorama. Suas assinaturas são conhecidas pelo framework; métodos `getValue`, `setValue`, `getBuffer`, `setBuffer`, `setMap` e callbacks não descrevem, por si, a entrega contínua de frames. No APK de câmera, o preview segue pela API `AVMCamera`/BMM, sem chamada a `IBYDAutoPanoService` encontrada.
- Em ADB, `bydcameramanager`, `bmmcameraserver` e `android.hardware.bydauto.panorama.IBYDAutoPanoService` aparecem registrados; processos `com.byd.cameramanager`, `bmmcameraserver`, `cameraserver` e `mm-qcamera-daemon` estavam em execução.
- A leitura de `dumpsys media.camera` mostrou a câmera Android padrão fechada e nenhum cliente ativo. Ao mesmo tempo, o log nativo `mm-camera` continuava reportando frames `CAMERA_SENSOR_NAME:pano` com contadores que avançavam 30 por segundo. Isso comprova atividade/frames no subsistema de câmera nativo durante a amostra, mas **não comprova que o APK OEM estivesse renderizando esses frames** nem identifica o cliente BMM que os consumia.

## Evidência estática: APK OEM de visualização

### `com.byd.bydcamera`

| Item | Evidência encontrada |
|---|---|
| APK | `/system/app/BydCamera/BydCamera.apk` |
| Versão / UID | `1.0`, `userId=1000`, shared UID `android.uid.system` |
| Activity | `com.byd.bydcamera.BydCameraActivity`, paisagem |
| View de vídeo | `SurfaceView` / `SurfaceHolder` |
| Receiver | `com.byd.bydcamera.BydCameraReceiver`, exported, exige `android.permission.BYD_CAMERA` |
| Ações declaradas | `android.intent.action.LUANCH_CAMERA` (grafia literal do APK) e `byd.intent.action.IMAGE_ON` |
| Permissões no manifesto | `CAMERA`, `BYDAUTO_PANORAMA_SET`, `BYDAUTO_PANORAMA_GET`, `BYDAUTO_PANORAMA_COMMON`, `BYD_CAMERA` |

O receiver configura o tipo de câmera antes de abrir `BydCameraActivity`. Para o tipo `0`, a Activity procura `pano_h` e, se indisponível, `pano_l`. Outros tipos incluem `rf`, `rvs`, `rear`, `front` e `dms`. Os IDs não são constantes: são extraídos da propriedade `vehicle.config.cam_sort`. A leitura `adb shell getprop vehicle.config.cam_sort` nesta sessão retornou string vazia; por isso os IDs concretos do veículo não foram estabelecidos.

### Caminho de preview no código da Activity

Para tipo 0:

```java
AVMCamera.open(mCameraID)
addPreviewSurface(mSurfaceHolder.getSurface(), 0)
setPreviewSurface(mSurfaceHolder.getSurface(), 0)
startPreview()
```

O ciclo de vida da Activity chama `openCamera()` em `SurfaceHolder.Callback.surfaceCreated()`. No encerramento, remove callbacks, chama `stopPreview()` e `close()`. Os ramos de câmeras comuns usam `Camera.open(id)` para `front`/`rear` ou `NormalCamera.open(id)` para outros IDs.

### API Java `android.hardware.AVMCamera`

O código descompilado do APK declara estas APIs relevantes:

```java
static AVMCamera open(int cameraId)
static AVMCamera open(int cameraId, WatermarkRender render)
boolean startPreview()
boolean stopPreview()
void close()
boolean addPreviewSurface(Surface surface, int index)
boolean setPreviewSurface(Surface surface, int index)
boolean rmPreviewSurface(Surface surface, int index)
boolean addTexture(SurfaceTexture texture, int index)
boolean setTexture(SurfaceTexture texture, int index)
boolean rmTexture(SurfaceTexture texture, int index)
boolean setMediaCodec(MediaCodec codec, int index)
boolean setMediaCodecFps(MediaCodec codec, int fps)
boolean setPreviewSize(int width, int height)
boolean setCameraFps(int fps)
boolean enablePreviewCallback(int index)
boolean disablePreviewCallback(int index)
void setPreviewCallback(IPreviewCallback callback)
void setEventCallback(IEventCallback callback)
```

Callbacks encontrados:

```java
interface IPreviewCallback {
  void onPreview(AVMCamera camera, byte[] data, int w, int h,
                 int colorFormat, int size, int index, long timeMS);
}
interface IEventCallback {
  void onEvent(AVMCamera camera, int type, int arg1, int arg2);
}
```

O bridge JNI também declara callbacks `ByteBuffer` com os mesmos metadados. A classe converte o `ByteBuffer` para `byte[]` antes de chamar seu `IPreviewCallback`. Os métodos nativos correspondentes incluem `nativeAddPreviewSurface(Surface,int)`, `nativeSetPreviewSurface(Surface,int)`, `nativeAddTexture(SurfaceTexture,int)`, `nativeSetMediaCodec(MediaCodec,int)` e `nativeEnablePreviewCallbackWithBuffer(int)`.

Modos de view declarados por `AVMCamera`: `VIEW_DEFAULT=0`, canais 1–4 (valores 1–4), decussação (5), variantes de espelhamento/rotação de decussação (6–7), composições horizontais (8–13), verticais (14–19), horizontais espelhadas (20–25) e verticais espelhadas (26–31). A Activity 360 observada usa índice de preview `0`; não foi encontrado nela um comando para mudar layout.

## Cadeia nativa e serviços

| Componente | Observado / significado confirmado |
|---|---|
| `bmmcameraserver` | Presente em `service list` com descriptor `android.hardware.IBMMCamServer`; processo rodando. O `dumpsys bmmcameraserver` não retornou detalhes úteis nesta build. |
| `libbmmcamera_jni.so` | JNI do wrapper Java: abre câmera, inicia preview, associa Surface/SurfaceTexture, envia frames a callback ou MediaCodec. |
| `libbmmcamera.so` | Contém a implementação cliente nativa `AVMCamera`/`BMMCamera`; símbolos referenciam `IBMMCamServer`, `IBMMCamDevice` e callback de preview/evento. |
| `libbmmcamera_client.so` | Inclui interfaces nativas Binder `IBMMCamServer`, `IBMMCamDevice` e `IBMMCamClient`; strings incluem `open`, `startPreview`, `setPreviewTarget`, `addPreviewTarget`, `rmPreviewTarget` e `obtainFrame`. |
| `libbmmcamera_utils.so` | Incluída na família BMM; não foi disassemblada nesta fase. |
| `mm-qcamera-daemon` | Processo Qualcomm ativo; os logs `mm-camera` citam sensor `pano` e contadores de frame. |
| `media.camera` / `cameraserver` | Serviço Android padrão presente, mas o dump de runtime reportou dispositivo 0 fechado e sem cliente. É uma interface distinta do serviço BMM OEM. |
| `com.byd.cameramanager` | APK `/system/app/BydCameraManager/BydCameraManager.apk`, UID 1000, persistent. A classe `BYDCamMgrService extends IBYDCameraService.Stub` registra `bydcameramanager`. |
| `android.hardware.bydauto.panorama.IBYDAutoPanoService` | Binder de estado/configuração do AVM; encontrado pelo nome no serviço e no `framework.jar`. |

O `BYDCamMgrService` implementa `preOpenCamera(IBYDCameraUser)`, `openCamera(IBYDCameraUser)`, `posCloseCamera(IBYDCameraUser)`, `registerUser`, `unregisterUser`, `onError` e `getCurrentCameraUser`. Na pré-abertura lê do cliente `cameraId`, `packageName` e propriedades como `camera_type`, `top_activity` e `native`; arbitra o uso, mantém usuário atual e um death recipient. Isso revela coordenação/posse de cliente, mas não demonstra que a Activity `BydCameraActivity` chame diretamente esses métodos Java: ela delega abertura ao cliente nativo `AVMCamera`.

## Interface Binder de panorama

Decompilada do `/system/framework/framework.jar` (nome de serviço igual ao descriptor):

```java
byte[] getBuffer(int type)
int getValue(int type)
int registerUser(IBYDAutoPanoListener listener)
int setBuffer(int type, byte[] data)
int setMap(String[] keys, Map map)
int setValue(int type, int value)
int unregisterUser(IBYDAutoPanoListener listener)
```

Transações declaradas no Stub: `getValue=1`, `setValue=2`, `getBuffer=3`, `setBuffer=4`, `setMap=5`, `registerUser=6`, `unregisterUser=7`. O listener declara eventos `onIntValueChanged(int type,int value)`, `onBufferValueChanged(int type,byte[] buffer)`, `onMapValueChanged(int type,Map map)`, `getPackageName()` e `getProperty(String tag)`.

`BYDAutoPanoramaDeviceDi2l` conecta ao serviço e registra listener no construtor. A implementação descompilada mapeia as seguintes operações/IDs. Os valores abaixo são **constantes e chamadas no código do framework**, não leituras executadas nesta investigação:

| Recurso | ID / valores aceitos no framework |
|---|---|
| `PANORAMA_WORK_STATE` | `1329598488` (get) |
| `PANORAMA_OUTPUT_STATE` | `1329598480` (get/set; set valida `1..11`) |
| `PANORAMA_OUTPUT_SIGNAL` | `1329598490` (get) |
| `PANORAMA_ROTATION` | `1329598492` (get/set; `1` horizontal, `2` vertical) |
| `PANORAMA_WORK_MODE` | `1329598484` (get/set; `0`, `1` ou `3`) |
| `PANORAMA_LVDS_STATE` | `1329598498` (get/set; `1..3`) |
| `PANORAMA_OPERATION` | `1840` (set; `1..7`) |
| `PANORAMA_FOCUS` | `1838` (set; `0`/`1`) |
| `PANORAMA_EMERGENCY_BUTTON_STATE` | `862978056` (get) |
| `HAS_ACU` | `862978048` (get) |
| `PANORAMA_ACU_STATE` | `1831` (get) |
| `PANORAMA_CAR_BODY_STATE` | `1329598504` (get) |
| `PANORAMA_RINGHT_CAMERA_SWITCH` | `1086328862` (get, grafia literal `RINGHT` no framework; usado para right/RF switch) |
| Toque/área | `setMap`; key array `{"1"}`, map keys `1_x`, `1_y`, `1_operate`, `1_w`, `1_h`; operações `0..3` |
| Parâmetros combinados | `setMap` com IDs-string `1329598480`, `1329598484`, `1329598492` para saída, modo e rotação |

Constantes de saída `PANORAMA_OUTPUT_*` incluem off=1, front=2, rear=3, left=4, right=5, compose=6, matching=7, front-left=8, front-right=9, rear-left=10 e rear-right=11. Modos de trabalho: panorama=0, full screen=1, widget=3; a classe base também define display modes adicionais (reverse/3D) que não são todos aceitos pelo setter DI2L. Os nomes e códigos representam controle/configuração de exibição/estado; não foram encontrados códigos de frame de vídeo nessa classe.

## `com.byd.auto_camera` e outras distinções

`com.byd.auto_camera` é outro APK (versão `4.0.8_2409041658_2`, UID 10037), requer `CAMERA` e contém uma tela de câmera com `TextureView`/`SurfaceTexture`, orientada a captura/gravador. A decompilação pesquisada não encontrou referências a `IBYDAutoPanoService`, `AVMCamera`, `pano_h`/`pano_l`, `LUANCH_CAMERA` ou `com.byd.bydcamera`. Portanto, com as evidências estáticas atuais, ele não é o proprietário da tela OEM 360.

## Permissões e possibilidade de integração em APK comum

- `com.byd.bydcamera` roda como UID 1000 (`android.uid.system`) e recebe permissões privilegiadas/granted por ser aplicativo OEM. O manifesto declara `BYD_CAMERA` para proteger o receiver e declara permissões de panorama.
- `android.permission.BYDAUTO_PANORAMA_COMMON` aparece no package manager como `dangerous`. GET/SET aparecem granted para os APKs OEM no dump, mas a inspeção deste framework dump não conseguiu determinar a definição/protection level completa de GET/SET nem de `BYD_CAMERA`.
- O APK do projeto `com.byd.carcontrol` está instalado como UID 10178 (app comum), não UID 1000. Seu manifesto atual não solicita `CAMERA`, `BYD_CAMERA` nem permissões `BYDAUTO_PANORAMA_*`; o package manager não as lista entre suas permissões instaladas/concedidas.
- `/system/etc/public.libraries.txt` inclui `libbmmcamera_jni.so` (há comentário OEM sobre BMM camera server). Isso confirma que o nome da biblioteca JNI está publicado pelo sistema. **Não foi testado** se um app comum consegue carregá-la e abrir um ID BMM, nem se o serviço/Binder aceita esse UID, callbacks e destinos Surface.
- O APK comum não herda o UID, a assinatura nem as permissões do `com.byd.bydcamera`. Assim, os métodos/classes identificados são o caminho mínimo estático, mas o acesso prático por `com.byd.carcontrol` continua não comprovado e provavelmente depende de concessão OEM/privilegiada para status Binder, e de autorização do serviço BMM para frames. Não se deve tratar uma leitura feita como `shell` (UID diferente e acesso de diagnóstico) como prova de acesso pelo APK.

## Observação ADB executada

Dispositivo visto: `172.20.227.181:5555`, modelo `BYD_AUTO`, produto `DiLink3.0`. Na captura base, a Activity do projeto estava em primeiro plano; `com.byd.bydcamera` não estava como Activity retomada. `dumpsys media.camera` informou um dispositivo legado `0` fechado, sem cliente. `ps -A` mostrou `mm-qcamera-daemon`, `cameraserver`, `bmmcameraserver` e `com.byd.cameramanager` ativos. Os serviços Binder relevantes foram confirmados pela lista.

No logcat de amostra, mensagens da thread do `mm-camera` avançaram no sensor `pano` com cerca de 30 frames por segundo; há também mensagens repetidas `write_image_buf ... camera_status:2`. A natureza do estado `2` e a relação desses frames com uma Surface/UI não foram determinadas. Não foram vistos logs de `BydCameraActivity`, `AVMCamera` ou `JNIBMMCamera` na tela de primeiro plano.

## O que ainda não está confirmado

1. A comparação dinâmica A/B abrindo e fechando a tela OEM 360 ainda não foi realizada. É necessário identificar o trigger real da interface (atalho OEM, marcha/situação que a exibe ou outro fluxo) sem emitir broadcasts de câmera por ADB.
2. Não foi observado em runtime qual ID (`pano_h` ou `pano_l`) o veículo resolve da propriedade `vehicle.config.cam_sort`; a leitura atual retornou vazio.
3. Não se capturou a Activity/processo BydCamera ativa, status de `bmmcameraserver` com cliente, Surface vinculada ou log de `AVMCamera` durante o preview.
4. Os métodos internos da interface C++ `IBMMCamServer`/`IBMMCamDevice` e o contrato de autorização completo não foram obtidos nesta fase. Strings nativas expõem nomes de operações, mas isso não basta para fixar suas assinaturas Binder.
5. Não foi executada leitura de estado de `IBYDAutoPanoService` nem tentativa de `AVMCamera.open()` por APK comum. Nenhum comando Binder de escrita foi enviado.

## Comandos de inspeção relevantes usados

```sh
adb devices -l
adb shell service list
adb shell ps -A
adb shell dumpsys activity activities
adb shell dumpsys media.camera
adb shell dumpsys package com.byd.bydcamera
adb shell dumpsys package com.byd.cameramanager
adb shell dumpsys package com.byd.auto_camera
adb shell dumpsys package com.byd.carcontrol
adb shell dumpsys package android
adb shell getprop vehicle.config.cam_sort
adb shell logcat -d -v threadtime -t 2500
adb shell pm path com.byd.bydcamera
adb shell cmd package resolve-activity --brief -a android.intent.action.LUANCH_CAMERA
adb pull /system/framework/framework.jar <pasta-local-de-investigacao>
adb pull /system/lib64/libbmmcamera.so <pasta-local-de-investigacao>
adb pull /system/lib64/libbmmcamera_jni.so <pasta-local-de-investigacao>
adb pull /system/lib64/libbmmcamera_client.so <pasta-local-de-investigacao>
jadx -d <pasta> <APK-ou-JAR>
strings <biblioteca-nativa> | grep -iE 'camera|pano|preview|surface|texture|buffer|frame'
```

O comando de resolução da ação não encontrou uma Activity para `LUANCH_CAMERA`, pois é uma ação do **BroadcastReceiver**; o package dump confirma o receiver, mas nenhum broadcast foi enviado. Captura local da amostra inicial: `Downloads/byd-camera-re/runtime/baseline-2026-09-28.txt` (fora do repositório).

## Menor implementação a validar depois da confirmação de acesso

O caminho mínimo de vídeo sugerido pela implementação OEM é um componente Android visível com um `SurfaceView`, resolver de forma autorizada o ID a partir de `vehicle.config.cam_sort`, abrir `AVMCamera`, fornecer a Surface no índice 0, iniciar preview e parar/fechar no ciclo de vida. A alternativa que a própria API oferece é `SurfaceTexture`, callback `byte[]`/`ByteBuffer` ou `MediaCodec`, mas não é o caminho usado pela tela OEM investigada.

Antes de integrar isso ao APK do projeto, o próximo experimento deve ser apenas a comparação OEM manual e a checagem não destrutiva das permissões/UID: registrar Activity/processos/logcat/dumpsys em repouso, pedir ao usuário abrir a câmera 360 pela interface nativa, capturar o mesmo conjunto aberto e depois fechado. Se isso confirmar o cliente BMM, o teste mínimo pelo APK deve ser feito em uma tela diagnóstica isolada, tratar explicitamente falha de permissão/ID/serviço e encerrar a câmera corretamente; sem autorização OEM, não se deve contornar UID, SELinux ou permission checks.
