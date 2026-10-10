# Persiana BYD — comandos ADB observados (10/10/2026)

**Escopo:** tela *Guarda-sol* do aplicativo OEM da central BYD Dolphin Plus DiLink 3.0. Comandos de toque na interface, não comandos de sensor/HAL.

- Pacote: `com.byd.carsettings`
- Activity: `com.byd.vehiclesettings.sunroof.view.SunRoofActivity`
- Resolução inspecionada: `1920x1080`
- Destino ADB Wi-Fi usado no teste: `10.208.220.181:5555`

## Preparar a tela
Com veículo efetivamente parado, câmbio em P, freio de estacionamento acionado e área da persiana livre:

```sh
adb connect 10.208.220.181:5555
adb -s 10.208.220.181:5555 shell am start -n com.byd.carsettings/com.byd.vehiclesettings.sunroof.view.SunRoofActivity
```

**Antes de qualquer toque:** conferir em captura atual ou visualmente que está na tela *Guarda-sol*, com botões corretos. As coordenadas só foram validadas na resolução indicada. Não executar durante condução; não contornar bloqueios OEM.

## Fechar (toque na tela original)
```sh
adb -s 10.208.220.181:5555 shell input tap 1470 297
```
Tarefa 352: toque único na interface. Indicador de 60% a 0% em ~10 s; proprietário confirmou fechamento físico.

## Abrir (toque na tela original)
```sh
adb -s 10.208.220.181:5555 shell input tap 1470 802
```
Tarefa 354: log registra toque único em Abrir e código de saída 0; proprietário relatou abertura física. Código zero indica que o toque ADB foi enviado, não é confirmação por sensor de posição.

## Verificação não invasiva
```sh
adb -s 10.208.220.181:5555 shell screencap -p /sdcard/shade-check.png
adb -s 10.208.220.181:5555 pull /sdcard/shade-check.png
```
A porcentagem exibida é informação da UI OEM; ainda não foi comprovado se representa sensor físico independente. Não reutilizar as coordenadas se a interface, a escala ou a resolução mudarem.
