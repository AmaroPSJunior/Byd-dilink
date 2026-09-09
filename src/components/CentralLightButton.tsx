import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  Lightbulb,
  Power,
  Sun,
  Palette,
  EyeOff,
  Radio,
  Sliders,
  CheckCircle2,
  AlertTriangle,
  MonitorOff,
  Cpu,
  RefreshCw,
  Info,
  Layers,
  ChevronDown,
  ChevronUp
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

// Helper to play synthesized click feedback audio
function playLatchingRelayChime(isTurnOff: boolean) {
  try {
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = isTurnOff ? 'triangle' : 'sine';
    osc.frequency.setValueAtTime(isTurnOff ? 320 : 540, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(isTurnOff ? 120 : 880, ctx.currentTime + 0.12);

    gain.gain.setValueAtTime(0.2, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.14);

    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.15);
  } catch {
    // AudioContext blocked or not supported
  }
}

export const CentralLightButton: React.FC<CentralLightButtonProps> = ({
  lightsState,
  onToggleMasterLights,
  onUpdateLightSubState,
  onLogDiLinkAction
}) => {
  const [isPressing, setIsPressing] = useState(false);
  const [toastMessage, setToastMessage] = useState<{ text: string; success: boolean } | null>(null);
  const [activeDispatchMethod, setActiveDispatchMethod] = useState<'ALL_5_METHODS' | 'HAL_NATIVE' | 'INTENT_BROADCAST' | 'CAR_PROPERTY' | 'SETTINGS_SYSTEM'>('ALL_5_METHODS');
  const [showTroubleshooter, setShowTroubleshooter] = useState(false);
  const [lastDispatchedTimestamp, setLastDispatchedTimestamp] = useState<string | null>(null);
  const [screenOffSimulated, setScreenOffSimulated] = useState(false);

  // Unconditional FORCE OFF: Always turns off every single light
  const handleForceTurnOffAll = () => {
    setIsPressing(true);
    playLatchingRelayChime(true);
    setTimeout(() => setIsPressing(false), 250);

    onToggleMasterLights(true); // force turnOffAll = true
    setLastDispatchedTimestamp(new Date().toLocaleTimeString());

    setToastMessage({
      text: '⚡ COMANDO FORÇADO: TODAS AS LUZES INTERNAS FORAM APAGADAS COM SUCESSO!',
      success: true
    });
    setTimeout(() => setToastMessage(null), 4000);

    // Multi-method dispatch log for DiLink
    onLogDiLinkAction(
      'FORCAR_APAGAR_TODAS_LUZES_INTERNAS',
      `// DISPARO MULTIVIAS BYD DILINK
1. android.hardware.bydauto.light.BYDAutoLightDevice.getInstance(context).setReadingLight(0, 0);
2. android.hardware.bydauto.light.BYDAutoLightDevice.getInstance(context).setAmbientLightSwitch(0);
3. Settings.System.putInt(context.contentResolver, "auto_dome_light", 0);
4. Settings.System.putInt(context.contentResolver, "byd_ambient_light_switch", 0);
5. CarPropertyManager.setIntProperty(VehiclePropertyIds.CABIN_LIGHTS_SWITCH, 0, 1);`,
      'com.byd.action.CONTROL_LIGHTS',
      {
        target: 'ALL_INTERIOR_LIGHTS',
        action: 'FORCE_OFF',
        readingLight: 0,
        ambientLight: 0,
        autoDomeLight: 0,
        methodsDispatched: ['BYDAutoLightDevice_HAL', 'DiLink_Broadcasts', 'Settings_System', 'CarPropertyManager_AAOS', 'Shell_Fallback']
      }
    );
  };

  // Turn ON lights intentionally
  const handleTurnOnAll = () => {
    setIsPressing(true);
    playLatchingRelayChime(false);
    setTimeout(() => setIsPressing(false), 250);

    onToggleMasterLights(false); // force turnOffAll = false
    setLastDispatchedTimestamp(new Date().toLocaleTimeString());

    setToastMessage({
      text: '💡 Luzes de cabine ligadas com sucesso!',
      success: true
    });
    setTimeout(() => setToastMessage(null), 3000);

    onLogDiLinkAction(
      'LIGAR_LUZES_INTERNAS',
      'android.hardware.bydauto.light.BYDAutoLightDevice.getInstance(context).setReadingLight(0, 1); BYDAutoLightDevice.getInstance(context).setAmbientLightSwitch(1);',
      'com.byd.action.LIGHT_CONTROL',
      { target: 'ALL_INTERIOR_LIGHTS', action: 'TURN_ON', brightness: 80 }
    );
  };

  // Blackout Mode (Luzes apagadas + Tela Multimídia DiLink apagada)
  const handleFullBlackout = () => {
    handleForceTurnOffAll();
    setScreenOffSimulated(true);

    onLogDiLinkAction(
      'MODO_BLACKOUT_CABINE_TOTAL',
      `// Apagar luzes de cabine + Tela DiLink
BYDAutoLightDevice.getInstance(context).setReadingLight(0, 0);
context.sendBroadcast(Intent("com.byd.action.SCREEN_OFF"));
Settings.System.putInt(context.contentResolver, "screen_off_timeout", 1000);`,
      'com.byd.action.SCREEN_OFF',
      { mode: 'CABIN_BLACKOUT', lights: 'OFF', screen: 'OFF' }
    );

    setToastMessage({
      text: '🌙 Modo Noturno Blackout ativado! Luzes de cabine e tela apagadas.',
      success: true
    });
    setTimeout(() => setToastMessage(null), 4000);
  };

  // Sub light individual toggle
  const handleSubToggle = (key: keyof InternalLightState, currentValue: boolean, label: string) => {
    const nextValue = !currentValue;
    playLatchingRelayChime(!nextValue);

    // If turning on an individual light, masterState should be true
    // If turning off, check if all others are off
    const newUpdates: Partial<InternalLightState> = {
      [key]: nextValue
    };

    if (nextValue) {
      newUpdates.masterState = true;
    }

    onUpdateLightSubState(newUpdates);

    onLogDiLinkAction(
      `ALTERAR_${label.toUpperCase().replace(/\s+/g, '_')}`,
      `BYDAutoLightDevice.getInstance(context).setSubLightState("${key}", ${nextValue ? 1 : 0});`,
      'com.byd.action.LIGHT_SUB_CONTROL',
      { targetKey: key, newValue: nextValue }
    );
  };

  return (
    <div className="flex flex-col items-center justify-center py-6 px-4">
      {/* Toast Alert Banner */}
      <AnimatePresence>
        {toastMessage && (
          <motion.div
            initial={{ opacity: 0, y: -20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.95 }}
            className={`mb-6 px-6 py-3.5 rounded-2xl flex items-center gap-3 shadow-2xl border backdrop-blur-md max-w-2xl text-center ${
              toastMessage.success
                ? 'bg-emerald-950/95 text-emerald-200 border-emerald-500/60'
                : 'bg-amber-950/95 text-amber-200 border-amber-500/60'
            }`}
          >
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
            <span className="font-semibold text-xs sm:text-sm font-mono tracking-tight">
              {toastMessage.text}
            </span>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Screen Off Simulation Banner if active */}
      <AnimatePresence>
        {screenOffSimulated && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            className="mb-6 p-4 rounded-2xl bg-slate-900 border-2 border-cyan-500/70 shadow-2xl flex items-center justify-between gap-4 max-w-xl w-full"
          >
            <div className="flex items-center gap-3">
              <MonitorOff className="w-6 h-6 text-cyan-400" />
              <div>
                <p className="text-sm font-bold text-slate-100 font-mono">Modo Blackout de Tela Ativo</p>
                <p className="text-xs text-slate-400">Sinal de tela desligada (`com.byd.action.SCREEN_OFF`) enviado ao DiLink.</p>
              </div>
            </div>
            <button
              onClick={() => setScreenOffSimulated(false)}
              className="px-3 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold font-mono transition"
            >
              Religar Tela
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* HERO SECTION - UNMISTAKABLE BUTTONS */}
      <div className="relative flex flex-col items-center my-2 w-full max-w-xl">
        {/* Glow halo */}
        <div
          className={`absolute -inset-4 rounded-full transition-all duration-700 blur-2xl opacity-50 ${
            lightsState.masterState
              ? 'bg-gradient-to-r from-amber-500 via-orange-500 to-yellow-400'
              : 'bg-emerald-600/30'
          }`}
        />

        {/* MAIN GIANT APAGAR A LUZ BUTTON */}
        <div className="relative p-3 rounded-full bg-slate-900 border-2 border-slate-800 shadow-2xl mb-6">
          <motion.button
            id="btn-force-turn-off-lights"
            onClick={handleForceTurnOffAll}
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.95 }}
            className={`relative w-64 h-64 sm:w-72 sm:h-72 rounded-full flex flex-col items-center justify-center p-6 text-center cursor-pointer transition-all duration-500 border-4 shadow-2xl select-none ${
              lightsState.masterState
                ? 'bg-gradient-to-br from-red-600 via-rose-700 to-amber-700 border-red-400 text-white shadow-red-500/40 hover:brightness-110'
                : 'bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 border-emerald-500/60 text-slate-100 shadow-emerald-950/60'
            }`}
          >
            {/* Center Animated Icon */}
            <div className="relative mb-3">
              <div
                className={`p-5 rounded-full transition-all duration-300 ${
                  lightsState.masterState
                    ? 'bg-white/20 text-white shadow-inner ring-4 ring-white/30'
                    : 'bg-emerald-950/80 text-emerald-400 border border-emerald-500/40'
                }`}
              >
                <Power className={`w-12 h-12 sm:w-14 sm:h-14 ${isPressing ? 'scale-90' : ''}`} />
              </div>

              <div
                className={`absolute -bottom-1 -right-1 p-1.5 rounded-full text-xs font-bold border ${
                  lightsState.masterState
                    ? 'bg-amber-400 text-slate-950 border-white'
                    : 'bg-emerald-500 text-slate-950 border-emerald-200'
                }`}
              >
                {lightsState.masterState ? (
                  <Lightbulb className="w-4 h-4 animate-bounce" />
                ) : (
                  <EyeOff className="w-4 h-4" />
                )}
              </div>
            </div>

            {/* Clear, Unambiguous Action Text */}
            <span className="text-lg sm:text-xl font-black tracking-wider uppercase font-mono leading-tight">
              APAGAR TODAS AS LUZES
            </span>

            <span className="text-[11px] mt-1.5 font-bold tracking-wide font-mono px-3.5 py-1 rounded-full bg-black/40 border border-white/20">
              {lightsState.masterState ? 'Toque para Forçar Blackout' : '✓ Luzes Apagadas (Tocar para reforçar)'}
            </span>
          </motion.button>
        </div>

        {/* ADJACENT QUICK ACTIONS BAR */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full">
          {/* Religar Luzes de Cabine */}
          <button
            id="btn-turn-on-lights"
            onClick={handleTurnOnAll}
            className="flex items-center justify-center gap-2.5 px-4 py-3 rounded-2xl bg-slate-900/90 hover:bg-slate-800 border border-amber-500/40 text-amber-300 text-xs sm:text-sm font-bold font-mono transition-all shadow-lg active:scale-95"
          >
            <Sun className="w-4 h-4 text-amber-400" />
            <span>LIGAR LUZES DE CABINE</span>
          </button>

          {/* Blackout Total (Luzes + Tela) */}
          <button
            id="btn-blackout-screen-lights"
            onClick={handleFullBlackout}
            className="flex items-center justify-center gap-2.5 px-4 py-3 rounded-2xl bg-slate-900/90 hover:bg-slate-800 border border-cyan-500/40 text-cyan-300 text-xs sm:text-sm font-bold font-mono transition-all shadow-lg active:scale-95"
          >
            <MonitorOff className="w-4 h-4 text-cyan-400" />
            <span>BLACKOUT TOTAL (LUZES + TELA)</span>
          </button>
        </div>

        {/* Dispatch Status Pill */}
        <div className="mt-3 flex items-center gap-2 text-[11px] font-mono text-slate-400 bg-slate-900/80 px-4 py-1.5 rounded-full border border-slate-800">
          <Radio className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
          <span>
            {lastDispatchedTimestamp
              ? `Último comando CAN transmitido às ${lastDispatchedTimestamp}`
              : 'CAN Bus pronto para transmissão de sinal de corte'}
          </span>
        </div>
      </div>

      {/* WHY DID IT NOT TURN OFF? QUICK TROUBLESHOOTING GUIDE FOR BYD CARS */}
      <div className="w-full max-w-4xl mt-4 bg-slate-900/80 rounded-3xl p-5 border border-slate-800 shadow-xl">
        <button
          onClick={() => setShowTroubleshooter(!showTroubleshooter)}
          className="flex items-center justify-between w-full text-left"
        >
          <div className="flex items-center gap-2.5">
            <Info className="w-5 h-5 text-cyan-400" />
            <div>
              <h4 className="font-bold text-slate-200 text-xs sm:text-sm font-mono tracking-wide uppercase">
                Não apagou no seu BYD? Entenda o funcionamento do DiLink
              </h4>
              <p className="text-[11px] text-slate-400 font-mono">
                Diferença entre sensores táteis de teto e barramento elétrico CAN do Dolphin, Song, Seal e Yuan.
              </p>
            </div>
          </div>
          {showTroubleshooter ? (
            <ChevronUp className="w-5 h-5 text-slate-400" />
          ) : (
            <ChevronDown className="w-5 h-5 text-slate-400" />
          )}
        </button>

        <AnimatePresence>
          {showTroubleshooter && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="mt-4 pt-4 border-t border-slate-800 text-xs font-mono text-slate-300 space-y-3"
            >
              <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800">
                <p className="font-bold text-amber-300 flex items-center gap-1.5 mb-1">
                  <AlertTriangle className="w-4 h-4 text-amber-400" />
                  1. Interruptores Capacitivos Físicos no Teto (Dolphin / Song / Yuan)
                </p>
                <p className="text-slate-400 leading-relaxed">
                  Em vários modelos BYD (ex: Dolphin GS, Song Plus DM-i, Seal), as luzes de leitura dianteiras individuais possuem um sensor de toque de vidro no próprio plafonier. Se alguém tocou com o dedo na lâmpada física, o circuito eletrônico local mantém o relé fechado por segurança. O botão central desliga o modo automático (<span className="text-cyan-300">Auto Dome Light</span>), as fitas LED ambiente e as luzes traseiras.
                </p>
              </div>

              <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800">
                <p className="font-bold text-cyan-300 flex items-center gap-1.5 mb-1">
                  <Layers className="w-4 h-4 text-cyan-400" />
                  2. Disparo por 5 Vias Simultâneas Implementado no Applet e APK
                </p>
                <p className="text-slate-400 leading-relaxed">
                  Para contornar variações de firmware (DiLink 3.0, 4.0, 5.0 internacional ou nacional), este aplicativo envia simultaneamente:
                </p>
                <ul className="list-disc list-inside mt-1.5 text-slate-400 space-y-1">
                  <li><strong className="text-slate-200">Via 1 - HAL Nativo BYD:</strong> <code className="text-emerald-300">android.hardware.bydauto.light.BYDAutoLightDevice</code></li>
                  <li><strong className="text-slate-200">Via 2 - Multi-Intents DiLink:</strong> <code className="text-emerald-300">com.byd.action.CONTROL_LIGHTS</code> e <code className="text-emerald-300">LIGHT_CONTROL</code></li>
                  <li><strong className="text-slate-200">Via 3 - Provedor de Configuração:</strong> <code className="text-emerald-300">Settings.System (auto_dome_light = 0)</code></li>
                  <li><strong className="text-slate-200">Via 4 - Android Automotive:</strong> <code className="text-emerald-300">CarPropertyManager (CABIN_LIGHTS_SWITCH = 1)</code></li>
                  <li><strong className="text-slate-200">Via 5 - Root / Shell Fallback:</strong> <code className="text-emerald-300">service call byd_light 1 i32 0</code></li>
                </ul>
              </div>

              <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                <div>
                  <p className="font-bold text-slate-200">Seletor de Método de Envio</p>
                  <p className="text-[11px] text-slate-400">Padrão recomendado: Todas as 5 Vias Simultâneas</p>
                </div>
                <div className="flex gap-1.5">
                  {(['ALL_5_METHODS', 'HAL_NATIVE', 'INTENT_BROADCAST'] as const).map((method) => (
                    <button
                      key={method}
                      onClick={() => setActiveDispatchMethod(method)}
                      className={`px-2.5 py-1 rounded-lg text-[10px] font-bold border transition ${
                        activeDispatchMethod === method
                          ? 'bg-cyan-500 text-slate-950 border-cyan-400'
                          : 'bg-slate-900 text-slate-400 border-slate-800 hover:border-slate-700'
                      }`}
                    >
                      {method === 'ALL_5_METHODS' ? 'Todas as 5' : method === 'HAL_NATIVE' ? 'HAL Nativo' : 'Broadcasts'}
                    </button>
                  ))}
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* DETAILED INTERNAL LIGHT CONTROL DECK */}
      <div className="w-full max-w-4xl mt-6 bg-slate-900/80 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-xl">
        <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <Sliders className="w-5 h-5 text-cyan-400" />
            <h3 className="font-bold text-slate-200 text-sm font-mono tracking-wide uppercase">
              Controle Individual por Lâmpada e Zona
            </h3>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono text-slate-400 bg-slate-950 px-3 py-1 rounded-full border border-slate-800">
              Brilho Global: {lightsState.brightnessPercentage}%
            </span>
          </div>
        </div>

        {/* Zonal Light Individual Controls */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-6">
          {[
            { key: 'driverReadingLight', label: 'Motorista (FL)', active: lightsState.driverReadingLight },
            { key: 'passengerReadingLight', label: 'Passageiro (FR)', active: lightsState.passengerReadingLight },
            { key: 'rearReadingLight', label: 'Traseira (RR)', active: lightsState.rearReadingLight },
            { key: 'footwellLight', label: 'Luz dos Pés', active: lightsState.footwellLight },
            { key: 'ambientLight', label: 'Fita LED RGB', active: lightsState.ambientLight },
          ].map((item) => (
            <button
              key={item.key}
              onClick={() => handleSubToggle(item.key as keyof InternalLightState, item.active, item.label)}
              className={`p-3.5 rounded-2xl flex flex-col items-start justify-between border transition-all text-left ${
                item.active
                  ? 'bg-amber-950/40 border-amber-500/60 text-amber-200 shadow-lg shadow-amber-950/30'
                  : 'bg-slate-950/80 border-slate-800 text-slate-400 hover:border-slate-700'
              }`}
            >
              <div className="flex items-center justify-between w-full mb-2">
                <Lightbulb className={`w-4 h-4 ${item.active ? 'text-amber-400' : 'text-slate-600'}`} />
                <span className={`text-[9px] uppercase font-mono font-bold px-1.5 py-0.5 rounded ${
                  item.active ? 'bg-amber-400/20 text-amber-300' : 'bg-slate-800 text-slate-500'
                }`}>
                  {item.active ? 'LIGADA' : 'DESLIGADA'}
                </span>
              </div>
              <span className="text-xs font-semibold font-mono text-slate-200">{item.label}</span>
              <span className="text-[10px] text-slate-500 font-mono mt-0.5">
                {item.active ? 'Toque p/ apagar' : 'Toque p/ acender'}
              </span>
            </button>
          ))}
        </div>

        {/* Ambient Light & Brightness Controls */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-slate-800/80">
          {/* Brightness Slider */}
          <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-800/80">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-mono font-semibold text-slate-300 flex items-center gap-1.5">
                <Sun className="w-4 h-4 text-amber-400" /> Regulador de Brilho de Cabine
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
                if (val === 0) {
                  handleForceTurnOffAll();
                } else if (!lightsState.masterState && val > 0) {
                  onUpdateLightSubState({ masterState: true });
                }
              }}
              className="w-full accent-cyan-400 h-2 bg-slate-800 rounded-lg cursor-pointer"
            />
            <div className="flex justify-between text-[10px] font-mono text-slate-500 mt-1">
              <span>0% (Apagado)</span>
              <span>50%</span>
              <span>100% (Máximo)</span>
            </div>
          </div>

          {/* Ambient Light Color Palette */}
          <div className="bg-slate-950/60 p-4 rounded-2xl border border-slate-800/80">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-mono font-semibold text-slate-300 flex items-center gap-1.5">
                <Palette className="w-4 h-4 text-purple-400" /> Cor da Fita LED de Ambiente
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
                    onUpdateLightSubState({ ambientColor: c.hex, ambientLight: true, masterState: true });
                    onLogDiLinkAction(
                      'ALTERAR_COR_LUZ_AMBIENTE',
                      `BYDAutoLightDevice.getInstance(context).setAmbientLightColor("${c.hex}");`,
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
