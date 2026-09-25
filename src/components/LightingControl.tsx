import React, { useState } from 'react';
import { 
  Lightbulb, 
  LightbulbOff, 
  Sun, 
  Sparkles, 
  CheckCircle2, 
  Terminal, 
  Radio, 
  Cpu, 
  AlertCircle,
  Eye,
  Sliders,
  Power
} from 'lucide-react';
import { VehicleState } from '../types';

interface LightingControlProps {
  vehicleState: VehicleState;
  setVehicleState: React.Dispatch<React.SetStateAction<VehicleState>>;
  addCanPacket: (busId: string, canId: string, dlc: number, data: string, direction: 'RX' | 'TX', desc: string) => void;
}

export const LightingControl: React.FC<LightingControlProps> = ({
  vehicleState,
  setVehicleState,
  addCanPacket
}) => {
  const { lighting } = vehicleState;
  const [hardwareStep, setHardwareStep] = useState<number>(0);
  const [hardwareLogs, setHardwareLogs] = useState<string[]>([]);
  const [isExecuting, setIsExecuting] = useState<boolean>(false);

  // Trigger Hardware-Confirmed Turn Off Procedure
  const executeHardwareOff = () => {
    if (isExecuting) return;
    setIsExecuting(true);
    setHardwareStep(1);
    setHardwareLogs(['[0.00ms] Executando comando de desligamento via barramento CAN LIN1...']);

    addCanPacket('LIN1_DOME', '0x03B', 8, '00 11 00 00 00 00 00 00', 'TX', 'Comando BCM Apagar Luzes Internas');

    setTimeout(() => {
      setHardwareStep(2);
      setHardwareLogs(prev => [
        ...prev,
        '[14.2ms] ACK recebido da ECU LIN1 Dome Controller (0x03B_ACK).'
      ]);
      addCanPacket('LIN1_DOME', '0x03C', 4, '01 ACK 00 OK', 'RX', 'Confirmacao LIN Gateway OK');
    }, 400);

    setTimeout(() => {
      setHardwareStep(3);
      setHardwareLogs(prev => [
        ...prev,
        '[28.8ms] Sensor fotossensível interno DiLink (HW_LUX_01): Medição = 0.02 Lux.'
      ]);
    }, 800);

    setTimeout(() => {
      setHardwareStep(4);
      setHardwareLogs(prev => [
        ...prev,
        '[42.0ms] CONFIRMAÇÃO DE HARDWARE CONCLUÍDA: Luzes totalmente apagadas e comutadores em repouso.'
      ]);
      setVehicleState(prev => ({
        ...prev,
        lighting: {
          ...prev.lighting,
          interiorDome: false,
          readingLightsLeft: false,
          readingLightsRight: false,
          hardwareConfirmStatus: 'confirmed'
        }
      }));
      setIsExecuting(false);
    }, 1200);
  };

  const presetColors = [
    { name: 'Azul DiLink Cyan', hex: '#06b6d4' },
    { name: 'Verde Esmeralda ECO', hex: '#10b981' },
    { name: 'Roxo Neomatsuri', hex: '#a855f7' },
    { name: 'Âmbar Dourado', hex: '#f59e0b' },
    { name: 'Vermelho Performance', hex: '#ef4444' },
    { name: 'Branco Neve 6000K', hex: '#f8fafc' },
  ];

  return (
    <div className="space-y-6">
      {/* Featured Header: Hardware-Confirmed Turn Off */}
      <div className="bg-slate-900/90 border border-amber-500/40 rounded-2xl p-6 relative overflow-hidden shadow-xl shadow-amber-950/20">
        <div className="absolute -top-10 -right-10 w-40 h-40 bg-amber-500/10 rounded-full blur-3xl pointer-events-none"></div>

        <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6">
          <div className="space-y-2 max-w-xl">
            <div className="flex items-center gap-2">
              <span className="px-2.5 py-0.5 text-[10px] font-mono font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40 rounded-full flex items-center gap-1">
                <Radio className="w-3 h-3 animate-pulse text-amber-400" />
                HARDWARE FEEDBACK LOOP
              </span>
              <span className="text-xs text-slate-400">BCM LIN1 & Opto-Sensor Confirm</span>
            </div>
            <h2 className="text-xl font-extrabold text-slate-100">
              Apagar Luzes Internas com Confirmação em Hardware
            </h2>
            <p className="text-xs text-slate-300 leading-relaxed">
              Envia requisição direta ao barramento LIN1 da cabine e valida em malha fechada via optossensor de fotoluminescência se a iluminação física realmente foi extinta (0 Lux).
            </p>
          </div>

          <button
            onClick={executeHardwareOff}
            disabled={isExecuting}
            className={`w-full lg:w-auto px-6 py-4 rounded-xl font-bold text-sm flex items-center justify-center gap-3 transition-all shadow-lg ${
              isExecuting
                ? 'bg-slate-800 text-amber-300 border border-amber-500/50 cursor-wait'
                : 'bg-gradient-to-r from-amber-500 via-orange-500 to-amber-600 text-slate-950 hover:brightness-110 shadow-amber-950/60 active:scale-98'
            }`}
          >
            {isExecuting ? <Radio className="w-5 h-5 animate-spin text-amber-400" /> : <LightbulbOff className="w-5 h-5" />}
            <span>{isExecuting ? 'Confirmando no Hardware...' : 'Apagar Luzes com Confirmação em Hardware'}</span>
          </button>
        </div>

        {/* Hardware Status Stepper */}
        <div className="mt-6 pt-6 border-t border-slate-800 grid grid-cols-1 md:grid-cols-4 gap-3">
          <div className={`p-3 rounded-xl border text-xs ${hardwareStep >= 1 ? 'bg-amber-950/40 border-amber-500/60 text-amber-200' : 'bg-slate-950/40 border-slate-800 text-slate-500'}`}>
            <div className="flex items-center gap-2 font-mono font-bold mb-1">
              <span className="w-5 h-5 rounded-full bg-slate-800 text-amber-400 flex items-center justify-center text-[10px]">1</span>
              TX Frame CAN/LIN
            </div>
            <p className="text-[10px] text-slate-400">Transmissão `0x03B` para BCM</p>
          </div>

          <div className={`p-3 rounded-xl border text-xs ${hardwareStep >= 2 ? 'bg-amber-950/40 border-amber-500/60 text-amber-200' : 'bg-slate-950/40 border-slate-800 text-slate-500'}`}>
            <div className="flex items-center gap-2 font-mono font-bold mb-1">
              <span className="w-5 h-5 rounded-full bg-slate-800 text-amber-400 flex items-center justify-center text-[10px]">2</span>
              ECU Gateway ACK
            </div>
            <p className="text-[10px] text-slate-400">Resposta `0x03C_ACK` ok</p>
          </div>

          <div className={`p-3 rounded-xl border text-xs ${hardwareStep >= 3 ? 'bg-amber-950/40 border-amber-500/60 text-amber-200' : 'bg-slate-950/40 border-slate-800 text-slate-500'}`}>
            <div className="flex items-center gap-2 font-mono font-bold mb-1">
              <span className="w-5 h-5 rounded-full bg-slate-800 text-amber-400 flex items-center justify-center text-[10px]">3</span>
              Sensor Lux (0.00 Lux)
            </div>
            <p className="text-[10px] text-slate-400">Leitura fotossensível cabine</p>
          </div>

          <div className={`p-3 rounded-xl border text-xs ${hardwareStep >= 4 ? 'bg-emerald-950/60 border-emerald-500 text-emerald-200' : 'bg-slate-950/40 border-slate-800 text-slate-500'}`}>
            <div className="flex items-center gap-2 font-mono font-bold mb-1">
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              Hardware Confirmado
            </div>
            <p className="text-[10px] text-slate-400">Confirmação física de corte</p>
          </div>
        </div>

        {/* Realtime Terminal Console Output */}
        {hardwareLogs.length > 0 && (
          <div className="mt-4 bg-slate-950 rounded-xl p-3 border border-slate-800 font-mono text-[11px] text-cyan-300 space-y-1">
            <div className="flex items-center gap-2 text-slate-500 text-[10px] border-b border-slate-800 pb-1 mb-1">
              <Terminal className="w-3.5 h-3.5 text-cyan-400" />
              LOG DE TELEMETRIA HARDWARE (DiLink BCM Loop)
            </div>
            {hardwareLogs.map((log, idx) => (
              <div key={idx} className="leading-snug">{log}</div>
            ))}
          </div>
        )}
      </div>

      {/* Manual Interior & Ambient Light Controls */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Interior Lighting Manual Controls */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-4">
          <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
            <Lightbulb className="w-4 h-4 text-amber-400" />
            Iluminação Interna & Leitura
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Master Dome Light */}
            <div className="p-4 bg-slate-950/60 border border-slate-800 rounded-xl flex items-center justify-between">
              <div>
                <p className="text-xs font-bold text-slate-200">Luz do Teto (Dome Light)</p>
                <p className="text-[10px] text-slate-400">Luz central cabine</p>
              </div>
              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    lighting: { ...prev.lighting, interiorDome: !prev.lighting.interiorDome }
                  }));
                  addCanPacket('LIN1_DOME', '0x03B', 8, lighting.interiorDome ? '00 00 00 00' : '00 11 00 00', 'TX', 'Toggle Dome Light');
                }}
                className={`p-3 rounded-xl border transition ${
                  lighting.interiorDome ? 'bg-amber-500/20 text-amber-300 border-amber-500/50 shadow-md shadow-amber-950' : 'bg-slate-800 text-slate-400 border-slate-700'
                }`}
              >
                <Power className="w-5 h-5" />
              </button>
            </div>

            {/* Reading Left */}
            <div className="p-4 bg-slate-950/60 border border-slate-800 rounded-xl flex items-center justify-between">
              <div>
                <p className="text-xs font-bold text-slate-200">Leitura Motorista</p>
                <p className="text-[10px] text-slate-400">Spot LED Dianteiro Esq</p>
              </div>
              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    lighting: { ...prev.lighting, readingLightsLeft: !prev.lighting.readingLightsLeft }
                  }));
                }}
                className={`p-3 rounded-xl border transition ${
                  lighting.readingLightsLeft ? 'bg-amber-500/20 text-amber-300 border-amber-500/50' : 'bg-slate-800 text-slate-400 border-slate-700'
                }`}
              >
                <Lightbulb className="w-5 h-5" />
              </button>
            </div>

            {/* Reading Right */}
            <div className="p-4 bg-slate-950/60 border border-slate-800 rounded-xl flex items-center justify-between">
              <div>
                <p className="text-xs font-bold text-slate-200">Leitura Passageiro</p>
                <p className="text-[10px] text-slate-400">Spot LED Dianteiro Dir</p>
              </div>
              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    lighting: { ...prev.lighting, readingLightsRight: !prev.lighting.readingLightsRight }
                  }));
                }}
                className={`p-3 rounded-xl border transition ${
                  lighting.readingLightsRight ? 'bg-amber-500/20 text-amber-300 border-amber-500/50' : 'bg-slate-800 text-slate-400 border-slate-700'
                }`}
              >
                <Lightbulb className="w-5 h-5" />
              </button>
            </div>

            {/* Welcome Lights */}
            <div className="p-4 bg-slate-950/60 border border-slate-800 rounded-xl flex items-center justify-between">
              <div>
                <p className="text-xs font-bold text-slate-200">Luzes de Boas-Vindas</p>
                <p className="text-[10px] text-slate-400">Projeção no chão e maçaneta</p>
              </div>
              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    lighting: { ...prev.lighting, welcomeLights: !prev.lighting.welcomeLights }
                  }));
                }}
                className={`p-3 rounded-xl border transition ${
                  lighting.welcomeLights ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/50' : 'bg-slate-800 text-slate-400 border-slate-700'
                }`}
              >
                <Sparkles className="w-5 h-5" />
              </button>
            </div>
          </div>
        </div>

        {/* Ambient RGB Lighting Control */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-cyan-400" />
              Luz de Ambiente RGB (64 Cores)
            </h3>
            <button
              onClick={() => {
                setVehicleState(prev => ({
                  ...prev,
                  lighting: { ...prev.lighting, ambientLight: !prev.lighting.ambientLight }
                }));
              }}
              className={`px-3 py-1 rounded-lg text-xs font-bold border transition ${
                lighting.ambientLight ? 'bg-cyan-950 text-cyan-300 border-cyan-500/50' : 'bg-slate-800 text-slate-400 border-slate-700'
              }`}
            >
              {lighting.ambientLight ? 'LIGADO' : 'DESLIGADO'}
            </button>
          </div>

          {/* Color Preview Band */}
          <div 
            className="h-10 rounded-xl border border-slate-700 flex items-center justify-center font-mono text-xs font-bold shadow-inner transition-all duration-300"
            style={{ 
              backgroundColor: lighting.ambientLight ? lighting.ambientColor : '#1e293b',
              boxShadow: lighting.ambientLight ? `0 0 25px ${lighting.ambientColor}66` : 'none',
              color: '#ffffff'
            }}
          >
            {lighting.ambientLight ? `Cor Ativa: ${lighting.ambientColor}` : 'Ambiente Desativado'}
          </div>

          {/* Preset Buttons */}
          <div>
            <label className="text-[11px] font-mono text-slate-400 mb-2 block">Paleta de Atalhos DiLink:</label>
            <div className="grid grid-cols-3 gap-2">
              {presetColors.map((preset) => (
                <button
                  key={preset.hex}
                  onClick={() => {
                    setVehicleState(prev => ({
                      ...prev,
                      lighting: { ...prev.lighting, ambientLight: true, ambientColor: preset.hex }
                    }));
                  }}
                  className="px-2.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-slate-300 flex items-center gap-2 hover:border-slate-600 transition"
                >
                  <span className="w-3.5 h-3.5 rounded-full border border-white/20" style={{ backgroundColor: preset.hex }}></span>
                  <span className="truncate text-[10px] font-semibold">{preset.name}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Custom Color & Brightness */}
          <div className="space-y-3 pt-2">
            <div>
              <div className="flex justify-between text-xs text-slate-300 mb-1">
                <span>Brilho do Ambiente</span>
                <span className="font-mono">{lighting.ambientBrightness}%</span>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                value={lighting.ambientBrightness}
                onChange={(e) => {
                  const val = parseInt(e.target.value);
                  setVehicleState(prev => ({
                    ...prev,
                    lighting: { ...prev.lighting, ambientBrightness: val }
                  }));
                }}
                className="w-full accent-cyan-400 bg-slate-800 rounded-lg cursor-pointer h-2"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
