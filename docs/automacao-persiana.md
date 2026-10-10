# Automação Persiana por acessibilidade

Implementação experimental, não homologada no veículo. Não usa ADB, shell, gestos,
coordenadas, setters HAL ou alteração de permissões. Os controles anteriores permanecem.

O usuário habilita explicitamente o serviço **Automação Persiana** nas configurações
Android. A cada solicitação confirma veículo imóvel, P, freio acionado e área livre.
O serviço lê velocidade, marcha, bloqueio e inicialização usando contexto normal do app.
Qualquer exceção, ausência de SDK/permissão ou estado desconhecido impede a ação.
Velocidade deve ser finita com módulo <=0,5 km/h, marcha 3/P, permit 0 e init 1.
As leituras são repetidas imediatamente antes do clique. Não há substituição por
confirmação humana quando as leituras são indisponíveis, nem falsificação de permissões.

O serviço abre com.byd.carsettings/com.byd.vehiclesettings.sunroof.view.SunRoofActivity.
Exige evento dessa activity, raiz do pacote OEM e exatamente um nó visível, habilitado,
clicável com rótulo Abrir/Fechar e ID sun_shade_open/sun_shade_close do pacote OEM.
Esses IDs vieram da inspeção estática OEM; sua exposição por acessibilidade ainda precisa
ser homologada. Nó ausente/ambíguo ou UI diferente expira em 10 segundos sem clique.
Não existe fallback por coordenadas. O pedido é consumido antes de ACTION_CLICK e não
há retry. A resposta indica clique enviado, nunca movimento físico confirmado.

Há apenas um pedido pendente em memória; não é reexecutado após morte do processo.
Ao retornar manualmente, interromper/desabilitar serviço ou expirar, o pedido é cancelado.
O retorno automático reordena MainActivity existente sem limpar a task. O estado mantido
em memória fica preservado enquanto Android conservar a Activity; não prometemos restaurar
estado não persistido pelo app legado após morte do processo. Se Android impedir retorno,
o usuário volta manualmente. Não coleta texto de outras telas nem registra conteúdo da árvore.

Sem serviço habilitado ou sem leitura permitida, o app informa bloqueio. Configurar
acessibilidade NÃO concede permissões BYDAuto. Não tente conceder privilégios adicionais.

## Validação

`./gradlew testDebugUnitTest assembleDebug --no-daemon`

Testes puros cobrem segurança, rótulo/ID/pacote, nó invisível/desabilitado, ambiguidade,
timeout e consumo único. O workflow build-apk.yml executa testes antes de compilar.
Teste real futuro exige autorização nova, instalação separada e veículo parado em P,
freio acionado e área livre. Verificar cancelamento, retorno, rotação e exposição dos nós
antes de autorizar qualquer comando. Nenhum teste de hardware foi feito nesta implementação.

Reversão: desabilitar o serviço nas configurações; para código, reverter o commit da
branch feature/sunshade-accessibility-safe. Não remover dados do aplicativo.

## Configurações ausentes no DiLink

O build 102 abria a ação Android padrão sem tratar sua ausência. No DiLink observado,
isso causava ActivityNotFoundException. Agora a resolução é verificada antes da abertura;
a rota alternativa é android.intent.action.BYD_ACCESSIBILITY, restrita ao componente
com.byd.carsettings/com.byd.systemsettings.accessibility.AccessibilityMainActivity.
São conferidos componente habilitado/exportado e permissão exigida. Recusa de segurança
ou desaparecimento da activity são tratados sem crash. Sem rota válida, há orientação
para o menu original/suporte e a automação permanece bloqueada.
Abrir configurações não habilita o serviço. A disponibilidade do menu de habilitação
para terceiros ainda precisa de homologação presencial e consentimento específico.
