import React, { useState } from 'react';
import { Code2, Terminal, Play, Trash2, Copy, Check, Cpu, ShieldCheck, Zap } from 'lucide-react';
import { DiLinkLogEntry } from '../types';

interface DiLinkSdkInspectorProps {
  logs: DiLinkLogEntry[];
  onClearLogs: () => void;
  onSimulateIntent: (actionName: string) => void;
}

export const DiLinkSdkInspector: React.FC<DiLinkSdkInspectorProps> = ({
  logs,
  onClearLogs,
  onSimulateIntent
}) => {
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [selectedLog, setSelectedLog] = useState<DiLinkLogEntry | null>(logs[0] || null);

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <div className="w-full max-w-6xl mx-auto py-6 px-4 space-y-6">
      
      {/* HEADER BANNER */}
      <div className="bg-slate-900/90 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-2xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-3 rounded-2xl bg-purple-950 border border-purple-500/40 text-purple-400">
            <Terminal className="w-6 h-6" />
          </div>
          <div>
            <h3 className="font-bold text-white text-base font-mono uppercase tracking-wide flex items-center gap-2">
              DiLink Hardware API & CAN Bus Inspector
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-900 text-purple-300 font-bold">
                Java Reflection Bridge
              </span>
            </h3>
            <p className="text-xs text-slate-400 font-mono">
              Monitora todas as chamadas `com.byd.service.*` e Broadcast Intents em tempo real.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => onSimulateIntent('MASTER_LIGHTS_OFF_INTENT')}
            className="flex items-center gap-1.5 px-3 py-2 bg-cyan-950 border border-cyan-500/40 hover:bg-cyan-900 rounded-xl text-cyan-300 font-mono text-xs font-bold transition-all"
          >
            <Play className="w-3.5 h-3.5 text-cyan-400" /> Testar Intent Apagar Luzes
          </button>
          
          <button
            onClick={onClearLogs}
            className="flex items-center gap-1.5 px-3 py-2 bg-slate-950 border border-slate-800 hover:bg-slate-800 rounded-xl text-slate-400 font-mono text-xs font-bold transition-all"
          >
            <Trash2 className="w-3.5 h-3.5 text-slate-500" /> Limpar Logs
          </button>
        </div>
      </div>

      {/* TERMINAL LOG STREAM & DETAIL VIEW */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Column: Log Feed Stream */}
        <div className="lg:col-span-2 bg-slate-950 rounded-3xl p-4 border border-slate-800 shadow-2xl flex flex-col h-[500px]">
          <div className="flex items-center justify-between px-3 py-2 border-b border-slate-800/80 mb-3">
            <span className="text-xs font-mono text-slate-400 font-bold flex items-center gap-1.5">
              <Cpu className="w-4 h-4 text-emerald-400" /> Stream de Hardware Events ({logs.length})
            </span>
            <span className="text-[10px] font-mono text-slate-500">
              Live CAN-Bus Rx/Tx
            </span>
          </div>

          <div className="flex-1 overflow-y-auto space-y-2 pr-1 font-mono text-xs scrollbar-thin">
            {logs.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-slate-600">
                <Terminal className="w-8 h-8 mb-2 opacity-50" />
                <span>Nenhum comando enviado ainda. Toque em qualquer botão da interface!</span>
              </div>
            ) : (
              logs.map((log) => (
                <div
                  key={log.id}
                  onClick={() => setSelectedLog(log)}
                  className={`p-3 rounded-xl border transition-all cursor-pointer ${
                    selectedLog?.id === log.id
                      ? 'bg-purple-950/60 border-purple-500 text-purple-200'
                      : 'bg-slate-900/60 border-slate-800 text-slate-300 hover:bg-slate-900'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] text-slate-500 font-bold">
                      [{log.timestamp}]
                    </span>
                    <span className={`text-[9px] px-1.5 py-0.5 rounded uppercase font-bold ${
                      log.category === 'LIGHT'
                        ? 'bg-amber-950 text-amber-300 border border-amber-800'
                        : log.category === 'SEATBELT'
                        ? 'bg-red-950 text-red-300 border border-red-800'
                        : 'bg-cyan-950 text-cyan-300 border border-cyan-800'
                    }`}>
                      {log.category}
                    </span>
                  </div>

                  <p className="font-bold text-slate-200 text-xs mb-1">
                    {log.actionName}
                  </p>

                  <p className="text-[11px] text-emerald-400/90 truncate font-mono bg-slate-950 p-1.5 rounded border border-slate-800">
                    {log.javaCodeCall}
                  </p>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Right Column: Code Detail Code Inspector */}
        <div className="bg-slate-900/90 rounded-3xl p-5 border border-slate-800 shadow-2xl flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-4">
              <span className="text-xs font-mono font-bold text-purple-300 uppercase flex items-center gap-1.5">
                <Code2 className="w-4 h-4 text-purple-400" /> Detalhes da Chamada Java
              </span>
              {selectedLog && (
                <button
                  onClick={() => copyToClipboard(selectedLog.javaCodeCall, 'detail')}
                  className="p-1.5 bg-slate-800 hover:bg-slate-700 rounded-lg text-slate-300 transition-all"
                  title="Copiar código Java"
                >
                  {copiedId === 'detail' ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                </button>
              )}
            </div>

            {selectedLog ? (
              <div className="space-y-4 font-mono text-xs">
                <div>
                  <label className="text-[10px] uppercase text-slate-500 font-bold">Ação Executada</label>
                  <p className="text-white font-bold">{selectedLog.actionName}</p>
                </div>

                <div>
                  <label className="text-[10px] uppercase text-slate-500 font-bold">Código Java (Reflection / Native SDK)</label>
                  <pre className="mt-1 p-3 bg-slate-950 rounded-xl border border-slate-800 text-cyan-300 text-[11px] whitespace-pre-wrap overflow-x-auto">
                    {selectedLog.javaCodeCall}
                  </pre>
                </div>

                <div>
                  <label className="text-[10px] uppercase text-slate-500 font-bold">Broadcast Intent Action</label>
                  <p className="mt-1 p-2 bg-slate-950 rounded-lg border border-slate-800 text-amber-300 text-[11px]">
                    {selectedLog.intentAction}
                  </p>
                </div>

                <div>
                  <label className="text-[10px] uppercase text-slate-500 font-bold">Extra Bundle Parameters</label>
                  <pre className="mt-1 p-2 bg-slate-950 rounded-lg border border-slate-800 text-slate-400 text-[10px]">
                    {JSON.stringify(selectedLog.extraParams, null, 2)}
                  </pre>
                </div>
              </div>
            ) : (
              <div className="text-slate-500 text-xs text-center py-12 font-mono">
                Selecione um log no painel à esquerda para ver os detalhes da API Java do DiLink.
              </div>
            )}
          </div>

          <div className="pt-4 border-t border-slate-800 mt-4 text-[11px] font-mono text-slate-400 flex items-center gap-1.5">
            <Zap className="w-4 h-4 text-amber-400 shrink-0" />
            <span>Compatível com multimídias BYD Dolphin, Seal, Song Plus, Yuan e Han.</span>
          </div>
        </div>

      </div>
    </div>
  );
};
