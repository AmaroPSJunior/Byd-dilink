# Auditoria do Inspector e ampliação de observação

## Diagnóstico anterior à implementação (30/09/2026)

A auditoria dos arquivos `app/src/main/java/com/byd/carcontrol/inspector/*`, do receiver e do manifesto encontrou estas fontes instrumentadas:

| Fonte atual | Coleta | Filtros/perdas conhecidas |
|---|---|---|
| Inventário BYDAuto por reflexão | `Class.forName(..., initialize=false)` para uma lista fixa; enumera assinaturas declaradas e alguns nomes/tipos de campo | Classes fixas e visíveis apenas pelo class loader do APK; campos não são lidos e não há enumeração geral de classes. |
| Getters BYDAuto de iluminação | `BydInteriorLightReader.readDetailed()` a cada 2 s para FIDs já usados no app | Não enumera FIDs. Erros do HAL são persistidos. Valores repetidos são suprimidos; um retorno numérico não prova estado físico. |
| Binder/ServiceManager | Reflexão em `ServiceManager.getService()` para nomes fixos a cada 5 s; registra existência, alive e descriptor | Não enumera o servicemanager, não intercepta transações de outros processos nem chama métodos de serviço. |
| System properties | Reflexão em `SystemProperties.get()` para três propriedades fixas a cada 5 s | Não enumera todas as propriedades; hidden API/SELinux podem negar. |
| Permissões | PackageManager e `checkSelfPermission()` para uma lista de permissões BYD, READ_LOGS e CAMERA | Mede somente declarações/UID deste APK; não descobre todas as permissões instaladas nem concede privilégios. |
| SensorManager | Inventário completo dos sensores publicados; listener em tipos selecionados, limitado a aproximadamente 1 amostra/s e emissão só na primeira/diferença | Apenas sensores Android expostos ao app; amostras intermediárias/iguais não são persistidas. |
| logcat | `logcat -v epoch -T 1`, stream contínuo com filtro por termos legíveis de luz/veículo | Linhas numéricas/hex, tags sem termos conhecidos e demais linhas são descartadas antes do armazenamento. `READ_LOGS` é privilegiada; Android pode negar logs OEM. Não selecionava explicitamente os buffers. |
| Broadcasts BYD | Receiver registra quatro ações declaradas no manifesto quando entregues ao app | Não observa broadcasts não entregues ao receiver, protegidos ou destinados a outros pacotes. |
| Contexto do dispositivo | Build, versão do APK, UID e fingerprint | Não é estado do veículo. |
| Marcadores | `currentTimeMillis()` e `elapsedRealtime()` no marcador | Já há precisão de milissegundo e relógio monotônico. |
| Correlação | Associação temporal em −20/+30 s; mantém eventos recentes por 120 s | Não calcula score, repetição entre ações, inversão ON/OFF ou diff final; evento igual pode ser suprimido por estado. Causalidade fica corretamente marcada como não confirmada. |
| Armazenamento/exportação | `events.jsonl` e `metadata.json` via MediaStore Downloads, fallback privado e compartilhamento individual | Não havia resumo JSON/legível, arquivo de diff, nem exportação agrupada. |

### Pontos cegos e limites de plataforma

Antes desta mudança o Inspector não lia `Settings.System/Secure/Global`, não executava `getprop` completo, não guardava logcat bruto, não consultava `dumpsys`/`service list`, não inventariava dinamicamente pacotes/componentes/permissões BYDAUTO, nem registrava inventário de `/dev`, `/sys` e `/proc`. Também não enumerava HAL HIDL via `lshal`, nem monitorava processos. `Context` comum não oferece observação de tráfego Binder de terceiros ou callbacks privados. `READ_LOGS`, dumpsys, `dmesg`, processos e paths do kernel dependem do UID, SELinux e política do firmware; qualquer tentativa é apenas leitura e seu resultado/erro deve ser preservado. O app não tenta root, shell privilegiado, transações Binder desconhecidas ou acesso CAN/UDS.

Há evidência anterior independente no `docs/STATUS.md` e `docs/adb-light-trace.md`: a central negou ao APK getters BYDAUTO de luz; o coletor logcat interno encontrou limite `READ_LOGS`; `adb shell` externo observou callback OEM `onDataEventChanged` com tipo bruto `0x2420002D`, ainda sem mapeamento. Isso orienta a investigação, mas não significa que o APK comum possa capturar o callback.

## Implementação da expansão

As novas tentativas ficam no processo/UID normal do APK. Enumeradores e comandos são read-only. PackageManager enumera somente os pacotes visíveis ao UID (`QUERY_ALL_PACKAGES` foi declarado para Android 11+); APKs BYD legíveis são consultados com `DexFile.entries()` sem carregar classes. Dados estruturais são armazenados como observações brutas, com fonte, UID e estado de acesso. Logcat deixa de filtrar conteúdo; tenta todos os buffers disponíveis por `-b all`, preserva linha, tag, PID/TID, prioridade, números e assinatura normalizada. A interface filtra apenas a visualização e permite abrir o JSON bruto de cada evento. Em experimentos, amostras repetidas de snapshots e sensores também são mantidas.

LOW/NORMAL/DEEP ajusta leituras conhecidas e a frequência de inventários; DEX, `dumpsys activity services` e `dmesg` são tentativas de início/marcador/fim, não polling frequente. Inventários de paths e saídas de comando têm limites documentados em cada observação (`truncated`/`outputTruncated`); o limite é explícito. Valores de campos identificados como voláteis são preservados como bruto, porém excluídos do diff. Erros e `Permission Denied` são resultados da fonte, não são tratados como ausência. `QUERY_ALL_PACKAGES` não concede acesso a tráfego Binder, permissões BYDAUTO, SELinux ou logs protegidos.

A pontuação de correlação é uma heurística local baseada em proximidade, repetição em marcadores distintos, reversão dos valores, vocabulário/identidade BYD e mudança de estado. Ela não estima probabilidade estatística nem confirma causalidade. A documentação específica do dispositivo permanece limitada a achados confirmados nos registros anteriores.
