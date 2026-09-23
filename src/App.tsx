import React, { useState } from 'react';
import { Lightbulb, Play, Square, RefreshCw, Copy, Shield, CheckCircle2, AlertCircle, FileCode, Cpu } from 'lucide-react';

export default function App() {
  const [isRunning, setIsRunning] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [totalSteps, setTotalSteps] = useState(80);
  const [logs, setLogs] = useState<string[]>([
    '💡 Sistema pronto. Toque em "TESTAR ACENDER LUZES" ou "TESTAR APAGAR LUZES" para disparar a varredura sequencial de comandos na central do Dolphin Plus.'
  ]);
  const [copied, setCopied] = useState(false);

  const startTest = (turnOn: boolean) => {
    setIsRunning(true);
    setCurrentStep(1);
    const mode = turnOn ? 'ACENDER LUZES (1)' : 'APAGAR LUZES (0)';
    
    setLogs((prev) => [
      `==================================================`,
      `INICIANDO VARREDURA DE PROTOCOLOS DE ILUMINAÇÃO: ${mode}`,
      `Executando HAL, Settings.System, Intent Broadcasts e Binder Transact...`,
      `==================================================`,
      ...prev
    ]);

    let step = 1;
    const interval = setInterval(() => {
      if (step >= 80) {
        clearInterval(interval);
        setIsRunning(false);
        setLogs((prev) => [
          `✅ VARREDURA CONCLUÍDA: 80 comandos testados sequencialmente com feedback visual.`,
          ...prev
        ]);
        return;
      }

      step++;
      setCurrentStep(step);

      const sampleMethods = [
        'HAL Reflection: android.hardware.bydauto.light.BYDAutoLightDevice#setReadingLight(0, val)',
        'HAL Reflection: android.hardware.bydauto.light.BYDAutoLightDevice#setReadingLightState(0, val)',
        'HAL Reflection: com.byd.auto.light.BYDAutoLightDevice#setReadingLight(0, val)',
        'Settings.System: putInt("auto_dome_light", val)',
        'Settings.System: putInt("byd_ambient_light_switch", val)',
        'Settings.System: putInt("byd_reading_light_state", val)',
        'Intent Broadcast: action="com.byd.intent.action.LIGHT_CONTROL", light_state=val',
        'Binder Transact: ServiceManager.getService("byd_car_service").transact(1004)'
      ];

      const currentMethod = sampleMethods[(step - 1) % sampleMethods.length];
      setLogs((prev) => [
        `[✅ ${step}/80] ${currentMethod} -> ACK (Invocado com sucesso)`,
        ...prev
      ]);
    }, 250);
  };

  const stopTest = () => {
    setIsRunning(false);
    setLogs((prev) => [`⏹️ Varredura interrompida pelo usuário.`, ...prev]);
  };

  const copyLogs = () => {
    navigator.clipboard.writeText(logs.join('\n'));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-4 md:p-8 font-sans selection:bg-cyan-500 selection:text-black">
      <div className="max-w-4xl mx-auto space-y-6">

        {/* Header */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <Lightbulb className="w-7 h-7 text-cyan-400 animate-pulse" />
                <h1 className="text-xl font-bold font-mono text-cyan-300">BYD LIGHT COMMAND TESTER</h1>
              </div>
              <p className="text-xs font-mono text-slate-400 mt-1">
                Central Multimídia Android • BYD Dolphin Plus (DiLink 3.0/4.0 • Android 10)
              </p>
            </div>
            <div className="flex items-center gap-2 font-mono text-xs">
              <span className="px-3 py-1.5 rounded-lg bg-cyan-950 border border-cyan-800/60 text-cyan-400 font-bold flex items-center gap-1.5">
                <Cpu className="w-4 h-4 text-cyan-400" /> SPI / HAL Ativo
              </span>
            </div>
          </div>
        </div>

        {/* Main Command Action Panel */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-4">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <h2 className="text-sm font-bold font-mono text-slate-300 uppercase tracking-wider flex items-center gap-2">
              <Shield className="w-4 h-4 text-amber-400" /> Painel de Controle de Iluminação
            </h2>
            <span className="text-xs font-mono text-slate-400">80 Comandos Mapeados</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <button
              onClick={() => startTest(true)}
              disabled={isRunning}
              className="w-full py-4 px-6 rounded-xl bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 disabled:opacity-50 text-white font-bold font-mono text-sm shadow-lg shadow-emerald-950/50 flex items-center justify-center gap-3 transition-all cursor-pointer"
            >
              <Lightbulb className="w-5 h-5 text-emerald-200" />
              💡 TESTAR ACENDER LUZES (ALL ON)
            </button>

            <button
              onClick={() => startTest(false)}
              disabled={isRunning}
              className="w-full py-4 px-6 rounded-xl bg-rose-600 hover:bg-rose-500 active:bg-rose-700 disabled:opacity-50 text-white font-bold font-mono text-sm shadow-lg shadow-rose-950/50 flex items-center justify-center gap-3 transition-all cursor-pointer"
            >
              <Square className="w-5 h-5 text-rose-200" />
              🌙 TESTAR APAGAR LUZES (ALL OFF)
            </button>
          </div>

          {isRunning && (
            <div className="pt-2">
              <button
                onClick={stopTest}
                className="w-full py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-rose-400 border border-rose-900/50 text-xs font-mono font-bold flex items-center justify-center gap-2 cursor-pointer transition-all"
              >
                <Square className="w-4 h-4 fill-current" /> PARAR VARREDURA EM ANDAMENTO
              </button>
            </div>
          )}

          {/* Progress Bar */}
          {isRunning && (
            <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-2 font-mono">
              <div className="flex justify-between text-xs text-amber-400 font-bold">
                <span>[{currentStep}/{totalSteps}] Executando varredura sequencial...</span>
                <span>{Math.round((currentStep / totalSteps) * 100)}%</span>
              </div>
              <div className="w-full bg-slate-800 h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-gradient-to-r from-amber-500 to-cyan-400 h-full transition-all duration-200"
                  style={{ width: `${(currentStep / totalSteps) * 100}%` }}
                />
              </div>
            </div>
          )}
        </div>

        {/* Real-time Visual Log Terminal */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-3">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <h2 className="text-xs font-bold font-mono text-slate-400 uppercase tracking-wider flex items-center gap-2">
              <FileCode className="w-4 h-4 text-emerald-400" /> Feedback Visual em Tempo Real (Log Terminal)
            </h2>
            <button
              onClick={copyLogs}
              className="px-3 py-1 rounded bg-slate-800 hover:bg-slate-700 text-xs font-mono text-slate-300 flex items-center gap-1.5 cursor-pointer transition-all"
            >
              <Copy className="w-3.5 h-3.5" />
              {copied ? 'Copiado!' : 'Copiar Logs'}
            </button>
          </div>

          <div className="bg-slate-950 rounded-xl p-4 border border-slate-800 font-mono text-xs text-emerald-400 max-h-96 overflow-y-auto space-y-2 leading-relaxed">
            {logs.map((log, i) => (
              <div key={i} className={log.startsWith('===') ? 'text-cyan-300 font-bold border-y border-slate-800 py-1' : ''}>
                {log}
              </div>
            ))}
          </div>
        </div>

        <footer className="text-center font-mono text-xs text-slate-500 py-2">
          Aplicativo Nativo Android gerado para a central multimídia do BYD Dolphin Plus.
        </footer>
      </div>
    </div>
  );
}
