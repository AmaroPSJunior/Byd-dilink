import React from 'react';
import { 
  Fan, 
  Thermometer, 
  Zap, 
  BatteryCharging, 
  Power, 
  Flame, 
  Wind, 
  Activity,
  Gauge
} from 'lucide-react';
import { VehicleState } from '../types';

interface ClimateAndBatteryProps {
  vehicleState: VehicleState;
  setVehicleState: React.Dispatch<React.SetStateAction<VehicleState>>;
}

export const ClimateAndBattery: React.FC<ClimateAndBatteryProps> = ({
  vehicleState,
  setVehicleState
}) => {
  const { climate, batterySoc, batteryTempC, rangeKm, driveMode } = vehicleState;

  const handleTempChange = (delta: number) => {
    setVehicleState(prev => ({
      ...prev,
      climate: {
        ...prev.climate,
        targetTempC: Math.min(32, Math.max(16, prev.climate.targetTempC + delta))
      }
    }));
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Climate Control Card */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-5">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
              <Fan className="w-4 h-4 text-cyan-400" />
              Ar-Condicionado Dual-Zone
            </h3>

            <button
              onClick={() => {
                setVehicleState(prev => ({
                  ...prev,
                  climate: { ...prev.climate, power: !prev.climate.power }
                }));
              }}
              className={`p-2.5 rounded-xl border transition ${
                climate.power
                  ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/50 shadow-md shadow-cyan-950'
                  : 'bg-slate-800 text-slate-400 border-slate-700'
              }`}
            >
              <Power className="w-5 h-5" />
            </button>
          </div>

          {/* Temperature Controls */}
          <div className="flex items-center justify-center gap-6 py-4">
            <button
              onClick={() => handleTempChange(-0.5)}
              className="w-12 h-12 rounded-2xl bg-slate-800 border border-slate-700 text-2xl font-bold text-cyan-300 hover:bg-slate-700 active:scale-95 transition flex items-center justify-center"
            >
              -
            </button>

            <div className="text-center">
              <span className="text-4xl font-mono font-extrabold text-slate-100">{climate.targetTempC.toFixed(1)}°C</span>
              <p className="text-xs text-slate-400 font-mono mt-1">Temperatura Alvo</p>
            </div>

            <button
              onClick={() => handleTempChange(0.5)}
              className="w-12 h-12 rounded-2xl bg-slate-800 border border-slate-700 text-2xl font-bold text-rose-300 hover:bg-slate-700 active:scale-95 transition flex items-center justify-center"
            >
              +
            </button>
          </div>

          {/* Fan Speed & Seat Heating */}
          <div className="space-y-4">
            <div>
              <div className="flex justify-between text-xs text-slate-300 mb-1">
                <span className="flex items-center gap-1.5"><Wind className="w-3.5 h-3.5 text-cyan-400" /> Velocidade do Ventilador</span>
                <span className="font-mono font-bold text-cyan-300">Nível {climate.fanSpeed} / 7</span>
              </div>
              <input
                type="range"
                min="1"
                max="7"
                value={climate.fanSpeed}
                onChange={(e) => {
                  const val = parseInt(e.target.value);
                  setVehicleState(prev => ({
                    ...prev,
                    climate: { ...prev.climate, fanSpeed: val }
                  }));
                }}
                className="w-full accent-cyan-400 bg-slate-800 rounded-lg cursor-pointer h-2"
              />
            </div>

            <div className="grid grid-cols-2 gap-3 pt-2">
              <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex items-center justify-between">
                <div>
                  <p className="text-xs font-bold text-slate-200">Aquecimento Motorista</p>
                  <p className="text-[10px] text-slate-400">Nível {climate.driverSeatHeat}/3</p>
                </div>
                <button
                  onClick={() => {
                    setVehicleState(prev => ({
                      ...prev,
                      climate: { ...prev.climate, driverSeatHeat: (prev.climate.driverSeatHeat + 1) % 4 }
                    }));
                  }}
                  className={`p-2 rounded-lg border ${climate.driverSeatHeat > 0 ? 'bg-amber-500/20 text-amber-300 border-amber-500/50' : 'bg-slate-800 text-slate-500'}`}
                >
                  <Flame className="w-4 h-4" />
                </button>
              </div>

              <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex items-center justify-between">
                <div>
                  <p className="text-xs font-bold text-slate-200">Aquecimento Passageiro</p>
                  <p className="text-[10px] text-slate-400">Nível {climate.passSeatHeat}/3</p>
                </div>
                <button
                  onClick={() => {
                    setVehicleState(prev => ({
                      ...prev,
                      climate: { ...prev.climate, passSeatHeat: (prev.climate.passSeatHeat + 1) % 4 }
                    }));
                  }}
                  className={`p-2 rounded-lg border ${climate.passSeatHeat > 0 ? 'bg-amber-500/20 text-amber-300 border-amber-500/50' : 'bg-slate-800 text-slate-500'}`}
                >
                  <Flame className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Battery & Powertrain Status */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-5">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
              <Zap className="w-4 h-4 text-emerald-400" />
              Bateria Blade & Powertrain
            </h3>
            <span className="px-2.5 py-0.5 text-[10px] font-mono font-bold bg-emerald-950 text-emerald-300 border border-emerald-800 rounded-full">
              LFP Blade 82.5 kWh
            </span>
          </div>

          <div className="grid grid-cols-2 gap-3 font-mono">
            <div className="bg-slate-950 p-3.5 rounded-xl border border-slate-800">
              <p className="text-[10px] text-slate-400">Carga Restante (SoC)</p>
              <p className="text-2xl font-extrabold text-emerald-400 mt-1">{batterySoc}%</p>
              <p className="text-[10px] text-slate-500 font-sans">Estimado: {rangeKm} km</p>
            </div>

            <div className="bg-slate-950 p-3.5 rounded-xl border border-slate-800">
              <p className="text-[10px] text-slate-400">Temperatura do Pack</p>
              <p className="text-2xl font-extrabold text-cyan-300 mt-1">{batteryTempC}°C</p>
              <p className="text-[10px] text-slate-500 font-sans">Resfriamento Líquido OK</p>
            </div>

            <div className="bg-slate-950 p-3.5 rounded-xl border border-slate-800">
              <p className="text-[10px] text-slate-400">Tensão Nominal</p>
              <p className="text-lg font-bold text-slate-200 mt-1">614.4 V</p>
              <p className="text-[10px] text-slate-500 font-sans">Balanceamento 3.2V/Célula</p>
            </div>

            <div className="bg-slate-950 p-3.5 rounded-xl border border-slate-800">
              <p className="text-[10px] text-slate-400">Saúde da Bateria (SoH)</p>
              <p className="text-lg font-bold text-slate-200 mt-1">100.0%</p>
              <p className="text-[10px] text-slate-500 font-sans">0 Códigos de Falha DTC</p>
            </div>
          </div>

          {/* Drive Mode Selector */}
          <div className="pt-2">
            <label className="text-xs font-bold text-slate-300 mb-2 block flex items-center gap-1.5">
              <Gauge className="w-4 h-4 text-purple-400" />
              Seletor de Modo de Condução:
            </label>
            <div className="grid grid-cols-4 gap-2">
              {(['ECO', 'NORMAL', 'SPORT', 'SNOW'] as const).map(mode => (
                <button
                  key={mode}
                  onClick={() => {
                    setVehicleState(prev => ({ ...prev, driveMode: mode }));
                  }}
                  className={`py-2 text-xs font-mono font-bold rounded-xl border transition ${
                    driveMode === mode
                      ? 'bg-purple-900/60 border-purple-500 text-purple-200 shadow-md shadow-purple-950'
                      : 'bg-slate-950 border-slate-800 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  {mode}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
