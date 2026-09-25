import React, { useState } from 'react';
import { 
  Cpu, 
  Activity, 
  CheckCircle2, 
  AlertTriangle, 
  Send, 
  Radio, 
  Terminal, 
  Layers, 
  Wifi, 
  Zap,
  RefreshCw,
  Info
} from 'lucide-react';
import { TransportBus } from '../types';

interface TransportsMatrixProps {
  transports: TransportBus[];
  setTransports: React.Dispatch<React.SetStateAction<TransportBus[]>>;
  onInjectPacket: (busId: string, commandHex: string) => void;
}

export const TransportsMatrix: React.FC<TransportsMatrixProps> = ({
  transports,
  setTransports,
  onInjectPacket
}) => {
  const [selectedBus, setSelectedBus] = useState<TransportBus>(transports[0]);
  const [testPayload, setTestPayload] = useState<string>('0x00 0x11 0x22 0xFF');

  const onlineCount = transports.filter(t => t.status === 'ONLINE').length;
  const busyCount = transports.filter(t => t.status === 'BUSY').length;

  const handleToggleBusStatus = (id: string) => {
    setTransports(prev =>
      prev.map(bus => {
        if (bus.id === id) {
          const nextStatus = bus.status === 'ONLINE' ? 'OFFLINE' : 'ONLINE';
          return { ...bus, status: nextStatus };
        }
        return bus;
      })
    );
  };

  const handleSendTestFrame = () => {
    if (!selectedBus) return;
    onInjectPacket(selectedBus.id, testPayload);
    // Update last packet for selected bus
    setTransports(prev =>
      prev.map(bus => {
        if (bus.id === selectedBus.id) {
          return {
            ...bus,
            packetsPerSec: bus.packetsPerSec + 12,
            lastPacket: testPayload
          };
        }
        return bus;
      })
    );
  };

  return (
    <div className="space-y-6">
      {/* Header Stat Panel */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
          <div>
            <p className="text-xs text-slate-400 font-mono">Total de Canais</p>
            <h3 className="text-xl font-mono font-extrabold text-cyan-300">13 Transportes</h3>
          </div>
          <div className="p-3 bg-cyan-950 text-cyan-400 rounded-xl border border-cyan-800">
            <Layers className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
          <div>
            <p className="text-xs text-slate-400 font-mono">Barramentos Ativos</p>
            <h3 className="text-xl font-mono font-extrabold text-emerald-400">
              {onlineCount + busyCount} / 13 ONLINE
            </h3>
          </div>
          <div className="p-3 bg-emerald-950 text-emerald-400 rounded-xl border border-emerald-800">
            <CheckCircle2 className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
          <div>
            <p className="text-xs text-slate-400 font-mono">Latência Média</p>
            <h3 className="text-xl font-mono font-extrabold text-slate-100">
              {(transports.reduce((acc, t) => acc + t.latencyMs, 0) / transports.length).toFixed(1)} ms
            </h3>
          </div>
          <div className="p-3 bg-slate-800 text-slate-300 rounded-xl border border-slate-700">
            <Activity className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
          <div>
            <p className="text-xs text-slate-400 font-mono">Taxa Global de Pacotes</p>
            <h3 className="text-xl font-mono font-extrabold text-amber-300">
              {transports.reduce((acc, t) => acc + t.packetsPerSec, 0)} pkg/s
            </h3>
          </div>
          <div className="p-3 bg-amber-950 text-amber-400 rounded-xl border border-amber-800">
            <Radio className="w-6 h-6" />
          </div>
        </div>
      </div>

      {/* 13 Transports Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: The 13 Transports Cards */}
        <div className="lg:col-span-2 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
              <Cpu className="w-4 h-4 text-cyan-400" />
              Matriz dos 13 Transportes BYD DiLink
            </h3>
            <span className="text-xs text-slate-400">Clique em um transporte para inspecionar</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {transports.map((bus, idx) => {
              const isSelected = selectedBus.id === bus.id;
              const isOnline = bus.status === 'ONLINE' || bus.status === 'BUSY';

              return (
                <div
                  key={bus.id}
                  onClick={() => setSelectedBus(bus)}
                  className={`p-4 rounded-xl border transition cursor-pointer relative overflow-hidden ${
                    isSelected
                      ? 'bg-slate-900 border-cyan-400 shadow-lg shadow-cyan-950/60 ring-1 ring-cyan-400/50'
                      : 'bg-slate-900/60 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-2.5">
                      <span className="w-6 h-6 rounded-lg bg-slate-800 border border-slate-700 font-mono font-bold text-xs flex items-center justify-center text-cyan-400">
                        {idx + 1}
                      </span>
                      <div>
                        <h4 className="text-xs font-bold text-slate-100">{bus.name}</h4>
                        <p className="text-[10px] text-slate-400 font-mono">{bus.protocol} • {bus.speed}</p>
                      </div>
                    </div>

                    <span className={`px-2 py-0.5 text-[9px] font-mono font-bold rounded-full border ${
                      bus.status === 'ONLINE' ? 'bg-emerald-950/80 text-emerald-300 border-emerald-500/50' :
                      bus.status === 'BUSY' ? 'bg-amber-950/80 text-amber-300 border-amber-500/50' :
                      'bg-rose-950/80 text-rose-300 border-rose-500/50'
                    }`}>
                      {bus.status}
                    </span>
                  </div>

                  <div className="mt-3 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[10px] font-mono text-slate-400">
                    <span>Fluxo: <strong className="text-slate-200">{bus.packetsPerSec} pk/s</strong></span>
                    <span>Latência: <strong className="text-cyan-300">{bus.latencyMs}ms</strong></span>
                    <span>Erros: <strong className="text-slate-300">{bus.errorCount}</strong></span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Col: Selected Transport Inspector & Injector */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-6 space-y-5">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div>
              <span className="text-[10px] font-mono text-cyan-400 font-bold uppercase">Inspetor de Canal</span>
              <h3 className="text-base font-extrabold text-slate-100">{selectedBus.name}</h3>
            </div>
            <button
              onClick={() => handleToggleBusStatus(selectedBus.id)}
              className={`px-3 py-1 rounded-lg text-xs font-bold border transition ${
                selectedBus.status === 'ONLINE'
                  ? 'bg-rose-950/60 border-rose-500/50 text-rose-300 hover:bg-rose-900'
                  : 'bg-emerald-950/60 border-emerald-500/50 text-emerald-300 hover:bg-emerald-900'
              }`}
            >
              {selectedBus.status === 'ONLINE' ? 'Desativar Canal' : 'Ativar Canal'}
            </button>
          </div>

          <div className="space-y-3 text-xs">
            <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 space-y-1.5 font-mono">
              <div className="flex justify-between text-slate-400">
                <span>Protocolo / Padrão:</span>
                <span className="text-slate-200 font-bold">{selectedBus.protocol}</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>Velocidade de Operação:</span>
                <span className="text-cyan-300">{selectedBus.speed}</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>Último Payload Injetado:</span>
                <span className="text-amber-300 text-[10px] truncate max-w-[150px]">{selectedBus.lastPacket || 'N/A'}</span>
              </div>
            </div>

            <div>
              <label className="text-[11px] font-mono text-slate-400 mb-1 block">Descrição do Transporte:</label>
              <p className="text-xs text-slate-300 bg-slate-950/40 p-3 rounded-xl border border-slate-800/80 leading-relaxed">
                {selectedBus.description}
              </p>
            </div>

            {/* Test Frame Injector */}
            <div className="space-y-2 pt-2 border-t border-slate-800">
              <label className="text-[11px] font-mono text-cyan-300 font-bold flex items-center gap-1.5">
                <Send className="w-3.5 h-3.5 text-cyan-400" />
                Injetar Frame HEX no Barramento
              </label>

              <input
                type="text"
                value={testPayload}
                onChange={(e) => setTestPayload(e.target.value)}
                placeholder="Ex: 0x01 0x22 0xFF 0x00"
                className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-xs font-mono text-amber-300 focus:outline-none focus:border-cyan-400"
              />

              <button
                onClick={handleSendTestFrame}
                className="w-full py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-slate-950 font-bold text-xs flex items-center justify-center gap-2 hover:brightness-110 shadow-lg shadow-cyan-950"
              >
                <Send className="w-4 h-4" />
                Transmitir Pacote ao Transporte #{selectedBus.id}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
