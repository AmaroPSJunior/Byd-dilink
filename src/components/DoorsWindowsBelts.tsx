import React from 'react';
import { 
  ShieldCheck, 
  AlertTriangle, 
  Sliders, 
  Maximize2, 
  Minimize2, 
  Lock, 
  Unlock,
  User,
  UserCheck,
  UserX,
  Volume2
} from 'lucide-react';
import { VehicleState } from '../types';

interface DoorsWindowsBeltsProps {
  vehicleState: VehicleState;
  setVehicleState: React.Dispatch<React.SetStateAction<VehicleState>>;
  onToggleDoor: (doorKey: keyof VehicleState['doors']) => void;
  onToggleSeatbelt: (seatKey: keyof VehicleState['seatbelts']) => void;
}

export const DoorsWindowsBelts: React.FC<DoorsWindowsBeltsProps> = ({
  vehicleState,
  setVehicleState,
  onToggleDoor,
  onToggleSeatbelt
}) => {
  const { doors, windows, seatbelts } = vehicleState;

  const doorsList: { key: keyof VehicleState['doors']; label: string; desc: string }[] = [
    { key: 'frontLeft', label: 'Dianteira Esquerda', desc: 'Porta do Motorista' },
    { key: 'frontRight', label: 'Dianteira Direita', desc: 'Porta do Passageiro' },
    { key: 'rearLeft', label: 'Traseira Esquerda', desc: 'Passageiro Trás Esq' },
    { key: 'rearRight', label: 'Traseira Direita', desc: 'Passageiro Trás Dir' },
    { key: 'trunk', label: 'Porta-Malas', desc: 'Tampa Traseira Elétrica' },
    { key: 'frunk', label: 'Capô / Frunk', desc: 'Compartimento Dianteiro' },
  ];

  const windowsList: { key: keyof VehicleState['windows']; label: string }[] = [
    { key: 'frontLeft', label: 'Vidro Dianteiro Esquerdo' },
    { key: 'frontRight', label: 'Vidro Dianteiro Direito' },
    { key: 'rearLeft', label: 'Vidro Traseiro Esquerdo' },
    { key: 'rearRight', label: 'Vidro Traseiro Direito' },
    { key: 'sunroof', label: 'Teto Solar Panorâmico' },
  ];

  const seatsList: { key: keyof VehicleState['seatbelts']; label: string; position: string }[] = [
    { key: 'driver', label: 'Motorista', position: 'Dianteiro Esquerdo' },
    { key: 'passenger', label: 'Passageiro', position: 'Dianteiro Direito' },
    { key: 'rearLeft', label: 'Traseiro Esquerdo', position: 'Fila Traseira' },
    { key: 'rearCenter', label: 'Traseiro Central', position: 'Fila Traseira' },
    { key: 'rearRight', label: 'Traseiro Direito', position: 'Fila Traseira' },
  ];

  const unbuckledWarning = Object.values(seatbelts).some(s => s.occupied && !s.buckled);

  return (
    <div className="space-y-6">
      {/* Warning Top Banner */}
      {unbuckledWarning && (
        <div className="bg-amber-950/80 border border-amber-500/60 rounded-2xl p-4 flex items-center justify-between text-amber-200 animate-pulse">
          <div className="flex items-center gap-3">
            <AlertTriangle className="w-6 h-6 text-amber-400" />
            <div>
              <h4 className="text-sm font-bold">ALERTA DE CINTO DE SEGURANÇA</h4>
              <p className="text-xs text-amber-300">Ocupante detectado no banco sem o cinto afivelado! Sinal sonoro ativo via BCM.</p>
            </div>
          </div>
          <Volume2 className="w-5 h-5 text-amber-400" />
        </div>
      )}

      {/* 3 Main Sections */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Section 1: Doors & Latching */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-cyan-400" />
              Monitoramento de Portas
            </h3>

            <button
              onClick={() => {
                setVehicleState(prev => ({
                  ...prev,
                  doors: { frontLeft: false, frontRight: false, rearLeft: false, rearRight: false, trunk: false, frunk: false }
                }));
              }}
              className="text-[11px] font-mono text-cyan-300 hover:underline"
            >
              Fechar Todas
            </button>
          </div>

          <div className="space-y-2.5">
            {doorsList.map(door => {
              const isOpen = doors[door.key];
              return (
                <div
                  key={door.key}
                  onClick={() => onToggleDoor(door.key)}
                  className={`p-3 rounded-xl border flex items-center justify-between transition cursor-pointer ${
                    isOpen
                      ? 'bg-rose-950/60 border-rose-500/60 text-rose-200'
                      : 'bg-slate-950/60 border-slate-800 text-slate-300 hover:border-slate-700'
                  }`}
                >
                  <div>
                    <p className="text-xs font-bold">{door.label}</p>
                    <p className="text-[10px] text-slate-400">{door.desc}</p>
                  </div>
                  <span className={`px-2.5 py-1 rounded-lg text-[10px] font-mono font-bold ${
                    isOpen ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40' : 'bg-slate-800 text-slate-400'
                  }`}>
                    {isOpen ? 'ABERTA' : 'FECHADA'}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Section 2: Electric Windows & Sunroof */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
              <Sliders className="w-4 h-4 text-cyan-400" />
              Vidros & Teto Solar
            </h3>

            <div className="flex gap-2">
              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    windows: { frontLeft: 0, frontRight: 0, rearLeft: 0, rearRight: 0, sunroof: 0 }
                  }));
                }}
                className="text-[10px] bg-slate-800 px-2 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-700"
              >
                1-Touch Subir
              </button>
            </div>
          </div>

          <div className="space-y-4">
            {windowsList.map(win => {
              const pos = windows[win.key];
              return (
                <div key={win.key} className="space-y-1 bg-slate-950/40 p-3 rounded-xl border border-slate-800/80">
                  <div className="flex justify-between text-xs">
                    <span className="font-semibold text-slate-200">{win.label}</span>
                    <span className="font-mono text-cyan-300 font-bold">{pos}% Abertura</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={pos}
                    onChange={(e) => {
                      const val = parseInt(e.target.value);
                      setVehicleState(prev => ({
                        ...prev,
                        windows: { ...prev.windows, [win.key]: val }
                      }));
                    }}
                    className="w-full accent-cyan-400 bg-slate-800 rounded-lg cursor-pointer h-2"
                  />
                </div>
              );
            })}
          </div>
        </div>

        {/* Section 3: Seatbelts & Occupancy Sensors */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-4">
          <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
            <UserCheck className="w-4 h-4 text-cyan-400" />
            Sensores de Cinto & Presença
          </h3>

          <div className="space-y-3">
            {seatsList.map(seat => {
              const data = seatbelts[seat.key];
              return (
                <div key={seat.key} className="bg-slate-950/60 p-3.5 rounded-xl border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-xs font-bold text-slate-200">{seat.label}</p>
                      <p className="text-[10px] text-slate-400 font-mono">{seat.position}</p>
                    </div>

                    <button
                      onClick={() => onToggleSeatbelt(seat.key)}
                      className={`px-3 py-1 rounded-lg text-xs font-mono font-bold border transition ${
                        !data.occupied
                          ? 'bg-slate-800 border-slate-700 text-slate-400'
                          : data.buckled
                          ? 'bg-emerald-950/80 border-emerald-500/80 text-emerald-300'
                          : 'bg-amber-950/90 border-amber-500/80 text-amber-300 animate-pulse'
                      }`}
                    >
                      {!data.occupied ? 'Vazio' : data.buckled ? 'Cinto OK' : 'Sem Cinto!'}
                    </button>
                  </div>

                  {/* Toggle occupied / buckled sub-buttons */}
                  <div className="flex gap-2 text-[10px] pt-1">
                    <button
                      onClick={() => {
                        setVehicleState(prev => ({
                          ...prev,
                          seatbelts: {
                            ...prev.seatbelts,
                            [seat.key]: { ...prev.seatbelts[seat.key], occupied: !prev.seatbelts[seat.key].occupied }
                          }
                        }));
                      }}
                      className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300"
                    >
                      {data.occupied ? 'Remover Ocupante' : 'Simular Ocupante'}
                    </button>

                    {data.occupied && (
                      <button
                        onClick={() => {
                          setVehicleState(prev => ({
                            ...prev,
                            seatbelts: {
                              ...prev.seatbelts,
                              [seat.key]: { ...prev.seatbelts[seat.key], buckled: !prev.seatbelts[seat.key].buckled }
                            }
                          }));
                        }}
                        className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-cyan-300"
                      >
                        {data.buckled ? 'Desafivelar' : 'Afivelar Cinto'}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};
