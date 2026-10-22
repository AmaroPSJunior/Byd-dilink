# BYD DiLink Controller

**Responsável pelo projeto:** [AmaroPedroJr](https://github.com/AmaroPSJunior)

Aplicativo Android para diagnóstico e controle experimental de recursos de um BYD Dolphin Plus com central DiLink. O projeto reúne leitores do framework BYD, controles que passam pelas APIs OEM disponíveis, um painel web local e notas de engenharia reversa feitas durante a análise do firmware e dos aplicativos da central.

## Estado do projeto

O código Android está em `app/`. A versão web é servida pela própria central a partir de `app/src/main/assets/web/index.html`; ela chama o serviço Android que vive no mesmo processo do app, em vez de tentar falar com a ECU/HAL a partir do navegador do celular.

As APIs BYD variam por firmware e algumas gravações exigem permissões de assinatura indisponíveis para um APK comum. Os documentos distinguem leituras observadas, chamadas que o HAL aceitou e operações realmente confirmadas no veículo. Encontrar uma classe ou método não significa que a operação funciona nesta instalação.

## Documentação

- [Estado e continuidade](docs/STATUS.md): objetivo ativo, decisões confirmadas e próxima etapa.
- [Auditoria e ampliação do Inspector](docs/inspector-audit-expansion.md): fontes existentes, pontos cegos, coleta ampliada e limites de acesso.
- [Captura ADB de logs de iluminação](docs/adb-light-trace.md): fonte complementar somente de leitura para correlacionar callbacks OEM com as ações marcadas no Inspector.
- [Histórico de mudanças](CHANGELOG.md): resumo das mudanças entregues.

- [Engenharia reversa DiLink](docs/engenharia-reversa-dilink.md): arquitetura, classes, identificadores, protocolos e nível de validação.
- [Servidor web local](docs/servidor-web-local.md): implantação na rede do hotspot, pareamento e exemplos HTTP.
- [Contrato OpenAPI](docs/openapi.yaml): rotas, parâmetros, autenticação e respostas para Swagger UI/Editor.
- [Investigação ADB e AppServer](docs/adb-readonly-probe.md): análise do provider SPI/Binder, Magic Manager/Sentry e resultados de leitura registrados.

## Compilar

Requisitos: Android SDK compatível com compileSdk 34, Java 17 e Gradle 8.5.

~~~sh
./gradlew assembleDebug --no-daemon
~~~

O APK de depuração é criado em `app/build/outputs/apk/debug/`. O script `gradlew` deste repositório encaminha para uma instalação Gradle; instale Gradle 8.5 e execute `gradle assembleDebug --no-daemon`. O workflow `.github/workflows/build-apk.yml` também configura Gradle 8.5 e compila por GitHub Actions quando executado manualmente ou em `main`/`master`.

## Servidor web

1. Instale o APK na central e mantenha a central e os demais dispositivos no mesmo hotspot.
2. Abra **Controles** e toque **Iniciar servidor web**.
3. Leia o QR code ou abra o endereço com token exibido no app.
4. O serviço escuta na porta `8765`; as rotas estão descritas no OpenAPI.

O endereço IP depende da rede do hotspot e pode mudar. O app atualiza o endereço enquanto o serviço está ativo. O token é de pareamento local; não publique o endereço completo nem o token em logs, capturas ou documentação pública.

## Limites operacionais

- Vidros e persiana verificam velocidade zero, posição P e permissões/estado do HAL antes de enviar o comando.
- A leitura do cinto validada em uso é a do motorista. As demais áreas não são tratadas como leituras confiáveis.
- Ajustes HVAC diretos podem ser recusados pelo firmware com `BYDACQUISITION_SEND_BUFFER`; a versão atual tenta usar a tela HVAC OEM pela acessibilidade e precisa de validação em veículo parado.
- O endpoint de luz interna distingue aceitação do HAL de confirmação física.
- O painel HTTP usa a rede local sem TLS. Use apenas uma rede confiável e mantenha o pareamento protegido.

Consulte os documentos antes de acrescentar métodos de escrita. Não use frames CAN, transações Binder, broadcasts ou FIDs adivinhados como comandos.

## Codex no Termux

As instruções para ferramentas de desenvolvimento estão em `AGENTS.md`. Para imprimir o contexto salvo antes de iniciar uma tarefa, execute `npm run codex:context`. O comando inclui `docs/STATUS.local.md` se você tiver criado esse arquivo a partir do exemplo; ele permanece fora do Git.

Para trabalhar em todo o Byd-dilink, incluindo o Inspector:

```sh
sh ~/Downloads/github/Byd-dilink/start-codex.sh
```

O inicializador usa `gpt-6-luna`, `workspace-write` e `approval_policy = "never"`.
Permite editar o projeto inteiro e os dados em `Download/BydDilink`, incluindo
`Inspector`, sem confirmações de edição. Operações fora das permissões podem
falhar em vez de pedir aprovação. A configuração `.codex/config.toml` também
aplica esses padrões ao iniciar `codex` dentro deste projeto confiável.
Reinicie a sessão para aplicar os novos padrões. O script antigo
`start-codex-inspector.sh` encaminha para o mesmo inicializador.

Para personalizar os caminhos, use `BYD_CODEX_DATA_DIR` e, opcionalmente,
`BYD_INSPECTOR_REPORTS_DIR` ao executar o script.

A leitura de respostas em português via Termux TTS é uma preferência global do
usuário: configure `notify` em `~/.codex/config.toml`, apontando para um script
fora do repositório. O inicializador herda essa configuração sem sobrescrevê-la.
Modelo, permissões e caminhos do Inspector continuam específicos deste projeto.
