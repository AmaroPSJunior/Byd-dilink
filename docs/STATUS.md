# Estado do projeto

Atualizado em: 2026-09-30

## Objetivo ativo

Ampliar o Inspector como ferramenta read-only de investigação de estados DiLink, com foco em correlacionar a iluminação interna.

## Estado atual

- Repositório BYD DiLink com app Android em `app/` e interface web em `src/`.
- O diretório `Inspector` é externo ao repositório e é aberto pelo inicializador `start-codex.sh` no Termux.
- A versão e o estado da instalação do Inspector ainda precisam ser confirmados na próxima tarefa dedicada à atualização.

## Decisões

- Usar este arquivo como resumo técnico compartilhável e `CHANGELOG.md` para mudanças concluídas.
- Manter anotações pessoais/transcrições em `docs/STATUS.local.md`, arquivo local ignorado pelo Git.
- Instruções permanentes para ferramentas de desenvolvimento ficam em `AGENTS.md`.

## Ensaio de luz interna (2026-09-30)

- Sessão Inspector `20260930_014413_254` (experimento, 01:44:13–01:45:39). Marcadores: `ligar luz 001` às 01:44:46 e `apagar luz 001` às 01:45:23.
- `setting.inside_light_power_state` (FID `0x42E0002D`) teve leitura inicial bruta `0`; não houve `STATE_CHANGED` desse FID nem de outro campo de iluminação durante a sessão. Assim, este ensaio não detectou uma mudança correspondente ao acendimento/apagamento observado no carro.
- Os seis getters de luz de leitura retornaram `-2147482648`; o log OEM informou falta de permissão para os FIDs `0x3FE0000A`, `0x3FE0000C`, `0x3FE0000E`, `0x3FE00010`, `0x3FE00016` e `0x3FE00018`.
- O FID central foi lido como `0` e não gerou erro no Inspector, mas o significado físico desse valor ainda não está mapeado. Repetir com marcadores separados para cada ação, anotar visualmente cada estado e comparar leituras antes/depois.
- Nova fonte investigada: `adb logcat` do usuário shell exibiu callbacks OEM `onDataEventChanged`; apareceu o tipo bruto `0x2420002D` com valores `1` e `0`, ainda sem mapeamento. Foi adicionado `scripts/adb-light-trace.py` para capturar esses logs em paralelo e preservar cópia local da sessão.

## Monitoramento durante trajeto (2026-09-30)

- Sessão Inspector `20260930_020911_856`, modo monitoramento, 02:09:11–02:43:58 (34 min 47 s). Foram gravadas 8.592 linhas, principalmente amostras dos sensores de movimento.
- O FID `0x42E0002D` (`setting.inside_light_power_state`) só gerou a leitura inicial `0`; não houve `STATE_CHANGED` de iluminação nem broadcasts de veículo na sessão. Os seis FIDs de leitura continuaram retornando `-2147482648`.
- O coletor interno de logcat foi limitado por `IllegalThreadStateException: process hasn't exited` associado a `READ_LOGS`; assim, não capturou logs OEM durante o trajeto.
- O buffer ADB analisado depois reteve somente 4 s (02:45:27–02:45:31), após o fim da sessão; nele apareceu `0x2420002D=1` às 02:45:30, sem relação temporal com a sessão. O ID continua sem mapeamento.

## Implementação concluída; verificação pendente (2026-09-30)

- Auditoria prévia registrada em `docs/inspector-audit-expansion.md`; o Inspector anterior filtrava logcat por palavras, consultava allowlists de Binder/propriedades e não coletava Settings, dumpsys, inventários de serviços/componentes ou diff final.
- Implementada expansão read-only em código: logcat `-b all` sem filtro textual; inventário de Settings, getprop, ServiceManager/dumpsys/lshal, pacotes/componentes/permissões BYDAUTO, processos e caminhos relevantes `/dev`, `/sys`, `/proc`; tentativa de dmesg com registro de bloqueios.
- Experimento agora oferece intensidades LOW/NORMAL/DEEP, snapshots no início/marcadores/fim, janela temporal de ±5 s, diff de estado, score heurístico e exportação JSON/relatório legível.
- Não foi possível compilar nem executar testes neste ambiente: `gradle` não está instalado; `./gradlew` apenas encaminha para esse binário. O SDK Android também não está configurado. Existe teste JUnit do score, ainda não executado. Nada foi instalado. Os resultados das fontes dependem do UID e das políticas SELinux/Package Visibility da central.

## Próxima etapa

Executar build e testes solicitados. Depois instalar e realizar ensaio parado de luz interna com marcadores ON/OFF repetidos; exportar pacote JSON e relatório junto de `adb-light-trace.py` executado em paralelo. Verificar se `0x2420002D` acompanha as ações sem atribuir significado ao ID antes de evidência repetida.

## Referências

- [Inspector e fluxo de dados](dilink-inspector.md)
- [Servidor web local](servidor-web-local.md)
- [Engenharia reversa DiLink](engenharia-reversa-dilink.md)

## Ampliação de logs do Inspector (2026-09-30)

- O snapshot Android agora enumera todos os sensores publicados por `SensorManager`, com metadados técnicos; fluxo ao vivo adiciona campo magnético, luz, proximidade e sensores ambientais à allowlist existente, amostrados no máximo aproximadamente a 1 Hz.
- Corrigido o caminho que consultava imediatamente o exit code do processo logcat. A captura interna ainda depende da permissão `READ_LOGS`; a correção não remove a restrição do Android.
- Pesquisa pública registrada em `docs/inspector-sensor-expansion.md`. Projetos BYD com telemetria ampla usam HAL/FIDs específicos, framework do veículo e/ou assinatura privilegiada; IDs não validados não foram incorporados.
- Mudança de código ainda não compilada nem instalada. Na próxima sessão no carro, conferir `ANDROID_SENSOR_INVENTORY`, estados das fontes e se `TYPE_LIGHT` está disponível; comparar marcadores de luz com fluxo de sensor e captura ADB externa.
- Tentativa de compilar/instalar (2026-09-30): ADB está conectado ao DiLink (`172.20.227.181:5555`), mas não foi possível gerar o APK porque este ambiente não tem Android SDK instalado/configurado (`ANDROID_HOME`/`sdk.dir` ausente; nenhuma cópia de `android.jar` encontrada). APKs antigos existentes não incluem com segurança a mudança de sensores e não foram instalados.
- Conclusão da instalação (2026-09-30): GitHub Actions `36675900062` compilou o APK; instalado no DiLink após remover o pacote anterior porque as assinaturas divergiam. Antes da troca, foram copiados para o Termux o APK anterior, 7,3 MB de sessões (10 diretórios) e dados privados; após instalar, confirmei os 10 diretórios no carro e comparei SHA-256 do APK instalado com o artefato (`06a081e804b75b85826e8bb260848217be9e0cf31431f75d0469c91af768a6dc`). O `versionName` interno continua `1.0.0`, apesar de o artefato do workflow chamar-se `v1.0.83`.
