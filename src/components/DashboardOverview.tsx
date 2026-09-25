import React from 'react';
import { 
  Lock, 
  Unlock, 
  LightbulbOff, 
  ShieldCheck, 
  AlertTriangle, 
  Zap, 
  Thermometer, 
  Activity,
  Gauge,
  Sliders,
  Maximize2,
  Minimize2
} from 'lucide-react';
import { VehicleState } from '../types';

interface DashboardOverviewProps {
  vehicleState: VehicleState;
  setVehicleState: React.Dispatch<React.SetStateAction<VehicleState>>;
  onTurnOffAllLights: () => void;
  onToggleDoor: (doorKey: keyof VehicleState['doors']) => void;
  onToggleSeatbelt: (seatKey: keyof VehicleState['seatbelts']) => void;
}

export const DashboardOverview: React.FC<DashboardOverviewProps> = ({
  vehicleState,
  setVehicleState,
  onTurnOffAllLights,
  onToggleDoor,
  onToggleSeatbelt
}) => {
  const { doors, windows, seatbelts, lighting, tpms } = vehicleState;

  const anyDoorOpen = Object.values(doors).some(Boolean);
  const unbuckledOccupants = Object.values(seatbelts).some(s => s.occupied && !s.buckled);

  return (
    <div className="space-y-6">
      {/* Top Banner Alert / Status */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* Hardware Status */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className={`p-3 rounded-xl ${lighting.interiorDome ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' : 'bg-slate-800 text-slate-400'}`}>
              <LightbulbOff className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs text-slate-400 font-mono">Luzes Internas</p>
              <h4 className="text-sm font-bold text-slate-100">
                {lighting.interiorDome ? 'Ligadas' : 'Apagadas (OK)'}
              </h4>
            </div>
          </div>
          <button
            onClick={onTurnOffAllLights}
            className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-gradient-to-r from-amber-500 to-orange-600 text-slate-950 hover:brightness-110 transition shadow-md shadow-amber-950"
          >
            Apagar Tudo
          </button>
        </div>

        {/* Doors Status */}
        <div className={`border rounded-xl p-4 flex items-center justify-between ${anyDoorOpen ? 'bg-rose-950/30 border-rose-500/50 text-rose-200' : 'bg-slate-900/60 border-slate-800 text-slate-100'}`}>
          <div className="flex items-center gap-3">
            <div className={`p-3 rounded-xl ${anyDoorOpen ? 'bg-rose-500/20 text-rose-400' : 'bg-emerald-500/20 text-emerald-400'}`}>
              {anyDoorOpen ? <AlertTriangle className="w-6 h-6 animate-pulse" /> : <ShieldCheck className="w-6 h-6" />}
            </div>
            <div>
              <p className="text-xs text-slate-400 font-mono">Portas & Capô</p>
              <h4 className="text-sm font-bold">
                {anyDoorOpen ? 'Portas Abertas!' : 'Todas Fechadas'}
              </h4>
            </div>
          </div>
        </div>

        {/* Seatbelts Status */}
        <div className={`border rounded-xl p-4 flex items-center justify-between ${unbuckledOccupants ? 'bg-amber-950/40 border-amber-500/50 text-amber-200' : 'bg-slate-900/60 border-slate-800 text-slate-100'}`}>
          <div className="flex items-center gap-3">
            <div className={`p-3 rounded-xl ${unbuckledOccupants ? 'bg-amber-500/20 text-amber-400' : 'bg-emerald-500/20 text-emerald-400'}`}>
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs text-slate-400 font-mono">Cintos de Segurança</p>
              <h4 className="text-sm font-bold">
                {unbuckledOccupants ? 'Cinto Não Afivelado' : 'Todos Protegidos'}
              </h4>
            </div>
          </div>
        </div>

        {/* Battery & Range */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-3 rounded-xl bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
              <Zap className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs text-slate-400 font-mono">Bateria LFP Blade</p>
              <h4 className="text-sm font-bold text-slate-100">
                {vehicleState.batterySoc}% ({vehicleState.rangeKm} km)
              </h4>
            </div>
          </div>
        </div>
      </div>

      {/* Main Interactive Diagram & Controls */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Center: Vehicle Diagram */}
        <div className="lg:col-span-2 bg-slate-900/80 border border-slate-800 rounded-2xl p-6 relative overflow-hidden flex flex-col items-center justify-center min-h-[460px]">
          <div className="absolute top-4 left-4 flex items-center gap-2">
            <span className="text-xs font-mono font-bold text-cyan-400 bg-cyan-950/80 px-2.5 py-1 rounded-md border border-cyan-800/50">
              ESQUEMA INTERATIVO DI-LINK
            </span>
            <span className="text-[10px] text-slate-400">Clique nas portas e cintos para simular</span>
          </div>

          {/* Car Graphic Representation */}
          <div className="relative w-[280px] sm:w-[320px] h-[480px] my-6 flex items-center justify-center">
            {/* Ambient glow underneath car */}
            <div 
              className="absolute inset-4 rounded-full blur-2xl opacity-40 transition-all duration-500"
              style={{ backgroundColor: lighting.ambientLight ? lighting.ambientColor : '#0284c7' }}
            ></div>

            {/* Car Chassis Body */}
            <div className="relative w-full h-full border-2 border-slate-700/80 rounded-[60px] bg-slate-950/90 shadow-2xl flex flex-col items-center justify-between py-6 px-4">
              
              {/* Headlights Glow */}
              <div className="absolute -top-3 left-6 right-6 flex justify-between">
                <div className={`w-10 h-3 rounded-t-full transition-all duration-300 ${lighting.headlights !== 'off' ? 'bg-cyan-300 shadow-[0_0_20px_#22d3ee]' : 'bg-slate-700'}`}></div>
                <div className={`w-10 h-3 rounded-t-full transition-all duration-300 ${lighting.headlights !== 'off' ? 'bg-cyan-300 shadow-[0_0_20px_#22d3ee]' : 'bg-slate-700'}`}></div>
              </div>

              {/* Frunk / Hood */}
              <button
                onClick={() => onToggleDoor('frunk')}
                className={`w-3/4 h-16 rounded-2xl border flex flex-col items-center justify-center transition-all cursor-pointer ${
                  doors.frunk
                    ? 'bg-rose-900/50 border-rose-500 text-rose-300 shadow-lg shadow-rose-950'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                <span className="text-[10px] font-mono font-bold uppercase">Capô Dianteiro</span>
                <span className="text-xs font-semibold">{doors.frunk ? 'ABERTO' : 'FECHADO'}</span>
              </button>

              {/* Cabin Area */}
              <div className="w-full relative flex-1 border border-slate-800/80 rounded-3xl bg-slate-900/90 my-3 p-3 flex flex-col justify-between">
                
                {/* Front Row (Driver & Passenger) */}
                <div className="grid grid-cols-2 gap-4">
                  {/* Front Left Door & Seat */}
                  <div className="relative flex flex-col items-center">
                    <button
                      onClick={() => onToggleDoor('frontLeft')}
                      className={`absolute -left-6 top-1 text-[9px] font-mono px-2 py-1 rounded border transition cursor-pointer ${
                        doors.frontLeft ? 'bg-rose-600 text-white border-rose-400 shadow-md shadow-rose-900' : 'bg-slate-800 border-slate-700 text-slate-300'
                      }`}
                    >
                      Porta DE: {doors.frontLeft ? 'ABERTA' : 'FECHADA'}
                    </button>
                    <button
                      onClick={() => onToggleSeatbelt('driver')}
                      className={`w-16 h-20 rounded-xl border flex flex-col items-center justify-center gap-1 transition cursor-pointer ${
                        !seatbelts.driver.occupied
                          ? 'bg-slate-800/40 border-slate-700/50 text-slate-500'
                          : seatbelts.driver.buckled
                          ? 'bg-emerald-950/80 border-emerald-500/80 text-emerald-300 shadow-md shadow-emerald-950'
                          : 'bg-amber-950/90 border-amber-500/80 text-amber-300 animate-pulse'
                      }`}
                    >
                      <span className="text-[9px] font-bold">Motorista</span>
                      <span className="text-[10px] font-mono">{seatbelts.driver.buckled ? 'Cinto OK' : 'Sem Cinto!'}</span>
                      <span className="text-[9px] text-slate-400">Vidro: {windows.frontLeft}%</span>
                    </button>
                  </div>

                  {/* Front Right Door & Seat */}
                  <div className="relative flex flex-col items-center">
                    <button
                      onClick={() => onToggleDoor('frontRight')}
                      className={`absolute -right-6 top-1 text-[9px] font-mono px-2 py-1 rounded border transition cursor-pointer ${
                        doors.frontRight ? 'bg-rose-600 text-white border-rose-400 shadow-md shadow-rose-900' : 'bg-slate-800 border-slate-700 text-slate-300'
                      }`}
                    >
                      Porta DD: {doors.frontRight ? 'ABERTA' : 'FECHADA'}
                    </button>
                    <button
                      onClick={() => onToggleSeatbelt('passenger')}
                      className={`w-16 h-20 rounded-xl border flex flex-col items-center justify-center gap-1 transition cursor-pointer ${
                        !seatbelts.passenger.occupied
                          ? 'bg-slate-800/40 border-slate-700/50 text-slate-500'
                          : seatbelts.passenger.buckled
                          ? 'bg-emerald-950/80 border-emerald-500/80 text-emerald-300 shadow-md shadow-emerald-950'
                          : 'bg-amber-950/90 border-amber-500/80 text-amber-300 animate-pulse'
                      }`}
                    >
                      <span className="text-[9px] font-bold">Passageiro</span>
                      <span className="text-[10px] font-mono">{seatbelts.passenger.buckled ? 'Cinto OK' : 'Sem Cinto!'}</span>
                      <span className="text-[9px] text-slate-400">Vidro: {windows.frontRight}%</span>
                    </button>
                  </div>
                </div>

                {/* Center Dome Light Indicator */}
                <div className="my-2 flex justify-center">
                  <div className={`px-3 py-1 rounded-full text-[10px] font-mono font-bold flex items-center gap-1.5 border transition-all ${
                    lighting.interiorDome ? 'bg-amber-400/20 text-amber-300 border-amber-500/50 shadow-[0_0_15px_#f59e0b]' : 'bg-slate-800 text-slate-400 border-slate-700'
                  }`}>
                    <span className={`w-2 h-2 rounded-full ${lighting.interiorDome ? 'bg-amber-400 animate-ping' : 'bg-slate-600'}`}></span>
                    Luz Teto: {lighting.interiorDome ? 'LIGADA' : 'APAGADA'}
                  </div>
                </div>

                {/* Rear Row (3 Seats) */}
                <div className="grid grid-cols-3 gap-2">
                  {/* Rear Left */}
                  <div className="relative flex flex-col items-center">
                    <button
                      onClick={() => onToggleDoor('rearLeft')}
                      className={`absolute -left-6 bottom-1 text-[9px] font-mono px-2 py-1 rounded border transition cursor-pointer ${
                        doors.rearLeft ? 'bg-rose-600 text-white border-rose-400 shadow-md shadow-rose-900' : 'bg-slate-800 border-slate-700 text-slate-300'
                      }`}
                    >
                      TE: {doors.rearLeft ? 'ABERTA' : 'FECHADA'}
                    </button>
                    <button
                      onClick={() => onToggleSeatbelt('rearLeft')}
                      className={`w-full h-16 rounded-xl border flex flex-col items-center justify-center transition cursor-pointer ${
                        !seatbelts.rearLeft.occupied
                          ? 'bg-slate-800/40 border-slate-700/50 text-slate-500'
                          : seatbelts.rearLeft.buckled
                          ? 'bg-emerald-950/80 border-emerald-500/80 text-emerald-300'
                          : 'bg-amber-950/90 border-amber-500/80 text-amber-300 animate-pulse'
                      }`}
                    >
                      <span className="text-[8px] font-bold">Trás Esq</span>
                      <span className="text-[9px] font-mono">{seatbelts.rearLeft.buckled ? 'OK' : 'Alerta'}</span>
                    </button>
                  </div>

                  {/* Rear Center */}
                  <button
                    onClick={() => onToggleSeatbelt('rearCenter')}
                    className={`h-16 rounded-xl border flex flex-col items-center justify-center transition cursor-pointer ${
                      !seatbelts.rearCenter.occupied
                        ? 'bg-slate-800/40 border-slate-700/50 text-slate-500'
                        : seatbelts.rearCenter.buckled
                        ? 'bg-emerald-950/80 border-emerald-500/80 text-emerald-300'
                        : 'bg-amber-950/90 border-amber-500/80 text-amber-300 animate-pulse'
                    }`}
                  >
                    <span className="text-[8px] font-bold">Trás Meio</span>
                    <span className="text-[9px] font-mono">{seatbelts.rearCenter.buckled ? 'OK' : 'Alerta'}</span>
                  </button>

                  {/* Rear Right */}
                  <div className="relative flex flex-col items-center">
                    <button
                      onClick={() => onToggleDoor('rearRight')}
                      className={`absolute -right-6 bottom-1 text-[9px] font-mono px-2 py-1 rounded border transition cursor-pointer ${
                        doors.rearRight ? 'bg-rose-600 text-white border-rose-400 shadow-md shadow-rose-900' : 'bg-slate-800 border-slate-700 text-slate-300'
                      }`}
                    >
                      TD: {doors.rearRight ? 'ABERTA' : 'FECHADA'}
                    </button>
                    <button
                      onClick={() => onToggleSeatbelt('rearRight')}
                      className={`w-full h-16 rounded-xl border flex flex-col items-center justify-center transition cursor-pointer ${
                        !seatbelts.rearRight.occupied
                          ? 'bg-slate-800/40 border-slate-700/50 text-slate-500'
                          : seatbelts.rearRight.buckled
                          ? 'bg-emerald-950/80 border-emerald-500/80 text-emerald-300'
                          : 'bg-amber-950/90 border-amber-500/80 text-amber-300 animate-pulse'
                      }`}
                    >
                      <span className="text-[8px] font-bold">Trás Dir</span>
                      <span className="text-[9px] font-mono">{seatbelts.rearRight.buckled ? 'OK' : 'Alerta'}</span>
                    </button>
                  </div>
                </div>
              </div>

              {/* Trunk / Tailgate */}
              <button
                onClick={() => onToggleDoor('trunk')}
                className={`w-3/4 h-16 rounded-2xl border flex flex-col items-center justify-center transition-all cursor-pointer ${
                  doors.trunk
                    ? 'bg-rose-900/50 border-rose-500 text-rose-300 shadow-lg shadow-rose-950'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                <span className="text-[10px] font-mono font-bold uppercase">Porta-Malas Traseiro</span>
                <span className="text-xs font-semibold">{doors.trunk ? 'ABERTO' : 'FECHADO'}</span>
              </button>

              {/* Tail Lights */}
              <div className="absolute -bottom-3 left-6 right-6 flex justify-between">
                <div className="w-10 h-3 bg-red-600 rounded-b-full shadow-[0_0_15px_#dc2626]"></div>
                <div className="w-10 h-3 bg-red-600 rounded-b-full shadow-[0_0_15px_#dc2626]"></div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Tire Pressures & Direct Control Commands */}
        <div className="space-y-6">
          {/* TPMS Sensors Box */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-5">
            <h3 className="text-sm font-bold text-slate-200 mb-4 flex items-center gap-2">
              <Activity className="w-4 h-4 text-cyan-400" />
              Pressão dos Pneus (TPMS)
            </h3>

            <div className="grid grid-cols-2 gap-3">
              <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                <p className="text-[10px] text-slate-400 font-mono">Dianteiro Esquerdo</p>
                <p className="text-lg font-mono font-bold text-cyan-300">{tpms.frontLeft.psi} PSI</p>
                <p className="text-[10px] text-slate-500">{tpms.frontLeft.tempC}°C • Normal</p>
              </div>

              <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                <p className="text-[10px] text-slate-400 font-mono">Dianteiro Direito</p>
                <p className="text-lg font-mono font-bold text-cyan-300">{tpms.frontRight.psi} PSI</p>
                <p className="text-[10px] text-slate-500">{tpms.frontRight.tempC}°C • Normal</p>
              </div>

              <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                <p className="text-[10px] text-slate-400 font-mono">Traseiro Esquerdo</p>
                <p className="text-lg font-mono font-bold text-cyan-300">{tpms.rearLeft.psi} PSI</p>
                <p className="text-[10px] text-slate-500">{tpms.rearLeft.tempC}°C • Normal</p>
              </div>

              <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                <p className="text-[10px] text-slate-400 font-mono">Traseiro Direito</p>
                <p className="text-lg font-mono font-bold text-cyan-300">{tpms.rearRight.psi} PSI</p>
                <p className="text-[10px] text-slate-500">{tpms.rearRight.tempC}°C • Normal</p>
              </div>
            </div>
          </div>

          {/* Rapid Action Panel */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-5 space-y-3">
            <h3 className="text-sm font-bold text-slate-200 mb-2 flex items-center gap-2">
              <Sliders className="w-4 h-4 text-cyan-400" />
              Comandos Rápidos DiLink
            </h3>

            <button
              onClick={onTurnOffAllLights}
              className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 font-bold text-xs flex items-center justify-between shadow-lg shadow-amber-950/50 hover:brightness-110 transition"
            >
              <span>Apagar Luzes Internas (Hard Confirm)</span>
              <LightbulbOff className="w-4 h-4" />
            </button>

            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    windows: { frontLeft: 0, frontRight: 0, rearLeft: 0, rearRight: 0, sunroof: 0 }
                  }));
                }}
                className="py-2 px-3 rounded-xl bg-slate-800 border border-slate-700 text-slate-200 text-xs font-semibold hover:bg-slate-700 transition flex items-center justify-center gap-1.5"
              >
                <Minimize2 className="w-3.5 h-3.5 text-cyan-400" />
                Fechar Vidros
              </button>

              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    windows: { frontLeft: 30, frontRight: 30, rearLeft: 30, rearRight: 30, sunroof: 20 }
                  }));
                }}
                className="py-2 px-3 rounded-xl bg-slate-800 border border-slate-700 text-slate-200 text-xs font-semibold hover:bg-slate-700 transition flex items-center justify-center gap-1.5"
              >
                <Maximize2 className="w-3.5 h-3.5 text-cyan-400" />
                Ventilar Vidros (30%)
              </button>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    doors: { frontLeft: false, frontRight: false, rearLeft: false, rearRight: false, trunk: false, frunk: false }
                  }));
                }}
                className="py-2 px-3 rounded-xl bg-slate-800 border border-slate-700 text-slate-200 text-xs font-semibold hover:bg-slate-700 transition flex items-center justify-center gap-1.5"
              >
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                Fechar Portas
              </button>

              <button
                onClick={() => {
                  setVehicleState(prev => ({
                    ...prev,
                    driveMode: prev.driveMode === 'ECO' ? 'SPORT' : prev.driveMode === 'SPORT' ? 'SNOW' : 'ECO'
                  }));
                }}
                className="py-2 px-3 rounded-xl bg-slate-800 border border-slate-700 text-slate-200 text-xs font-semibold hover:bg-slate-700 transition flex items-center justify-center gap-1.5"
              >
                <Gauge className="w-3.5 h-3.5 text-purple-400" />
                Modo: {vehicleState.driveMode}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
