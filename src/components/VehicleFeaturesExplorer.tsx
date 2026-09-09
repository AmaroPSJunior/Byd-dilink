import React, { useState } from 'react';
import {
  Search,
  Filter,
  CheckCircle2,
  AlertCircle,
  Zap,
  Lightbulb,
  ShieldCheck,
  Lock,
  Maximize2,
  Thermometer,
  Disc,
  Volume2,
  Activity,
  Play,
  RotateCcw,
  Sliders,
  Cpu,
  Layers,
  Sparkles,
  ChevronDown,
  Check
} from 'lucide-react';
import { FeatureCategory, VehicleFeature, CompatibilityStatus } from '../types';
import { INITIAL_VEHICLE_FEATURES } from '../data/allVehicleFeatures';

interface VehicleFeaturesExplorerProps {
  onLogDiLinkAction: (
    actionName: string,
    javaCall: string,
    intent: string,
    params: Record<string, any>,
    category?: 'LIGHT' | 'SEATBELT' | 'DOOR' | 'WINDOW' | 'SYSTEM' | 'CANBUS'
  ) => void;
}

export const VehicleFeaturesExplorer: React.FC<VehicleFeaturesExplorerProps> = ({
  onLogDiLinkAction
}) => {
  const [features, setFeatures] = useState<VehicleFeature[]>(INITIAL_VEHICLE_FEATURES);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [compatibilityFilter, setCompatibilityFilter] = useState<string>('ALL');
  
  // Scanning State
  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);
  const [lastScanTime, setLastScanTime] = useState<string | null>('2026-09-09 00:35');

  // Handle Feature Control Change
  const handleUpdateFeatureValue = (featureId: string, newValue: any) => {
    setFeatures((prev) =>
      prev.map((f) => {
        if (f.id === featureId) {
          const updated = {
            ...f,
            currentValue: newValue,
            lastTestedAt: new Date().toLocaleTimeString()
          };

          // Send log
          onLogDiLinkAction(
            `CONTROLE_${f.id.toUpperCase()}`,
            f.sdkMethod.replace(/Value|0|true|false/g, String(newValue)),
            f.intentAction,
            { featureId: f.id, newValue },
            getLogCategory(f.category)
          );

          return updated;
        }
        return f;
      })
    );
  };

  const getLogCategory = (cat: FeatureCategory): 'LIGHT' | 'SEATBELT' | 'DOOR' | 'WINDOW' | 'SYSTEM' | 'CANBUS' => {
    switch (cat) {
      case 'LIGHTING':
        return 'LIGHT';
      case 'SEATBELT':
        return 'SEATBELT';
      case 'DOORS_LOCKS':
        return 'DOOR';
      case 'WINDOWS_ROOF':
        return 'WINDOW';
      default:
        return 'CANBUS';
    }
  };

  // Run Full Vehicle Diagnostics Scan
  const handleRunFullScan = () => {
    setIsScanning(true);
    setScanProgress(0);

    let progress = 0;
    const total = features.length;
    const interval = setInterval(() => {
      progress += 1;
      const percentage = Math.round((progress / total) * 100);
      setScanProgress(percentage);

      const currentFeature = features[progress - 1];
      if (currentFeature) {
        onLogDiLinkAction(
          `VARREDURA_SINAL_${currentFeature.id.toUpperCase()}`,
          currentFeature.sdkMethod,
          currentFeature.intentAction,
          { status: 'HARDWARE_ACK_OK', busAddress: '0x7E0' },
          getLogCategory(currentFeature.category)
        );
      }

      if (progress >= total) {
        clearInterval(interval);
        setIsScanning(false);
        setLastScanTime(new Date().toLocaleTimeString());
        
        // Mark all tested
        setFeatures((prev) =>
          prev.map((f) => ({ ...f, lastTestedAt: new Date().toLocaleTimeString() }))
        );
      }
    }, 40);
  };

  // Filter Features
  const filteredFeatures = features.filter((f) => {
    const matchesCategory = selectedCategory === 'ALL' || f.category === selectedCategory;
    const matchesSearch =
      searchQuery.trim() === '' ||
      f.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      f.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      f.sdkMethod.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesCompatibility =
      compatibilityFilter === 'ALL' ||
      (compatibilityFilter === 'COMPATIBLE' && f.isCompatible) ||
      (compatibilityFilter === 'CONTROLLABLE' && f.isControllable);

    return matchesCategory && matchesSearch && matchesCompatibility;
  });

  const categoryCounts = {
    ALL: features.length,
    LIGHTING: features.filter((f) => f.category === 'LIGHTING').length,
    SEATBELT: features.filter((f) => f.category === 'SEATBELT').length,
    DOORS_LOCKS: features.filter((f) => f.category === 'DOORS_LOCKS').length,
    WINDOWS_ROOF: features.filter((f) => f.category === 'WINDOWS_ROOF').length,
    CLIMATE_HVAC: features.filter((f) => f.category === 'CLIMATE_HVAC').length,
    BATTERY_EV: features.filter((f) => f.category === 'BATTERY_EV').length,
    DRIVE_MODE: features.filter((f) => f.category === 'DRIVE_MODE').length,
    TPMS: features.filter((f) => f.category === 'TPMS').length,
    MIRRORS_WIPERS: features.filter((f) => f.category === 'MIRRORS_WIPERS').length,
    AUDIO_DILINK: features.filter((f) => f.category === 'AUDIO_DILINK').length,
    SAFETY_DIAG: features.filter((f) => f.category === 'SAFETY_DIAG').length
  };

  const categoryLabels: Record<string, { label: string; icon: React.ReactNode }> = {
    ALL: { label: 'Todos os Recursos', icon: <Layers className="w-4 h-4 text-cyan-400" /> },
    LIGHTING: { label: 'Iluminação & Faróis', icon: <Lightbulb className="w-4 h-4 text-amber-400" /> },
    SEATBELT: { label: 'Cintos & Bancos', icon: <ShieldCheck className="w-4 h-4 text-red-400" /> },
    DOORS_LOCKS: { label: 'Portas & Trava', icon: <Lock className="w-4 h-4 text-emerald-400" /> },
    WINDOWS_ROOF: { label: 'Vidros & Teto', icon: <Maximize2 className="w-4 h-4 text-blue-400" /> },
    CLIMATE_HVAC: { label: 'Climatização A/C', icon: <Thermometer className="w-4 h-4 text-sky-400" /> },
    BATTERY_EV: { label: 'Bateria & Propulsão EV', icon: <Zap className="w-4 h-4 text-emerald-400" /> },
    DRIVE_MODE: { label: 'Modos de Condução', icon: <Activity className="w-4 h-4 text-violet-400" /> },
    TPMS: { label: 'Pneus TPMS', icon: <Disc className="w-4 h-4 text-orange-400" /> },
    MIRRORS_WIPERS: { label: 'Retrovisores & Palhetas', icon: <Sliders className="w-4 h-4 text-teal-400" /> },
    AUDIO_DILINK: { label: 'Áudio & Tela Giratória', icon: <Volume2 className="w-4 h-4 text-fuchsia-400" /> },
    SAFETY_DIAG: { label: 'Diagnóstico & ADAS', icon: <Cpu className="w-4 h-4 text-indigo-400" /> }
  };

  const getCompatibilityBadge = (status: CompatibilityStatus) => {
    switch (status) {
      case 'COMPATIBLE_ENABLED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-950/80 text-emerald-400 border border-emerald-800/80">
            <CheckCircle2 className="w-3 h-3 text-emerald-400" /> Habilitado neste Veículo
          </span>
        );
      case 'NATIVE_DILINK_API':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-cyan-950/80 text-cyan-300 border border-cyan-800/80">
            <Cpu className="w-3 h-3 text-cyan-400" /> Barramento Nativo DiLink
          </span>
        );
      case 'OPTIONAL_EQUIPPED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-950/80 text-amber-300 border border-amber-800/80">
            <Sparkles className="w-3 h-3 text-amber-400" /> Opcional / Modelo Premium
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-slate-800 text-slate-400">
            <AlertCircle className="w-3 h-3" /> Não Equipado
          </span>
        );
    }
  };

  const renderCategoryIcon = (category: FeatureCategory) => {
    switch (category) {
      case 'LIGHTING':
        return <Lightbulb className="w-5 h-5 text-amber-400" />;
      case 'SEATBELT':
        return <ShieldCheck className="w-5 h-5 text-red-400" />;
      case 'DOORS_LOCKS':
        return <Lock className="w-5 h-5 text-emerald-400" />;
      case 'WINDOWS_ROOF':
        return <Maximize2 className="w-5 h-5 text-blue-400" />;
      case 'CLIMATE_HVAC':
        return <Thermometer className="w-5 h-5 text-sky-400" />;
      case 'BATTERY_EV':
        return <Zap className="w-5 h-5 text-emerald-400" />;
      case 'DRIVE_MODE':
        return <Activity className="w-5 h-5 text-violet-400" />;
      case 'TPMS':
        return <Disc className="w-5 h-5 text-orange-400" />;
      case 'MIRRORS_WIPERS':
        return <Sliders className="w-5 h-5 text-teal-400" />;
      case 'AUDIO_DILINK':
        return <Volume2 className="w-5 h-5 text-fuchsia-400" />;
      case 'SAFETY_DIAG':
        return <Cpu className="w-5 h-5 text-indigo-400" />;
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-6 space-y-6 animate-fadeIn">
      {/* Top Banner & Scanner Box */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-900 to-cyan-950/60 p-6 rounded-3xl border border-slate-800 shadow-2xl relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-96 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 relative z-10">
          <div>
            <div className="flex items-center gap-3">
              <div className="p-3 bg-cyan-500/20 rounded-2xl border border-cyan-500/30 text-cyan-400">
                <Cpu className="w-7 h-7 animate-pulse" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-white tracking-wide">
                  Catálogo & Varredura de Recursos BYD DiLink
                </h1>
                <p className="text-sm text-slate-400">
                  Mapeamento em tempo real de sensores, atuadores, comandos e diagnósticos do barramento CAN.
                </p>
              </div>
            </div>

            {/* Hardware Metrics Badge Bar */}
            <div className="flex flex-wrap items-center gap-4 mt-4 text-xs font-mono">
              <div className="flex items-center gap-2 bg-slate-950/80 px-3 py-1.5 rounded-xl border border-slate-800">
                <span className="text-slate-400">Total de Recursos Mapeados:</span>
                <span className="text-cyan-400 font-bold">{features.length} Funcionalidades</span>
              </div>
              <div className="flex items-center gap-2 bg-slate-950/80 px-3 py-1.5 rounded-xl border border-slate-800">
                <span className="text-slate-400">Barramento CAN:</span>
                <span className="text-emerald-400 font-bold flex items-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5" /> 500 kbps ACK OK
                </span>
              </div>
              <div className="flex items-center gap-2 bg-slate-950/80 px-3 py-1.5 rounded-xl border border-slate-800">
                <span className="text-slate-400">Última Varredura:</span>
                <span className="text-amber-300 font-bold">{lastScanTime || 'Pendente'}</span>
              </div>
            </div>
          </div>

          {/* Action Button: Auto-Scan All Capabilities */}
          <div className="flex flex-col sm:flex-row items-center gap-3 w-full lg:w-auto">
            <button
              id="btn-run-full-vehicle-scan"
              disabled={isScanning}
              onClick={handleRunFullScan}
              className={`w-full sm:w-auto px-6 py-3.5 rounded-2xl font-bold text-sm flex items-center justify-center gap-2 transition-all shadow-xl ${
                isScanning
                  ? 'bg-cyan-950 text-cyan-400 border border-cyan-800 cursor-not-allowed'
                  : 'bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-black shadow-cyan-500/20 border border-cyan-300/40'
              }`}
            >
              {isScanning ? (
                <>
                  <RotateCcw className="w-4 h-4 animate-spin text-cyan-400" />
                  <span>Scanning Veículo ({scanProgress}%)...</span>
                </>
              ) : (
                <>
                  <Play className="w-4 h-4 fill-current" />
                  <span>Executar Varredura de Compatibilidade</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Progress Bar when Scanning */}
        {isScanning && (
          <div className="mt-6 space-y-1.5 animate-fadeIn">
            <div className="flex justify-between text-xs font-mono text-cyan-300">
              <span>Testando módulo DiLink CAN Bus...</span>
              <span>{scanProgress}%</span>
            </div>
            <div className="w-full h-2 bg-slate-950 rounded-full overflow-hidden border border-cyan-900">
              <div
                className="h-full bg-gradient-to-r from-cyan-500 to-emerald-400 transition-all duration-75"
                style={{ width: `${scanProgress}%` }}
              />
            </div>
          </div>
        )}
      </div>

      {/* Category Pills & Filters */}
      <div className="space-y-4">
        {/* Category Scrollable Bar */}
        <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none">
          {Object.entries(categoryLabels).map(([catKey, { label, icon }]) => {
            const count = categoryCounts[catKey as keyof typeof categoryCounts] || 0;
            const isSelected = selectedCategory === catKey;

            return (
              <button
                key={catKey}
                onClick={() => setSelectedCategory(catKey)}
                className={`flex items-center gap-2 px-4 py-2.5 rounded-2xl text-xs font-semibold whitespace-nowrap transition-all border ${
                  isSelected
                    ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/60 shadow-lg shadow-cyan-950/50'
                    : 'bg-slate-900/80 text-slate-400 border-slate-800 hover:bg-slate-850 hover:text-slate-200'
                }`}
              >
                {icon}
                <span>{label}</span>
                <span
                  className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold ${
                    isSelected ? 'bg-cyan-500 text-black' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Search & Secondary Filter Bar */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 bg-slate-900/60 p-3 rounded-2xl border border-slate-800">
          <div className="relative w-full sm:w-96">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Pesquisar recurso (ex: farol, ar, teto, pneu, bateria)..."
              className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 font-mono"
            />
          </div>

          <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
            <span className="text-xs text-slate-400 font-mono flex items-center gap-1.5">
              <Filter className="w-3.5 h-3.5 text-cyan-400" /> Filtrar:
            </span>
            <select
              value={compatibilityFilter}
              onChange={(e) => setCompatibilityFilter(e.target.value)}
              className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-200 font-mono focus:outline-none focus:border-cyan-500"
            >
              <option value="ALL">Todos os Recursos</option>
              <option value="COMPATIBLE">Apenas Compatíveis / Habilitados</option>
              <option value="CONTROLLABLE">Apenas Controláveis</option>
            </select>
          </div>
        </div>
      </div>

      {/* Main Scrollable Vehicle Feature List */}
      <div className="bg-slate-900/80 rounded-3xl border border-slate-800/80 shadow-2xl p-4 lg:p-6 space-y-4">
        <div className="flex items-center justify-between border-b border-slate-800/80 pb-4">
          <div className="flex items-center gap-2">
            <Layers className="w-5 h-5 text-cyan-400" />
            <h2 className="text-lg font-bold text-white">
              Lista Detalhada de Recursos do Veículo ({filteredFeatures.length})
            </h2>
          </div>
          <span className="text-xs text-slate-400 font-mono">
            Mostrando {filteredFeatures.length} de {features.length} recursos
          </span>
        </div>

        {filteredFeatures.length === 0 ? (
          <div className="py-16 text-center space-y-3">
            <AlertCircle className="w-10 h-10 text-slate-600 mx-auto" />
            <p className="text-slate-400 text-sm font-mono">
              Nenhum recurso encontrado para a busca "{searchQuery}".
            </p>
            <button
              onClick={() => {
                setSearchQuery('');
                setSelectedCategory('ALL');
                setCompatibilityFilter('ALL');
              }}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-cyan-400 rounded-xl"
            >
              Limpar Filtros
            </button>
          </div>
        ) : (
          <div className="space-y-3 max-h-[700px] overflow-y-auto pr-2 scrollbar-thin scrollbar-thumb-slate-800">
            {filteredFeatures.map((f) => (
              <div
                key={f.id}
                id={`feature-card-${f.id}`}
                className="bg-slate-950/90 hover:bg-slate-950 border border-slate-800/80 hover:border-cyan-500/40 rounded-2xl p-4 transition-all duration-200 space-y-3 group"
              >
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  {/* Left Info Column */}
                  <div className="flex items-start gap-3.5 flex-1">
                    <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 group-hover:border-cyan-500/30 transition-colors">
                      {renderCategoryIcon(f.category)}
                    </div>

                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-sm font-bold text-white group-hover:text-cyan-300 transition-colors">
                          {f.name}
                        </h3>
                        {getCompatibilityBadge(f.compatibilityLabel)}
                      </div>
                      <p className="text-xs text-slate-400">{f.description}</p>
                      
                      <div className="flex items-center gap-2 text-[11px] font-mono text-slate-500 pt-1">
                        <span className="text-cyan-400/90 font-semibold">{f.sdkMethod}</span>
                      </div>
                    </div>
                  </div>

                  {/* Right Control & State Column */}
                  <div className="flex flex-col sm:flex-row items-end sm:items-center gap-4 bg-slate-900/60 p-3 rounded-2xl border border-slate-800/60 min-w-[280px] justify-between">
                    {/* Value Badge */}
                    <div className="text-right sm:text-left">
                      <span className="text-[10px] uppercase text-slate-500 font-mono block">Status Atual:</span>
                      <span className="text-sm font-bold font-mono text-emerald-400">
                        {typeof f.currentValue === 'boolean'
                          ? f.currentValue
                            ? 'LIGADO / ATIVO'
                            : 'DESLIGADO'
                          : `${f.currentValue} ${f.valueUnit || ''}`}
                      </span>
                    </div>

                    {/* Interactive Control */}
                    {f.isControllable ? (
                      <div className="flex items-center gap-2">
                        {f.controlType === 'toggle' && (
                          <button
                            id={`btn-toggle-${f.id}`}
                            onClick={() => handleUpdateFeatureValue(f.id, !f.currentValue)}
                            className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                              f.currentValue
                                ? 'bg-emerald-500 text-black shadow-lg shadow-emerald-500/20'
                                : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                            }`}
                          >
                            {f.currentValue ? 'LIGADO' : 'DESLIGADO'}
                          </button>
                        )}

                        {f.controlType === 'range' && (
                          <div className="flex items-center gap-2">
                            <input
                              type="range"
                              min={f.rangeMin || 0}
                              max={f.rangeMax || 100}
                              step={f.rangeStep || 1}
                              value={f.currentValue}
                              onChange={(e) => handleUpdateFeatureValue(f.id, Number(e.target.value))}
                              className="w-24 accent-cyan-400 cursor-pointer"
                            />
                            <span className="text-xs font-mono text-cyan-300 w-10 text-right font-bold">
                              {f.currentValue}
                            </span>
                          </div>
                        )}

                        {f.controlType === 'select' && f.options && (
                          <select
                            value={f.currentValue}
                            onChange={(e) => handleUpdateFeatureValue(f.id, e.target.value)}
                            className="bg-slate-950 border border-slate-700 rounded-xl px-2.5 py-1.5 text-xs text-cyan-300 font-mono font-semibold focus:outline-none focus:border-cyan-400"
                          >
                            {f.options.map((opt) => (
                              <option key={opt.value} value={opt.value}>
                                {opt.label}
                              </option>
                            ))}
                          </select>
                        )}

                        {f.controlType === 'action_button' && (
                          <button
                            onClick={() => handleUpdateFeatureValue(f.id, 'EXECUTADO')}
                            className="px-3.5 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-black font-bold text-xs shadow-md shadow-cyan-900/30"
                          >
                            Executar Comando
                          </button>
                        )}
                      </div>
                    ) : (
                      <span className="text-[11px] font-mono text-slate-500 bg-slate-950 px-2.5 py-1 rounded-lg border border-slate-800">
                        Apenas Leitura CAN
                      </span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
