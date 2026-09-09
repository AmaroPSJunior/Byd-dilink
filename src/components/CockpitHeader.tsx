import React from 'react';
import {
  Car,
  Lightbulb,
  ShieldCheck,
  Smartphone,
  Code2,
  Sparkles,
  Zap,
  Thermometer,
  Wifi,
  Cpu,
  Lock,
  Compass,
  Layers
} from 'lucide-react';
import { AppTab, VehicleTelemetry } from '../types';

interface CockpitHeaderProps {
  telemetry: VehicleTelemetry;
  activeTab: AppTab;
  setActiveTab: (tab: AppTab) => void;
  masterLightsOn: boolean;
  unbuckledCount: number;
}

export const CockpitHeader: React.FC<CockpitHeaderProps> = ({
  telemetry,
  activeTab,
  setActiveTab,
  masterLightsOn,
  unbuckledCount
}) => {
  return (
    <header className="bg-slate-900/90 backdrop-blur-md border-b border-slate-800 text-slate-100 p-4 sticky top-0 z-40 shadow-2xl">
      <div className="max-w-7xl mx-auto flex flex-col lg:flex-row items-center justify-between gap-4">
        
        {/* Brand & Vehicle Telemetry */}
        <div className="flex items-center gap-4 w-full lg:w-auto justify-between lg:justify-start">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center shadow-lg shadow-cyan-500/20 border border-cyan-400/30">
              <Car className="w-6 h-6 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold tracking-wider text-lg text-white font-mono">
                  {telemetry.modelName}
                </span>
                <span className="text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-cyan-950/80 text-cyan-400 border border-cyan-800 font-semibold">
                  DiLink {telemetry.diLinkVersion}
                </span>
              </div>
              <p className="text-xs text-slate-400 flex items-center gap-2 font-mono">
                <span>VIN: {telemetry.vin}</span>
                <span className="text-slate-600">•</span>
                <span className="text-emerald-400 flex items-center gap-1">
                  <Cpu className="w-3 h-3" /> CAN-BUS 500k
                </span>
              </p>
            </div>
          </div>

          {/* Status Quick Widgets (Battery, Temp, Gear) */}
          <div className="flex items-center gap-3 bg-slate-950/70 p-2 rounded-xl border border-slate-800">
            <div className="flex items-center gap-1.5 px-2 text-xs font-mono">
              <Zap className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
              <span className="text-emerald-300 font-bold">{telemetry.batteryPercentage}%</span>
              <span className="text-slate-500 text-[10px]">({telemetry.remainingRangeKm} km)</span>
            </div>
            <div className="h-4 w-[1px] bg-slate-800" />
            <div className="flex items-center gap-1 text-xs font-mono text-slate-300">
              <Thermometer className="w-3.5 h-3.5 text-amber-400" />
              <span>{telemetry.cabinTempCelsius}°C</span>
            </div>
            <div className="h-4 w-[1px] bg-slate-800" />
            <div className="px-2 py-0.5 rounded bg-blue-600/30 text-blue-300 border border-blue-500/40 text-xs font-bold font-mono">
              MARCHA {telemetry.gear}
            </div>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className="flex items-center gap-1.5 bg-slate-950/80 p-1.5 rounded-2xl border border-slate-800/80 w-full lg:w-auto overflow-x-auto scrollbar-none">
          <button
            id="tab-cockpit"
            onClick={() => setActiveTab('cockpit')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              activeTab === 'cockpit'
                ? 'bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-md shadow-cyan-900/40 border border-cyan-400/40'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Lightbulb className="w-4 h-4 text-cyan-300" />
            <span>Painel Principal</span>
            {masterLightsOn && (
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
            )}
            {unbuckledCount > 0 && (
              <span className="px-1.5 py-0.5 text-[10px] rounded-full bg-red-500 text-white font-bold">
                {unbuckledCount}
              </span>
            )}
          </button>

          <button
            id="tab-vehicle-features"
            onClick={() => setActiveTab('vehicle_features')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              activeTab === 'vehicle_features'
                ? 'bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-md shadow-cyan-900/40 border border-cyan-400/40'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Layers className="w-4 h-4 text-amber-400" />
            <span>Catálogo de Recursos</span>
          </button>

          <button
            id="tab-doors-windows"
            onClick={() => setActiveTab('vehicle_doors_windows')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              activeTab === 'vehicle_doors_windows'
                ? 'bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-md shadow-cyan-900/40 border border-cyan-400/40'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Car className="w-4 h-4 text-blue-300" />
            <span>Portas & Vidros</span>
          </button>

          <button
            id="tab-dilink-inspector"
            onClick={() => setActiveTab('dilink_inspector')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              activeTab === 'dilink_inspector'
                ? 'bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-md shadow-cyan-900/40 border border-cyan-400/40'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Code2 className="w-4 h-4 text-purple-400" />
            <span>Inspector DiLink SDK</span>
          </button>

          <button
            id="tab-apk-export"
            onClick={() => setActiveTab('apk_export')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              activeTab === 'apk_export'
                ? 'bg-gradient-to-r from-emerald-600 to-teal-600 text-white shadow-md shadow-emerald-900/40 border border-emerald-400/40'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Smartphone className="w-4 h-4 text-emerald-400" />
            <span>Gerar APK & GitHub</span>
          </button>

          <button
            id="tab-ai-assistant"
            onClick={() => setActiveTab('ai_assistant')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              activeTab === 'ai_assistant'
                ? 'bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white shadow-md shadow-violet-900/40 border border-violet-400/40'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Sparkles className="w-4 h-4 text-fuchsia-300" />
            <span>Voz & IA BYD</span>
          </button>
        </nav>

      </div>
    </header>
  );
};

