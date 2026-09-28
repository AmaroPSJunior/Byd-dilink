# Servidor web local de controles BYD

**Responsável pelo projeto:** AmaroPedroJr

## Visão geral

O servidor roda na central Android junto ao código que conversa com os serviços do veículo. O celular do hotspot e outros dispositivos conectados à mesma rede acessam a página via HTTP. O navegador não fala com ECU/HAL: envia pedidos à central e `LocalCarWebService` encaminha para funções Kotlin.

~~~text
Navegador de outro dispositivo
        │ HTTP na LAN + token
        ▼
Central DiLink · LocalCarWebService · porta 8765
        │ APIs Android/OEM
        ▼
BYDAuto / manager HVAC / tela HVAC OEM
~~~

## Iniciar e acessar

1. Conecte a central e os dispositivos ao mesmo hotspot.
2. Na aba **Controles** do APK, toque **Iniciar servidor web**.
3. Use o endereço completo e QR mostrados pelo APK. O IP muda conforme a rede.
4. O navegador salva o token localmente e remove o token da barra de endereço.

O serviço roda como foreground e é iniciado manualmente. A notificação fica ativa enquanto o servidor está ligado. O IP é atualizado periodicamente; o token persiste para manter o pareamento após reinício do serviço. Apagar os dados do app exige novo pareamento.

## Segurança e rede

- Porta TCP `8765`, bind em `0.0.0.0`.
- `/` e `/index.html` servem o HTML sem autenticação; todos os endpoints `/api/*` exigem token.
- O navegador usa `Authorization: Bearer <token>`. O servidor também aceita `?token=` como alternativa local.
- O token tem 24 bytes aleatórios, exibidos como 48 dígitos hexadecimais e comparados com `MessageDigest.isEqual`.
- Não há TLS, CORS aberto ou DNS `.local`. Redes com isolamento entre clientes Wi-Fi podem impedir acesso mesmo no mesmo SSID.
- Abra [openapi.yaml](openapi.yaml) no Swagger Editor. Configure o servidor para `http://<IP-ATUAL-DA-CENTRAL>:8765` e use **Authorize** com Bearer token.

HTTP local não cifra o conteúdo. Use apenas uma rede confiável e não publique URL pareado/token.

## Respostas

Sucesso normalmente retorna HTTP 200 e JSON com `ok: true` ou `message`. Falhas de validação/interlock geralmente retornam 409 com `{"ok":false,"error":"..."}`. Token ausente/inválido retorna 401. Leitura parcial pode incluir erro em um recurso sem invalidar o objeto geral.

`accepted` quer dizer que a chamada foi aceita pela camada local/HAL. `confirmed` indica comparação posterior quando disponível. Aceitação não garante mudança física.

## Recursos

| Recurso | Implementação | Limites conhecidos |
| --- | --- | --- |
| Energia HVAC | Manager OEM; fallback por acessibilidade na tela OEM. | Escrita direta pode exigir permissão de assinatura. |
| Ventilação HVAC | Manager OEM; fallback tenta selecionar nível na barra OEM. | Alvo 1–7; nível zero pode representar desligado. Fallback precisa ensaio. |
| Temperatura HVAC | Manager OEM; fallback usa setas do motorista e confere getter. | 17–33 °C ou teto 27 °C com aquecimento; passo 0,5/1 °C. |
| Persiana | `BydSunshadeControl`. | Velocidade ≤0,5 km/h, P e estado OEM apto. |
| Vidros | `BydWindowControl`. | Velocidade ≤0,5 km/h, P, permit liberado; presets 0/50/100. |
| Luz interna | `BydInteriorLightControl`. | Aceitação do HAL e confirmação da lâmpada são separadas. |
| Cinto/alerta | Leitor e preferências do alerta. | Somente motorista validado. |

## Exemplo com curl

~~~sh
BASE='http://192.168.1.50:8765'
TOKEN='token-copiado-do-qr'

curl -H "Authorization: Bearer $TOKEN" "$BASE/api/state"

curl -X POST "$BASE/api/climate/temperature" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"celsius":22}'
~~~

Substitua IP/token pelos valores atuais. Nunca grave o token real em exemplo, issue, commit ou captura pública.

## Como acrescentar uma rota

1. Adicione schema e resposta em `docs/openapi.yaml`.
2. Adicione rota validada em `LocalCarHttpServer.route`.
3. Encaminhe para função Kotlin reutilizável também pela tela Android.
4. Valide tipo, intervalo, enum e pré-condições no servidor; JavaScript não é confiável.
5. Exponha erros de permissão/interlock; não esconda recusas.
6. Exiba estado de carregamento e não indique sucesso se o backend recusou.
7. Registre leitura, escrita controlada e confirmação real no veículo.

## Validação registrada

Em 28/09/2026, pelo telefone conectado ao hotspot, a página e `/api/state` responderam HTTP 200. Sem token, `/api/state` retornou 401. Um POST de preferências de alerta já salvas retornou 200; um pedido de vidro em 25% foi recusado com 409 antes de operação física. Um comando de temperatura foi recusado pelo firmware com `BYDACQUISITION_SEND_BUFFER`.

Esses ensaios validam rede, autenticação, JSON e despacho, não todos os atuadores. O fallback HVAC da 1.0.76 ainda requer instalação e ensaio parado.
