# Ampliação das fontes de diagnóstico do Inspector

Atualizado em: 2026-09-30

## O que a aplicação registra agora

O snapshot do `SensorManager` enumera todos os sensores Android anunciados pelo sistema, incluindo fabricante, tipo, modo de relatório, faixa, resolução, consumo informado e se o sensor pode acordar o dispositivo. O fluxo contínuo continua limitado a tipos passivos pertinentes ao experimento: aceleração, giroscópio, gravidade, aceleração linear, rotação, campo magnético, luz ambiente, proximidade, pressão, temperatura ambiente e umidade. As leituras são limitadas a aproximadamente uma amostra por segundo por tipo, e só são gravadas quando o valor muda.

O inventário não prova que há um sensor físico dedicado: alguns itens podem ser virtuais, agregados ou oferecidos por software. Em sessões anteriores do nosso carro, o inventário não indicou sensor `TYPE_LIGHT`; portanto, esse acréscimo pode não produzir lux. Se `TYPE_LIGHT` aparecer no inventário, compare os seus valores durante a troca da luz da cabine, lembrando que ele pode medir luz externa/tela e não o estado elétrico da lâmpada.

O coletor de logcat também passou a esperar o processo encerrar antes de consultar o código de saída, evitando a exceção `IllegalThreadStateException: process hasn't exited`. Isso não contorna `READ_LOGS`: um APK normal pode continuar sem permissão para ler logs de outros processos. A captura ADB iniciada pelo usuário shell continua sendo a fonte adequada para logs OEM quando autorizada pelo aparelho.

## Pesquisa de projetos públicos

- [BYD Dashboard Auto](https://github.com/rewin0087/byd_dashboard_auto) integra o HAL proprietário `android.hardware.bydauto.*`, grava telemetria de viagem e carga e declara que o APK real depende de assinatura OEM/permissões privilegiadas. É uma referência de arquitetura, mas as permissões não são obtidas por adicionar nomes no manifesto.
- [BYDMate](https://github.com/AndyShaman/BYDMate) e [BYDMate own, notas do daemon](https://github.com/scroodge/BYDMate-own/blob/main/docs/REMOTE_COMMAND_DAEMON.md) documentam leituras/paridade de sinais BYD em alguns veículos, inclusive SOC, potência, portas e pneus. As notas dizem que os FIDs vieram do SDK/framework real do carro e que alguns sinais dependem da arquitetura CANFD; não devemos copiar IDs sem confirmação no modelo/firmware deste veículo.
- [byd-dolphin-hacking](https://github.com/wheregoes/byd-dolphin-hacking) reúne pesquisa de head unit, providers, HAL e sinais CAN. É material específico de Dolphin/DiLink e algumas explorações exigem privilégios/hardware diferentes; não prova disponibilidade pelo app Inspector.
- [Zadarboy/byd](https://github.com/Zadarboy/byd) e [hass-byd-vehicle](https://github.com/jkaberg/hass-byd-vehicle) enfocam a API/app conectado da BYD e telemetry via MQTT/Home Assistant, distinta da leitura local de sensores do Android.
- A API padrão [VehiclePropertyIds do Android](https://developer.android.com/reference/android/car/VehiclePropertyIds) inclui propriedades como `CABIN_LIGHTS_STATE` e `READING_LIGHTS_STATE`, mas exige `Car.PERMISSION_READ_INTERIOR_LIGHTS` de assinatura/privilegiada. Nosso carro também não expôs `car_service` padrão nas inspeções anteriores.

## Limite e interpretação

Não existe uma API pública universal que permita a um aplicativo comum enumerar e ler todos os módulos ECU/sensores do carro. `SensorManager` cobre sensores Android expostos ao processo; serviços BYD, propriedades Android Automotive, sysfs, providers e sinais CAN têm controles e formatos próprios. Aumentar o volume de valores sem acesso ou mapeamento validado acrescentaria ruído. O Inspector segue somente leitura e não envia comandos CAN/UDS nem chama setters OEM.

Para cada sessão, primeiro confira `ANDROID_SENSOR_INVENTORY` e o status das fontes. `STATE_CHANGED` significa mudança observada na fonte; ausência de evento pode significar sensor inexistente, valor constante, fonte negada ou estado não exportado pelo firmware. Compare uma ação marcada com a janela de leituras e os logs ADB do mesmo instante.
