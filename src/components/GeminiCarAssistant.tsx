import React, { useState } from 'react';
import { Sparkles, Mic, Send, Bot, User, Lightbulb, ShieldAlert, CheckCircle2 } from 'lucide-react';
import { InternalLightState, SeatBeltState, SeatPosition } from '../types';

interface GeminiCarAssistantProps {
  lightsState: InternalLightState;
  seatBelts: Record<SeatPosition, SeatBeltState>;
  onToggleMasterLights: (turnOffAll: boolean) => void;
  onToggleSeatBelt: (position: SeatPosition) => void;
  onLogDiLinkAction: (actionName: string, javaCall: string, intent: string, params: Record<string, any>) => void;
}

interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  time: string;
}

export const GeminiCarAssistant: React.FC<GeminiCarAssistantProps> = ({
  lightsState,
  seatBelts,
  onToggleMasterLights,
  onToggleSeatBelt,
  onLogDiLinkAction
}) => {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: '1',
      sender: 'assistant',
      text: 'Olá! Sou o Assistente Inteligente do seu veículo BYD. Você pode me pedir em português: "Apagar todas as luzes", "Verificar cintos de segurança" ou "Status do carro". Como posso ajudar agora?',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  ]);

  const [inputQuery, setInputQuery] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSendMessage = async (queryText?: string) => {
    const textToSend = queryText || inputQuery;
    if (!textToSend.trim()) return;

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      sender: 'user',
      text: textToSend,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    setMessages((prev) => [...prev, userMsg]);
    if (!queryText) setInputQuery('');
    setLoading(true);

    try {
      // Call server backend API
      const response = await fetch('/api/gemini/assistant', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: textToSend,
          vehicleState: {
            masterLightsOn: lightsState.masterState,
            unbuckledSeatsCount: (Object.values(seatBelts) as SeatBeltState[]).filter((s) => s.isOccupied && !s.isBuckled).length
          }
        })
      });

      const data = await response.json();
      const replyText = data.reply || 'Comando processado pelo sistema de bordo BYD DiLink.';

      // Check if command is to turn off lights
      const lower = textToSend.toLowerCase();
      if (lower.includes('apagar') || lower.includes('desligar') || lower.includes('luz')) {
        onToggleMasterLights(true);
      } else if (lower.includes('ligar luz') || lower.includes('acender')) {
        onToggleMasterLights(false);
      }

      const botMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: 'assistant',
        text: replyText,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };

      setMessages((prev) => [...prev, botMsg]);
    } catch (err) {
      // Local fallback parser
      const lower = textToSend.toLowerCase();
      let reply = 'Comando veicular recebido com sucesso.';

      if (lower.includes('apagar') || lower.includes('desligar luz')) {
        onToggleMasterLights(true);
        reply = '⚡ Entendido! Apagando todas as luzes internas da cabine agora via DiLink CAN-Bus.';
      } else if (lower.includes('cinto') || lower.includes('segurança')) {
        const unbuckled = (Object.values(seatBelts) as SeatBeltState[]).filter((s) => s.isOccupied && !s.isBuckled);
        if (unbuckled.length > 0) {
          reply = `⚠️ Atenção! Encontrei ${unbuckled.length} cinto(s) desatado(s): ${unbuckled.map(s => s.name).join(', ')}.`;
        } else {
          reply = '✓ Todos os passageiros estão com os cintos de segurança devidamente afivelados!';
        }
      }

      const botMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: 'assistant',
        text: reply,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };

      setMessages((prev) => [...prev, botMsg]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-4xl mx-auto py-6 px-4 space-y-6">
      <div className="bg-slate-900/90 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-2xl flex flex-col h-[550px]">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800 mb-4">
          <div className="flex items-center gap-3">
            <div className="p-3 rounded-2xl bg-gradient-to-br from-violet-600 to-fuchsia-600 text-white shadow-lg shadow-violet-900/40">
              <Sparkles className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-bold text-white text-base font-mono uppercase tracking-wide">
                Assistente de Voz BYD AI (Gemini 2.5)
              </h3>
              <p className="text-xs text-slate-400 font-mono">
                Comandos de Voz em Português para Luzes, Cintos e Diagnósticos do Veículo
              </p>
            </div>
          </div>
          <span className="px-3 py-1 rounded-full bg-violet-950 text-violet-300 border border-violet-800 font-mono text-xs font-bold">
            On-Board AI
          </span>
        </div>

        {/* Quick Suggestion Chips */}
        <div className="flex items-center gap-2 overflow-x-auto pb-3 scrollbar-none">
          {[
            '⚡ Apagar todas as luzes internas',
            '🛡️ Verificar status dos cintos',
            '💡 Ligar iluminação de leitura',
            '🚗 Status geral do veículo'
          ].map((chip) => (
            <button
              key={chip}
              onClick={() => handleSendMessage(chip.replace(/^[^\w\s]+/, '').trim())}
              className="px-3.5 py-1.5 rounded-full bg-slate-950 border border-slate-800 text-xs font-mono text-slate-300 hover:border-violet-500 hover:text-white transition-all shrink-0"
            >
              {chip}
            </button>
          ))}
        </div>

        {/* Chat Feed */}
        <div className="flex-1 overflow-y-auto space-y-3 my-2 pr-1 font-sans text-sm scrollbar-thin">
          {messages.map((m) => (
            <div
              key={m.id}
              className={`flex items-start gap-3 ${
                m.sender === 'user' ? 'flex-row-reverse' : ''
              }`}
            >
              <div
                className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 text-xs font-bold ${
                  m.sender === 'user'
                    ? 'bg-cyan-600 text-white'
                    : 'bg-violet-600 text-white'
                }`}
              >
                {m.sender === 'user' ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
              </div>

              <div
                className={`max-w-[80%] p-4 rounded-2xl border font-mono text-xs leading-relaxed ${
                  m.sender === 'user'
                    ? 'bg-cyan-950/80 border-cyan-500/40 text-cyan-100 rounded-tr-none'
                    : 'bg-slate-950 border-slate-800 text-slate-200 rounded-tl-none'
                }`}
              >
                <div className="flex items-center justify-between mb-1 gap-4">
                  <span className="font-bold text-[10px] text-slate-400">
                    {m.sender === 'user' ? 'Você' : 'BYD AI Assistente'}
                  </span>
                  <span className="text-[9px] text-slate-500">{m.time}</span>
                </div>
                <p className="text-sm font-sans">{m.text}</p>
              </div>
            </div>
          ))}

          {loading && (
            <div className="flex items-center gap-2 text-violet-400 text-xs font-mono p-2 animate-pulse">
              <Bot className="w-4 h-4" />
              <span>Processando inteligência veicular Gemini...</span>
            </div>
          )}
        </div>

        {/* Input Bar */}
        <div className="flex items-center gap-2 pt-3 border-t border-slate-800">
          <input
            type="text"
            value={inputQuery}
            onChange={(e) => setInputQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
            placeholder="Ex: 'Apagar luzes internas', 'Como estão os cintos?'"
            className="flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-4 py-3 text-sm text-white focus:outline-none focus:border-violet-500 font-mono"
          />

          <button
            onClick={() => handleSendMessage()}
            disabled={loading}
            className="p-3 rounded-2xl bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white hover:opacity-90 transition-all disabled:opacity-50"
          >
            <Send className="w-5 h-5" />
          </button>
        </div>

      </div>
    </div>
  );
};
