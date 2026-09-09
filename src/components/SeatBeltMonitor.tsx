import React from 'react';
import { ShieldCheck, ShieldAlert, User, Volume2, VolumeX, AlertTriangle, CheckCircle2, Info } from 'lucide-react';
import { SeatBeltState, SeatPosition } from '../types';

interface SeatBeltMonitorProps {
  seatBelts: Record<SeatPosition, SeatBeltState>;
  onToggleSeatBelt: (position: SeatPosition) => void;
  onToggleOccupied: (position: SeatPosition) => void;
  chimeEnabled: boolean;
  setChimeEnabled: (enabled: boolean) => void;
  onLogDiLinkAction: (actionName: string, javaCall: string, intent: string, params: Record<string, any>) => void;
}

export const SeatBeltMonitor: React.FC<SeatBeltMonitorProps> = ({
  seatBelts,
  onToggleSeatBelt,
  onToggleOccupied,
  chimeEnabled,
  setChimeEnabled,
  onLogDiLinkAction
}) => {
  const seatsList: SeatBeltState[] = [
    seatBelts.driver,
    seatBelts.passenger,
    seatBelts.rear_left,
    seatBelts.rear_center,
    seatBelts.rear_right
  ];

  const unbuckledOccupiedSeats = seatsList.filter((s) => s.isOccupied && !s.isBuckled);
  const unbuckledCount = unbuckledOccupiedSeats.length;

  return (
    <div className="w-full max-w-4xl mx-auto my-6 bg-slate-900/90 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-2xl">
      
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6 pb-4 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <div className={`p-3 rounded-2xl border ${
            unbuckledCount > 0
              ? 'bg-red-950/80 border-red-500/50 text-red-400 animate-pulse'
              : 'bg-emerald-950/80 border-emerald-500/50 text-emerald-400'
          }`}>
            {unbuckledCount > 0 ? (
              <ShieldAlert className="w-7 h-7" />
            ) : (
              <ShieldCheck className="w-7 h-7" />
            )}
          </div>
          <div>
            <h3 className="text-lg font-bold text-white font-mono uppercase tracking-wide flex items-center gap-2">
              Status dos Cintos de Segurança
              <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-normal">
                Sensores de Peso ISO 26262
              </span>
            </h3>
            <p className="text-xs text-slate-400 font-mono">
              Monitoramento em Tempo Real via DiLink CAN-Bus (`com.byd.action.SEATBELT_STATE_CHANGED`)
            </p>
          </div>
        </div>

        {/* Chime Toggle Button */}
        <button
          onClick={() => {
            const next = !chimeEnabled;
            setChimeEnabled(next);
            onLogDiLinkAction(
              next ? 'LIGAR_ALERTA_SONORO_CINTO' : 'DESLIGAR_ALERTA_SONORO_CINTO',
              `BYDAutoSeatBeltBus.getInstance(context).setSeatbeltWarningChime(${next});`,
              'com.byd.action.SEATBELT_CHIME',
              { chimeEnabled: next }
            );
          }}
          className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-mono font-semibold border transition-all ${
            chimeEnabled
              ? 'bg-amber-950/60 border-amber-500/50 text-amber-300'
              : 'bg-slate-950 border-slate-800 text-slate-500'
          }`}
        >
          {chimeEnabled ? (
            <>
              <Volume2 className="w-4 h-4 text-amber-400" /> Alerta Sonoro Ativo
            </>
          ) : (
            <>
              <VolumeX className="w-4 h-4 text-slate-500" /> Alerta Mudo
            </>
          )}
        </button>
      </div>

      {/* WARNING SUMMARY BAR */}
      <div className={`p-4 rounded-2xl mb-6 flex items-center gap-3 border ${
        unbuckledCount > 0
          ? 'bg-red-950/60 border-red-500/60 text-red-200'
          : 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
      }`}>
        {unbuckledCount > 0 ? (
          <AlertTriangle className="w-6 h-6 text-red-400 shrink-0 animate-bounce" />
        ) : (
          <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0" />
        )}
        <div className="flex-1">
          <p className="font-bold text-sm font-mono">
            {unbuckledCount > 0
              ? `ALERTA: ${unbuckledCount} PASSAGEIRO(S) SEM CINTO DE SEGURANÇA AFIVELADO!`
              : 'TODOS OS PASSAGEIROS ESTÃO COM O CINTO DE SEGURANÇA AFIVELADO'}
          </p>
          <p className="text-xs text-slate-400 font-mono mt-0.5">
            {unbuckledCount > 0
              ? `Assento(s): ${unbuckledOccupiedSeats.map((s) => s.name).join(', ')}`
              : 'Condução segura confirmada pelo sistema de bordo BYD.'}
          </p>
        </div>
      </div>

      {/* 2D VISUAL COCKPIT SEATING BLUEPRINT */}
      <div className="bg-slate-950/80 rounded-3xl p-6 border border-slate-800/80">
        <div className="text-center mb-4">
          <span className="text-xs font-mono text-slate-400 uppercase tracking-widest bg-slate-900 px-3 py-1 rounded-full border border-slate-800">
            Frente do Veículo (Painel & Volante)
          </span>
        </div>

        {/* Front Row (Driver & Passenger) */}
        <div className="grid grid-cols-2 gap-4 max-w-lg mx-auto mb-6">
          {[seatBelts.driver, seatBelts.passenger].map((seat) => (
            <div
              key={seat.id}
              className={`relative p-4 rounded-2xl border-2 flex flex-col items-center justify-between gap-3 transition-all ${
                !seat.isBuckled && seat.isOccupied
                  ? 'bg-red-950/40 border-red-500 text-red-200 shadow-lg shadow-red-950/50'
                  : seat.isBuckled
                  ? 'bg-emerald-950/30 border-emerald-500/60 text-emerald-200'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400'
              }`}
            >
              {/* Position Header */}
              <div className="flex items-center justify-between w-full">
                <span className="text-xs font-bold font-mono text-slate-200 flex items-center gap-1">
                  <User className="w-3.5 h-3.5 text-cyan-400" /> {seat.name}
                </span>
                <span className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded ${
                  seat.isOccupied ? 'bg-cyan-950 text-cyan-300 border border-cyan-800' : 'bg-slate-800 text-slate-500'
                }`}>
                  {seat.isOccupied ? 'Ocupado' : 'Livre'}
                </span>
              </div>

              {/* Seat Icon Graphic */}
              <div className="relative my-2">
                <div className={`w-16 h-16 rounded-2xl flex items-center justify-center border-2 ${
                  seat.isBuckled
                    ? 'bg-emerald-600/20 border-emerald-400 text-emerald-300'
                    : seat.isOccupied
                    ? 'bg-red-600/20 border-red-500 text-red-400 animate-pulse'
                    : 'bg-slate-800 border-slate-700 text-slate-600'
                }`}>
                  <ShieldCheck className="w-8 h-8" />
                </div>
              </div>

              {/* Status Badge & Toggles */}
              <div className="w-full space-y-2">
                <button
                  id={`btn-belt-${seat.id}`}
                  onClick={() => onToggleSeatBelt(seat.id)}
                  className={`w-full py-2 px-3 rounded-xl text-xs font-bold font-mono border transition-all ${
                    seat.isBuckled
                      ? 'bg-emerald-600 text-white border-emerald-400 hover:bg-emerald-500'
                      : 'bg-red-600 text-white border-red-400 hover:bg-red-500 animate-pulse'
                  }`}
                >
                  {seat.isBuckled ? 'AFIVELADO ✓' : 'DESATADO! CLIQUE'}
                </button>

                <button
                  onClick={() => onToggleOccupied(seat.id)}
                  className="w-full py-1 text-[11px] font-mono text-slate-400 hover:text-slate-200 underline"
                >
                  {seat.isOccupied ? 'Simular Banco Vazio' : 'Simular Sensor Peso (Ocupar)'}
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* Rear Row (3 Seats) */}
        <div className="grid grid-cols-3 gap-3 max-w-2xl mx-auto">
          {[seatBelts.rear_left, seatBelts.rear_center, seatBelts.rear_right].map((seat) => (
            <div
              key={seat.id}
              className={`p-3 rounded-2xl border-2 flex flex-col items-center justify-between gap-2 transition-all ${
                !seat.isBuckled && seat.isOccupied
                  ? 'bg-red-950/40 border-red-500 text-red-200 shadow-lg shadow-red-950/50'
                  : seat.isBuckled
                  ? 'bg-emerald-950/30 border-emerald-500/60 text-emerald-200'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400'
              }`}
            >
              <span className="text-[11px] font-bold font-mono text-slate-300 text-center">
                {seat.name}
              </span>

              <div className={`w-12 h-12 rounded-xl flex items-center justify-center border ${
                seat.isBuckled
                  ? 'bg-emerald-600/20 border-emerald-400 text-emerald-300'
                  : seat.isOccupied
                  ? 'bg-red-600/20 border-red-500 text-red-400 animate-pulse'
                  : 'bg-slate-800 border-slate-700 text-slate-600'
              }`}>
                <ShieldCheck className="w-6 h-6" />
              </div>

              <button
                id={`btn-belt-${seat.id}`}
                onClick={() => onToggleSeatBelt(seat.id)}
                className={`w-full py-1.5 px-2 rounded-lg text-[10px] font-bold font-mono border transition-all ${
                  seat.isBuckled
                    ? 'bg-emerald-600 text-white border-emerald-400'
                    : 'bg-red-600 text-white border-red-400 animate-pulse'
                }`}
              >
                {seat.isBuckled ? 'AFIVELADO' : 'DESATADO'}
              </button>
            </div>
          ))}
        </div>

        <div className="text-center mt-6">
          <span className="text-[11px] font-mono text-slate-500">
            Traseira do Veículo (Bagageiro / Porta-malas)
          </span>
        </div>
      </div>
    </div>
  );
};
