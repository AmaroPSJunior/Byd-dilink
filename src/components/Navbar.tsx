import React, { useState, useEffect } from 'react';
import { 
  Car, 
  Lightbulb, 
  Cpu, 
  Sliders, 
  Fan, 
  Terminal, 
  Zap, 
  Lock, 
  Unlock, 
  Wifi, 
  Activity,
  ShieldCheck,
  Battery
} from 'lucide-react';
import { TabType, VehicleState } from '../types';

interface NavbarProps {
  activeTab: TabType;
  setActiveTab: (tab: TabType) => void;
  vehicleState: VehicleState;
  onToggleLock: () => void;
  onlineTransportsCount: number;
}

export const Navbar: React.FC<NavbarProps> = ({
  activeTab,
  setActiveTab,
  vehicleState,
  onToggleLock,
  onlineTransportsCount
}) => {
  const [timeStr, setTimeStr] = useState('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTimeStr(now.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  const navItems: { id: TabType; label: string; icon: React.ReactNode; badge?: string }[] = [
    { id: 'overview', label: 'Visão Geral', icon: <Car className="w-4 h-4" /> },
    { id: 'lighting', label: 'Luzes Internas & Hardware', icon: <Lightbulb className="w-4 h-4" /> },
    { id: 'transports', label: 'Matriz 13 Transportes', icon: <Cpu className="w-4 h-4" />, badge: `${onlineTransportsCount}/13` },
    { id: 'doors_windows', label: 'Portas, Vidros & Cintos', icon: <Sliders className="w-4 h-4" /> },
    { id: 'climate', label: 'Clima & Bateria', icon: <Fan className="w-4 h-4" /> },
    { id: 'can_sniffer', label: 'Sniffer CAN / DiLink', icon: <Terminal className="w-4 h-4" /> },
  ];

  return (
    <header className="bg-slate-900/80 backdrop-blur-md border-b border-cyan-900/40 sticky top-0 z-50 px-4 py-2.5">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-3">
        {/* Brand & Vehicle ID */}
        <div className="flex items-center justify-between w-full md:w-auto">
          <div className="flex items-center gap-3">
            <div className="bg-gradient-to-br from-cyan-500 to-blue-600 p-2 rounded-xl shadow-lg shadow-cyan-500/20 border border-cyan-300/30">
              <Car className="w-6 h-6 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-blue-300 to-teal-300 text-lg">
                  BYD DiLink
                </span>
                <span className="px-2 py-0.5 text-[10px] font-mono font-bold bg-cyan-950 text-cyan-300 border border-cyan-800 rounded-full">
                  LAB v4.2
                </span>
              </div>
              <p className="text-xs text-slate-400 font-mono flex items-center gap-1.5">
                <span className="inline-block w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                Automotive Bus Gateway • OS 4.0.8
              </p>
            </div>
          </div>

          {/* Quick Lock Mobile */}
          <div className="flex items-center gap-2 md:hidden">
            <button
              onClick={onToggleLock}
              className={`p-2 rounded-lg border text-xs font-semibold flex items-center gap-1 transition-all ${
                vehicleState.locked
                  ? 'bg-emerald-950/80 border-emerald-500/50 text-emerald-300'
                  : 'bg-amber-950/80 border-amber-500/50 text-amber-300'
              }`}
            >
              {vehicleState.locked ? <Lock className="w-4 h-4" /> : <Unlock className="w-4 h-4" />}
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className="flex items-center gap-1 overflow-x-auto w-full md:w-auto no-scrollbar py-1">
          {navItems.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all whitespace-nowrap border ${
                  isActive
                    ? 'bg-gradient-to-r from-cyan-600/30 to-blue-600/30 border-cyan-400/60 text-cyan-200 shadow-md shadow-cyan-950/50'
                    : 'bg-slate-800/40 border-slate-700/40 text-slate-400 hover:text-slate-200 hover:bg-slate-800/80'
                }`}
              >
                {item.icon}
                <span>{item.label}</span>
                {item.badge && (
                  <span className={`px-1.5 py-0.2 rounded text-[10px] font-mono font-bold ${
                    isActive ? 'bg-cyan-500/30 text-cyan-200' : 'bg-slate-700/60 text-slate-300'
                  }`}>
                    {item.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Top Right Status Stats */}
        <div className="hidden lg:flex items-center gap-4 text-xs font-mono border-l border-slate-800 pl-4">
          {/* SoC */}
          <div className="flex items-center gap-1.5 bg-slate-800/50 px-2.5 py-1 rounded-lg border border-slate-700/50">
            <Battery className="w-4 h-4 text-emerald-400" />
            <span className="text-slate-200 font-bold">{vehicleState.batterySoc}%</span>
            <span className="text-[10px] text-slate-400">({vehicleState.rangeKm} km)</span>
          </div>

          {/* Quick Lock */}
          <button
            onClick={onToggleLock}
            className={`px-3 py-1 rounded-lg border text-xs font-semibold flex items-center gap-1.5 transition-all ${
              vehicleState.locked
                ? 'bg-emerald-950/60 border-emerald-500/40 text-emerald-300 hover:bg-emerald-900/60'
                : 'bg-amber-950/60 border-amber-500/40 text-amber-300 hover:bg-amber-900/60'
            }`}
          >
            {vehicleState.locked ? <Lock className="w-3.5 h-3.5 text-emerald-400" /> : <Unlock className="w-3.5 h-3.5 text-amber-400" />}
            <span>{vehicleState.locked ? 'Trancado' : 'Destrancado'}</span>
          </button>

          {/* Clock */}
          <div className="text-cyan-400 font-bold bg-cyan-950/40 px-2.5 py-1 rounded-lg border border-cyan-900/50">
            {timeStr || '12:00:00'}
          </div>
        </div>
      </div>
    </header>
  );
};
