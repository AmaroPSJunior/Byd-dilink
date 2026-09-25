import React, { useState } from 'react';
import { 
  Terminal, 
  Play, 
  Square, 
  Send, 
  Filter, 
  Trash2, 
  RefreshCw, 
  Download,
  Info,
  Radio
} from 'lucide-react';
import { CanPacket } from '../types';

interface CanSnifferProps {
  packets: CanPacket[];
  onClearPackets: () => void;
  onSendCustomPacket: (busId: string, canId: string, dlc: number, dataHex: string, desc: string) => void;
  isPaused: boolean;
  setIsPaused: (val: boolean) => void;
}

export const CanSniffer: React.FC<CanSnifferProps> = ({
  packets,
  onClearPackets,
  onSendCustomPacket,
  isPaused,
  setIsPaused
}) => {
  const [filterBus, setFilterBus] = useState<string>('ALL');
  const [canIdInput, setCanIdInput] = useState<string>('0x03B');
  const [busInput, setBusInput] = useState<string>('LIN1_DOME');
  const [dataInput, setDataInput] = useState<string>('00 11 00 00 00 00 00 00');
  const [descInput, setDescInput] = useState<string>('Comando Manual DiLink BCM');

  const filteredPackets = packets.filter(p => {
    if (filterBus === 'ALL') return true;
    return p.busId === filterBus;
  });

  const presetCommands = [
    { label: 'Apagar Luzes Internas (Hard Confirm)', bus: 'LIN1_DOME', id: '0x03B', data: '00 11 00 00 00 00 00 00', desc: 'Desliga todas as luzes do teto e leitoras' },
    { label: 'Trancar Todas as Portas', bus: 'CAN2_BODY', id: '0x1A0', data: 'FF 01 00 00 00 00 00 00', desc: 'Comando trava central do veículo' },
    { label: 'Subir Todos os Vidros (1-Touch)', bus: 'CAN2_BODY', id: '0x2A5', data: '00 00 00 00 00 00 00 00', desc: 'Comando BCM recolher vidros' },
    { label: 'Ping Diagnóstico Gateway UDS', bus: 'UDS_DIAG', id: '0x7DF', data: '02 10 01 00 00 00 00 00', desc: 'Diagnostic Session Control Request' },
    { label: 'Solicitar Telemetria de Bateria', bus: 'CAN1_PWR', id: '0x350', data: '01 0B 00 00 00 00 00 00', desc: 'Request BMS Pack Volt & Temp' },
  ];

  const handleTransmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSendCustomPacket(busInput, canIdInput, 8, dataInput, descInput);
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: Realtime Terminal Packet Stream */}
        <div className="lg:col-span-2 bg-slate-900/90 border border-slate-800 rounded-2xl p-6 space-y-4 flex flex-col h-[580px]">
          {/* Header & Filter Controls */}
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 border-b border-slate-800 pb-3">
            <div className="flex items-center gap-2">
              <Terminal className="w-5 h-5 text-cyan-400" />
              <h3 className="text-sm font-bold text-slate-100">Sniffer do Barramento CAN / DiLink</h3>
              <span className="text-[10px] font-mono bg-cyan-950 text-cyan-300 px-2 py-0.5 rounded border border-cyan-800">
                {packets.length} Pacotes Capturados
              </span>
            </div>

            <div className="flex items-center gap-2">
              {/* Pause/Play Stream */}
              <button
                onClick={() => setIsPaused(!isPaused)}
                className={`p-2 rounded-lg text-xs font-bold border flex items-center gap-1 transition ${
                  isPaused
                    ? 'bg-amber-950/80 border-amber-500/60 text-amber-300'
                    : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
                }`}
              >
                {isPaused ? <Play className="w-3.5 h-3.5 text-amber-400" /> : <Square className="w-3.5 h-3.5 text-rose-400" />}
                <span>{isPaused ? 'Retomar' : 'Pausar'}</span>
              </button>

              {/* Filter */}
              <select
                value={filterBus}
                onChange={(e) => setFilterBus(e.target.value)}
                className="bg-slate-950 border border-slate-700 text-slate-200 text-xs font-mono rounded-lg px-2.5 py-2 focus:outline-none focus:border-cyan-400"
              >
                <option value="ALL">Todos os Barramentos</option>
                <option value="LIN1_DOME">LIN1_DOME</option>
                <option value="CAN1_PWR">CAN1_POWERTRAIN</option>
                <option value="CAN2_BODY">CAN2_BODY</option>
                <option value="UDS_DIAG">UDS_DIAGNOSTICS</option>
              </select>

              {/* Clear */}
              <button
                onClick={onClearPackets}
                className="p-2 rounded-lg bg-slate-800 border border-slate-700 text-slate-400 hover:text-rose-400 hover:border-rose-800 transition"
                title="Limpar Histórico"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Packet Stream Console Log */}
          <div className="flex-1 bg-slate-950 rounded-xl p-3 border border-slate-800 font-mono text-xs overflow-y-auto space-y-1.5 no-scrollbar">
            {filteredPackets.length === 0 ? (
              <div className="text-slate-600 text-center py-12 text-xs">
                Nenhum pacote capturado para o filtro selecionado.
              </div>
            ) : (
              filteredPackets.map((pkt) => (
                <div
                  key={pkt.id}
                  className={`p-2 rounded-lg border flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px] leading-tight ${
                    pkt.direction === 'TX'
                      ? 'bg-cyan-950/30 border-cyan-800/50 text-cyan-200'
                      : 'bg-slate-900/60 border-slate-800/80 text-slate-300'
                  }`}
                >
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-slate-500 text-[10px]">{pkt.timestamp}</span>
                    <span className={`px-1.5 py-0.2 rounded font-bold text-[9px] ${
                      pkt.direction === 'TX' ? 'bg-cyan-500/20 text-cyan-300' : 'bg-emerald-500/20 text-emerald-300'
                    }`}>
                      {pkt.direction}
                    </span>
                    <span className="font-bold text-slate-200">{pkt.busId}</span>
                    <span className="text-amber-300 font-bold">{pkt.canId}</span>
                    <span className="text-slate-500">DLC:{pkt.dlc}</span>
                  </div>

                  <div className="flex items-center gap-3">
                    <span className="bg-slate-950 px-2 py-0.5 rounded border border-slate-800 text-cyan-300 tracking-wider">
                      {pkt.data}
                    </span>
                    {pkt.description && (
                      <span className="text-[10px] text-slate-400 max-w-[180px] truncate hidden md:inline">
                        {pkt.description}
                      </span>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Right Col: Frame Generator & Preset Library */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-6 space-y-5">
          <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2 border-b border-slate-800 pb-3">
            <Radio className="w-4 h-4 text-cyan-400" />
            Injetor Manual de Trama CAN
          </h3>

          <form onSubmit={handleTransmit} className="space-y-3 text-xs">
            <div>
              <label className="text-[10px] font-mono text-slate-400 block mb-1">Barramento Alvo:</label>
              <select
                value={busInput}
                onChange={(e) => setBusInput(e.target.value)}
                className="w-full bg-slate-950 border border-slate-700 text-slate-200 rounded-xl px-3 py-2 font-mono focus:outline-none focus:border-cyan-400"
              >
                <option value="LIN1_DOME">LIN1_DOME (Iluminação)</option>
                <option value="CAN1_PWR">CAN1_POWERTRAIN (Motor/Bateria)</option>
                <option value="CAN2_BODY">CAN2_BODY (Carroceria/Portas)</option>
                <option value="UDS_DIAG">UDS_DIAGNOSTICS (Sessão ECU)</option>
              </select>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-[10px] font-mono text-slate-400 block mb-1">ID CAN (Hex):</label>
                <input
                  type="text"
                  value={canIdInput}
                  onChange={(e) => setCanIdInput(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 text-amber-300 font-mono rounded-xl px-3 py-2 focus:outline-none focus:border-cyan-400"
                />
              </div>

              <div>
                <label className="text-[10px] font-mono text-slate-400 block mb-1">DLC (Bytes):</label>
                <input
                  type="number"
                  value={8}
                  readOnly
                  className="w-full bg-slate-950/60 border border-slate-800 text-slate-400 font-mono rounded-xl px-3 py-2"
                />
              </div>
            </div>

            <div>
              <label className="text-[10px] font-mono text-slate-400 block mb-1">Payload Hexadecimal (8 Bytes):</label>
              <input
                type="text"
                value={dataInput}
                onChange={(e) => setDataInput(e.target.value)}
                className="w-full bg-slate-950 border border-slate-700 text-cyan-300 font-mono rounded-xl px-3 py-2 focus:outline-none focus:border-cyan-400"
              />
            </div>

            <div>
              <label className="text-[10px] font-mono text-slate-400 block mb-1">Descrição / Rótulo:</label>
              <input
                type="text"
                value={descInput}
                onChange={(e) => setDescInput(e.target.value)}
                className="w-full bg-slate-950 border border-slate-700 text-slate-300 text-xs rounded-xl px-3 py-2 focus:outline-none focus:border-cyan-400"
              />
            </div>

            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-slate-950 font-bold text-xs flex items-center justify-center gap-2 hover:brightness-110 shadow-lg shadow-cyan-950"
            >
              <Send className="w-4 h-4" />
              Transmitir Pacote
            </button>
          </form>

          {/* Presets */}
          <div className="pt-2 border-t border-slate-800 space-y-2">
            <label className="text-[10px] font-mono text-slate-400 block">Comandos Predefinidos:</label>
            <div className="space-y-1.5">
              {presetCommands.map((preset, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    setBusInput(preset.bus);
                    setCanIdInput(preset.id);
                    setDataInput(preset.data);
                    setDescInput(preset.desc);
                  }}
                  className="w-full text-left p-2 rounded-lg bg-slate-950 border border-slate-800 hover:border-slate-700 text-[11px] flex items-center justify-between text-slate-300 transition"
                >
                  <span className="font-semibold text-slate-200">{preset.label}</span>
                  <span className="font-mono text-[9px] text-cyan-400">{preset.id}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
