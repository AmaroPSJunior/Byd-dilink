import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  Lightbulb,
  Power,
  Sun,
  Palette,
  Timer,
  CheckCircle2,
  AlertTriangle,
  Zap,
  Sparkles,
  Sliders,
  EyeOff
} from 'lucide-react';
import { InternalLightState } from '../types';

interface CentralLightButtonProps {
  lightsState: InternalLightState;
  onToggleMasterLights: (turnOffAll: boolean) => void;
  onUpdateLightSubState: (updates: Partial<InternalLightState>) => void;
  onLogDiLinkAction: (actionName: string, javaCall: string, intent: string, params: Record<string, any>) => void;
}

const AMBIENT_COLORS = [
  { name: 'Azul BYD', hex: '#00d2ff' },
  { name: 'Verde Esmeralda', hex: '#10b981' },
  { name: 'Roxo Neón', hex: '#a855f7' },
  { name: 'Âmbar Quente', hex: '#f59e0b' },
  { name: 'Vermelho Esporte', hex: '#ef4444' },
  { name: 'Cian DiLink', hex: '#06b6d4' },
  { name: 'Branco Puro', hex: '#ffffff' },
];

export const CentralLightButton: React.FC<CentralLightButtonProps> = ({
  lightsState,
  onToggleMasterLights,
  onUpdateLightSubState,
  onLogDiLinkAction
}) => {
  const [isPressing, setIsPressing] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const handleMasterClick = () => {
    const newState = !lightsState.masterState;
    setIsPressing(true);
    setTimeout(() => setIsPressing(false), 300);

    onToggleMasterLights(!newState); // If currently ON, turn OFF (turnOffAll = true)

    const msg = newState
      ? 'Luzes internas ligadas!'
      : '⚡ TODAS AS LUZES INTERNAS FORAM APAGADAS COM SUCESSO!';
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);

    onLogDiLinkAction(
      newState ? 'LIGAR_LUZES_INTERNAS' : 'APAGAR_TODAS_LUZES_INTERNAS',
      newState
        ? 'BYDAutoLightBus.getInstance(context).setReadingLightState(LIGHT_ALL, STATE_ON);'
        : 'BYDAutoLightBus.getInstance(context).setReadingLightState(LIGHT_ALL, STATE_OFF); BYDAutoLightBus.getInstance(context).setAmbientLightState(STATE_OFF);',
      'com.byd.action.LIGHT_CONTROL',
      { command: newState ? 'MASTER_ON' : 'MASTER_OFF', target: 'ALL_INTERNAL_LIGHTS', value: newState ? 100 : 0 }
    );
  };

  const handleSubToggle = (key: keyof InternalLightState, value: boolean, label: string) => {
    onUpdateLightSubState({ [key]: value });
    onLogDiLinkAction(
      `ALTERAR_${label.toUpperCase().replace(/\s+/g, '_')}`,
      `BYDAutoLightBus.getInstance(context).setSubLightState("${key}", ${value ? 1 : 0});`,
      'com.byd.action.LIGHT_SUB_CONTROL',
      { targetKey: key, newValue: value }
    );
  };

  return (
    <div className="flex flex-col items-center justify-center py-6 px-4">
      {/* Toast Notification Alert */}
      <AnimatePresence>
        {toastMessage && (
          <motion.div
            initial={{ opacity: 0, y: -20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.95 }}
            className={`mb-6 px-6 py-3 rounded-2xl flex items-center gap-3 shadow-xl border ${
              lightsState.masterState
                ? 'bg-amber-950/90 text-amber-200 border-amber-500/50'
                : 'bg-emerald-950/90 text-emerald-200 border-emerald-500/50'
            }`}
          >
            {lightsState.masterState ? (
              <Lightbulb className="w-5 h-5 text-amber-400" />
            ) : (
              <EyeOff className="w-5 h-5 text-emerald-400" />
            )}
            <span className="font-semibold text-sm font-mono">{toastMessage}</span>
          </motion.div>
        )}
      </AnimatePresence>

      {/* CENTRAL HERO BUTTON SECTION */}
      <div className="relative flex flex-col items-center my-4">
        {/* Outer Glowing Ambient Ring */}
        <div
          className={`absolute -inset-6 rounded-full transition-all duration-700 blur-2xl opacity-60 ${
            lightsState.masterState
              ? 'bg-gradient-to-r from-amber-500 via-orange-500 to-yellow-400 animate-pulse'
              : 'bg-slate-800 opacity-20'
          }`}
          style={{
            backgroundColor: lightsState.masterState ? lightsState.ambientColor : undefined
          }}
        />

        {/* Outer Circular Ring Frame */}
        <div className="relative p-3 rounded-full bg-slate-900 border-2 border-slate-800/80 shadow-2xl">
          {/* Main Giant Central Button */}
          <motion.button
            id="btn-master-lights-off"
            onClick={handleMasterClick}
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.95 }}
            className={`relative w-64 h-64 sm:w-72 sm:h-72 rounded-full flex flex-col items-center justify-center p-6 text-center cursor-pointer transition-all duration-500 border-4 shadow-2xl select-none ${
              lightsState.masterState
                ? 'bg-gradient-to-br from-amber-500 via-orange-600 to-amber-700 border-amber-300/80 text-white shadow-amber-500/40'
                : 'bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 border-cyan-500/50 text-slate-200 shadow-cyan-950/80 hover:border-cyan-400'
            }`}
          >
            {/* Center Animated Icon */}
            <div className="relative mb-3">
              <div
                className={`p-5 rounded-full transition-all duration-500 ${
                  lightsState.masterState
                    ? 'bg-white/20 text-white shadow-inner ring-4 ring-white/30'
                    : 'bg-slate-800/90 text-cyan-400 border border-slate-700 shadow-inner'
                }`}
              >
                <Power className={`w-12 h-12 sm:w-14 sm:h-14 transition-transform duration-300 ${isPressing ? 'scale-90' : ''}`} />
              </div>
              
              {/* Secondary Status Badge */}
              <div
                className={`absolute -bottom-1 -right-1 p-1.5 rounded-full text-xs font-bold border ${
                  lightsState.masterState
                    ? 'bg-amber-300 text-amber-950 border-white'
                    : 'bg-emerald-500 text-slate-950 border-emerald-300'
                }`}
              >
                {lightsState.masterState ? (
                  <Lightbulb className="w-4 h-4 animate-bounce" />
                ) : (
                  <EyeOff className="w-4 h-4" />
                )}
              </div>
            </div>

            {/* Label and Status */}
            <span className="text-base sm:text-lg font-black tracking-wider uppercase font-mono leading-tight">
              {lightsState.masterState
                ? 'APAGAR TODAS AS LUZES'
                : 'LUZES APAGADAS'}
            </span>
            <span className="text-[11px] mt-1 font-semibold opacity-90 tracking-wide font-mono px-3 py-1 rounded-full bg-black/30 border border-white/10">
              {lightsState.masterState
                ? 'Toque para apagar cabine'
                : 'Modo Noturno Ativo'}
            </span>
          </motion.button>
        </div>

        {/* Quick Sub-label Under Central Button */}
        <p className="mt-4 text-xs font-mono text-slate-400 flex items-center gap-1.5 bg-slate-900/80 px-4 py-1.5 rounded-full border border-slate-800">
          <Zap className="w-3.5 h-3.5 text-cyan-400" />
          <span>Atalho Direto para DiLink CAN Bus API (`com.byd.service.BYDAutoLightBus`)</span>
        </p>
      </div>

      {/* DETAILED INTERNAL LIGHT CONTROL DECK */}
      <div className="w-full max-w-4xl mt-6 bg-slate-900/80 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-xl">
        <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <Sliders className="w-5 h-5 text-cyan-400" />
            <h3 className="font-bold text-slate-200 text-sm font-mono tracking-wide uppercase">
              Controle Individual por Zona de Cabine
            </h3>
          </div>
          <span className="text-xs font-mono text-slate-400 bg-slate-950 px-3 py-1 rounded-full border border-slate-800">
            Intensidade: {lightsState.brightnessPercentage}%
          </span>
        </div>

        {/* Zonal Light Toggles */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
          {[
            { key: 'driverReadingLight', label: 'Luz Leitura Motorista', active: lightsState.driverReadingLight },
            { key: 'passengerReadingLight', label: 'Luz Leitura Passageiro', active: lightsState.passengerReadingLight },
            { key: 'rearReadingLight', label: 'Luz Leitura Traseira', active: lightsState.rearReadingLight },
            { key: 'footwellLight', label: 'Luz Cortesia Pés', active: lightsState.footwellLight },
          ].map((item) => (
            <button
              key={item.key}
              onClick={() => handleSubToggle(item.key as keyof InternalLightState, !item.active, item.label)}
              className={`p-3 rounded-2xl flex flex-col items-start justify-between border transition-all text-left ${
                item.active && lightsState.masterState
                  ? 'bg-amber-950/40 border-amber-500/50 text-amber-200'
                  : 'bg-slate-950/60 border-slate-800 text-slate-400 hover:border-slate-700'
              }`}
            >
              <div className="flex items-center justify-between w-full mb-2">
                <Lightbulb className={`w-4 h-4 ${item.active && lightsState.masterState ? 'text-amber-400' : 'text-slate-600'}`} />
                <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded ${
                  item.active && lightsState.masterState ? 'bg-amber-400/20 text-amber-300' : 'bg-slate-800 text-slate-500'
                }`}>
                  {item.active && lightsState.masterState ? 'LIGADA' : 'DESLIGADA'}
                </span>
              </div>
              <span className="text-xs font-semibold font-mono text-slate-300">{item.label}</span>
            </button>
          ))}
        </div>

        {/* Ambient Light & Brightness Controls */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-slate-800/80">
          
          {/* Brightness Slider */}
          <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-800/80">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-mono font-semibold text-slate-300 flex items-center gap-1.5">
                <Sun className="w-4 h-4 text-amber-400" /> Brilho das Luzes Internas
              </span>
              <span className="text-xs font-mono font-bold text-cyan-400">
                {lightsState.brightnessPercentage}%
              </span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={lightsState.brightnessPercentage}
              onChange={(e) => {
                const val = parseInt(e.target.value, 10);
                onUpdateLightSubState({ brightnessPercentage: val });
                if (val === 0 && lightsState.masterState) {
                  onToggleMasterLights(true);
                }
              }}
              className="w-full accent-cyan-400 h-2 bg-slate-800 rounded-lg cursor-pointer"
            />
          </div>

          {/* Ambient Light Color Palette */}
          <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-800/80">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-mono font-semibold text-slate-300 flex items-center gap-1.5">
                <Palette className="w-4 h-4 text-purple-400" /> Cor da Luz de Ambiente
              </span>
              <span className="text-xs font-mono text-slate-400 font-bold">
                RGB DiLink
              </span>
            </div>
            <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
              {AMBIENT_COLORS.map((c) => (
                <button
                  key={c.hex}
                  onClick={() => {
                    onUpdateLightSubState({ ambientColor: c.hex, ambientLight: true });
                    onLogDiLinkAction(
                      'ALTERAR_COR_LUS_AMBIENTE',
                      `BYDAutoLightBus.getInstance(context).setAmbientLightColor("${c.hex}");`,
                      'com.byd.action.AMBIENT_COLOR',
                      { colorHex: c.hex }
                    );
                  }}
                  className={`w-7 h-7 rounded-full transition-transform border-2 flex items-center justify-center shrink-0 ${
                    lightsState.ambientColor === c.hex ? 'scale-125 border-white shadow-lg' : 'border-transparent hover:scale-110'
                  }`}
                  style={{ backgroundColor: c.hex }}
                  title={c.name}
                />
              ))}
            </div>
          </div>

        </div>
      </div>
    </div>
  );
};
