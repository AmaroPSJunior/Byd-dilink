import React from 'react';
import {
  Car,
  DoorClosed,
  DoorOpen,
  Lock,
  Unlock,
  Wind,
  ShieldAlert,
  Sliders,
  CheckCircle2,
  ChevronUp,
  ChevronDown
} from 'lucide-react';
import { DoorPosition, DoorState, WindowPosition, WindowState } from '../types';

interface DoorsAndWindowsGridProps {
  doors: Record<DoorPosition, DoorState>;
  windows: Record<WindowPosition, WindowState>;
  onToggleDoor: (position: DoorPosition) => void;
  onToggleLockDoor: (position: DoorPosition) => void;
  onUpdateWindow: (position: WindowPosition, openPercentage: number) => void;
  onGlobalWindowAction: (presetPercentage: number, actionLabel: string) => void;
  onGlobalLockAction: (lockAll: boolean) => void;
  onLogDiLinkAction: (actionName: string, javaCall: string, intent: string, params: Record<string, any>) => void;
}

export const DoorsAndWindowsGrid: React.FC<DoorsAndWindowsGridProps> = ({
  doors,
  windows,
  onToggleDoor,
  onToggleLockDoor,
  onUpdateWindow,
  onGlobalWindowAction,
  onGlobalLockAction,
  onLogDiLinkAction
}) => {
  const openDoorsList = (Object.values(doors) as DoorState[]).filter((d) => d.isOpen);

  return (
    <div className="w-full max-w-5xl mx-auto py-6 px-4 space-y-8">
      
      {/* GLOBAL ONE-TOUCH QUICK ACTIONS */}
      <div className="bg-slate-900/90 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-2xl">
        <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <Car className="w-5 h-5 text-cyan-400" />
            <h3 className="font-bold text-white text-sm font-mono tracking-wide uppercase">
              Ações Rápidas de Portas & Vidros BYD
            </h3>
          </div>
          <span className="text-xs font-mono text-slate-400">
            Automóvel Inteligente DiLink
          </span>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <button
            onClick={() => onGlobalWindowAction(0, 'FECHAR_TODOS_VIDROS')}
            className="p-3.5 bg-cyan-950/60 hover:bg-cyan-900/80 border border-cyan-500/40 rounded-2xl text-cyan-200 font-mono text-xs font-bold flex flex-col items-center gap-2 transition-all"
          >
            <ChevronUp className="w-5 h-5 text-cyan-400" />
            <span>Fechar Todos os Vidros (100%)</span>
          </button>

          <button
            onClick={() => onGlobalWindowAction(10, 'MODO_VENTILACAO_VIDROS')}
            className="p-3.5 bg-blue-950/60 hover:bg-blue-900/80 border border-blue-500/40 rounded-2xl text-blue-200 font-mono text-xs font-bold flex flex-col items-center gap-2 transition-all"
          >
            <Wind className="w-5 h-5 text-blue-400" />
            <span>Modo Ventilação (10%)</span>
          </button>

          <button
            onClick={() => onGlobalLockAction(true)}
            className="p-3.5 bg-emerald-950/60 hover:bg-emerald-900/80 border border-emerald-500/40 rounded-2xl text-emerald-200 font-mono text-xs font-bold flex flex-col items-center gap-2 transition-all"
          >
            <Lock className="w-5 h-5 text-emerald-400" />
            <span>Trancar Todas as Portas</span>
          </button>

          <button
            onClick={() => onGlobalLockAction(false)}
            className="p-3.5 bg-amber-950/60 hover:bg-amber-900/80 border border-amber-500/40 rounded-2xl text-amber-200 font-mono text-xs font-bold flex flex-col items-center gap-2 transition-all"
          >
            <Unlock className="w-5 h-5 text-amber-400" />
            <span>Destravar Veículo</span>
          </button>
        </div>
      </div>

      {/* DOORS & HATCH STATUS MATRIX */}
      <div className="bg-slate-900/90 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-2xl">
        <div className="flex items-center justify-between mb-6 pb-3 border-b border-slate-800">
          <div>
            <h3 className="font-bold text-white text-base font-mono uppercase tracking-wide">
              Detecção & Sensores de Portas
            </h3>
            <p className="text-xs text-slate-400 font-mono">
              Status via `com.byd.action.DOOR_STATE_CHANGED`
            </p>
          </div>
          {openDoorsList.length > 0 ? (
            <span className="px-3 py-1 bg-red-950 text-red-300 border border-red-500/50 rounded-full font-mono text-xs font-bold flex items-center gap-1.5 animate-pulse">
              <ShieldAlert className="w-4 h-4 text-red-400" /> {openDoorsList.length} Porta(s) Aberta(s)
            </span>
          ) : (
            <span className="px-3 py-1 bg-emerald-950 text-emerald-300 border border-emerald-500/50 rounded-full font-mono text-xs font-bold flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-400" /> Veículo Fechado
            </span>
          )}
        </div>

        {/* 6-Door Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
          {(Object.keys(doors) as DoorPosition[]).map((pos) => {
            const door = doors[pos];
            return (
              <div
                key={door.id}
                className={`p-4 rounded-2xl border-2 flex flex-col justify-between gap-3 transition-all ${
                  door.isOpen
                    ? 'bg-red-950/30 border-red-500 text-red-200'
                    : 'bg-slate-950/60 border-slate-800 text-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono font-bold text-sm text-white">
                    {door.name}
                  </span>
                  <span className={`text-[10px] font-mono uppercase font-bold px-2 py-0.5 rounded ${
                    door.isOpen ? 'bg-red-500 text-white' : 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                  }`}>
                    {door.isOpen ? 'ABERTA' : 'FECHADA'}
                  </span>
                </div>

                <div className="flex items-center gap-2 my-1">
                  <button
                    onClick={() => onToggleDoor(pos)}
                    className={`flex-1 py-2 px-3 rounded-xl font-mono text-xs font-bold border transition-all flex items-center justify-center gap-1.5 ${
                      door.isOpen
                        ? 'bg-red-600 text-white border-red-400'
                        : 'bg-slate-800 text-slate-300 border-slate-700 hover:bg-slate-700'
                    }`}
                  >
                    {door.isOpen ? (
                      <>
                        <DoorOpen className="w-4 h-4 text-white" /> Fechar
                      </>
                    ) : (
                      <>
                        <DoorClosed className="w-4 h-4 text-emerald-400" /> Abrir
                      </>
                    )}
                  </button>

                  <button
                    onClick={() => onToggleLockDoor(pos)}
                    className={`p-2 rounded-xl border transition-all ${
                      door.isLocked
                        ? 'bg-emerald-950 text-emerald-300 border-emerald-600'
                        : 'bg-amber-950 text-amber-300 border-amber-600'
                    }`}
                    title={door.isLocked ? 'Trancada' : 'Destrancada'}
                  >
                    {door.isLocked ? (
                      <Lock className="w-4 h-4 text-emerald-400" />
                    ) : (
                      <Unlock className="w-4 h-4 text-amber-400" />
                    )}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* WINDOWS POSITION CONTROL DECK */}
      <div className="bg-slate-900/90 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-2xl">
        <div className="flex items-center justify-between mb-6 pb-3 border-b border-slate-800">
          <div>
            <h3 className="font-bold text-white text-base font-mono uppercase tracking-wide">
              Controle Múltiplo de Vidros (0% a 100%)
            </h3>
            <p className="text-xs text-slate-400 font-mono">
              Interação com `com.byd.service.BYDAutoWindowBus`
            </p>
          </div>
          <Sliders className="w-5 h-5 text-cyan-400" />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {(Object.keys(windows) as WindowPosition[]).map((pos) => {
            const win = windows[pos];
            return (
              <div
                key={win.id}
                className="bg-slate-950/70 p-4 rounded-2xl border border-slate-800 space-y-3"
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono font-bold text-sm text-slate-200">
                    {win.name}
                  </span>
                  <span className="font-mono font-bold text-xs text-cyan-400 bg-slate-900 px-2.5 py-1 rounded-lg border border-slate-800">
                    {win.openPercentage === 0 ? 'FECHADO (0%)' : `${win.openPercentage}% ABERTO`}
                  </span>
                </div>

                {/* Progress Visual Bar */}
                <div className="w-full bg-slate-800 h-3 rounded-full overflow-hidden p-0.5 border border-slate-700">
                  <div
                    className="bg-gradient-to-r from-cyan-500 to-blue-500 h-full rounded-full transition-all duration-300"
                    style={{ width: `${win.openPercentage}%` }}
                  />
                </div>

                <input
                  type="range"
                  min="0"
                  max="100"
                  step="5"
                  value={win.openPercentage}
                  onChange={(e) => {
                    const pct = parseInt(e.target.value, 10);
                    onUpdateWindow(pos, pct);
                  }}
                  className="w-full accent-cyan-400 h-2 bg-slate-800 rounded-lg cursor-pointer"
                />

                <div className="flex items-center gap-2 pt-1">
                  <button
                    onClick={() => onUpdateWindow(pos, 0)}
                    className="flex-1 py-1 px-2 rounded bg-slate-900 border border-slate-800 text-[11px] font-mono text-slate-300 hover:bg-slate-800"
                  >
                    Fechar (0%)
                  </button>
                  <button
                    onClick={() => onUpdateWindow(pos, 20)}
                    className="flex-1 py-1 px-2 rounded bg-slate-900 border border-slate-800 text-[11px] font-mono text-slate-300 hover:bg-slate-800"
                  >
                    Ventilar (20%)
                  </button>
                  <button
                    onClick={() => onUpdateWindow(pos, 100)}
                    className="flex-1 py-1 px-2 rounded bg-slate-900 border border-slate-800 text-[11px] font-mono text-slate-300 hover:bg-slate-800"
                  >
                    Total (100%)
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>

    </div>
  );
};
