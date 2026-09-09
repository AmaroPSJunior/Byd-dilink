import React, { useState } from 'react';
import {
  Smartphone,
  Github,
  Copy,
  Check,
  Download,
  Terminal,
  CheckCircle2,
  AlertCircle,
  FileCode,
  Layers,
  ArrowRight,
  Play,
  RotateCcw,
  Cpu,
  ShieldCheck,
  ExternalLink,
  Zap,
  Code2
} from 'lucide-react';
import {
  ANDROID_MANIFEST_XML,
  MAIN_ACTIVITY_KOTLIN,
  BYD_DILINK_SERVICE_HELPER_KOTLIN,
  BUILD_GRADLE,
  ROOT_BUILD_GRADLE,
  SETTINGS_GRADLE,
  GRADLE_WRAPPER_PROPERTIES,
  GITHUB_ACTIONS_WORKFLOW
} from '../data/dilinkCodeTemplates';

export const AndroidExporterModal: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'analysis' | 'files' | 'simulator'>('analysis');
  const [activeFile, setActiveFile] = useState<
    | 'manifest'
    | 'mainActivity'
    | 'serviceHelper'
    | 'appGradle'
    | 'rootGradle'
    | 'settingsGradle'
    | 'wrapperProps'
    | 'workflow'
  >('workflow');

  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Simulator state
  const [simulating, setSimulating] = useState(false);
  const [simLogs, setSimLogs] = useState<string[]>([]);
  const [simProgress, setSimProgress] = useState(0);
  const [simComplete, setSimComplete] = useState(false);

  const fileMap = {
    workflow: { name: '.github/workflows/build-apk.yml', code: GITHUB_ACTIONS_WORKFLOW, path: '.github/workflows/build-apk.yml', lang: 'yaml' },
    mainActivity: { name: 'MainActivity.kt', code: MAIN_ACTIVITY_KOTLIN, path: 'app/src/main/java/com/byd/carcontrol/MainActivity.kt', lang: 'kotlin' },
    serviceHelper: { name: 'BYDDiLinkServiceHelper.kt', code: BYD_DILINK_SERVICE_HELPER_KOTLIN, path: 'app/src/main/java/com/byd/carcontrol/BYDDiLinkServiceHelper.kt', lang: 'kotlin' },
    manifest: { name: 'AndroidManifest.xml', code: ANDROID_MANIFEST_XML, path: 'app/src/main/AndroidManifest.xml', lang: 'xml' },
    appGradle: { name: 'app/build.gradle', code: BUILD_GRADLE, path: 'app/build.gradle', lang: 'groovy' },
    rootGradle: { name: 'build.gradle (raiz)', code: ROOT_BUILD_GRADLE, path: 'build.gradle', lang: 'groovy' },
    settingsGradle: { name: 'settings.gradle', code: SETTINGS_GRADLE, path: 'settings.gradle', lang: 'groovy' },
    wrapperProps: { name: 'gradle-wrapper.properties', code: GRADLE_WRAPPER_PROPERTIES, path: 'gradle/wrapper/gradle-wrapper.properties', lang: 'ini' },
  };

  const currentFileObj = fileMap[activeFile];

  const handleCopyCode = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const runBuildSimulation = () => {
    setSimulating(true);
    setSimComplete(false);
    setSimLogs([]);
    setSimProgress(5);

    const steps = [
      { delay: 400, text: '🚀 [GitHub Actions] Trigger: push on branch main...', progress: 15 },
      { delay: 900, text: '🐧 Setting up Ubuntu 22.04 LTS runner environment...', progress: 28 },
      { delay: 1400, text: '📦 actions/checkout@v4: Fetching Kotlin source code...', progress: 40 },
      { delay: 2000, text: '☕ actions/setup-java@v4: Configuring JDK 17 & Kotlin compiler 1.9.22...', progress: 55 },
      { delay: 2700, text: '⚡ Executing: ./gradlew assembleDebug --no-daemon', progress: 70 },
      { delay: 3200, text: '🔮 [Kotlin Compiler] Compiling Kotlin classes & BYD reflection hooks...', progress: 85 },
      { delay: 3800, text: 'BUILD SUCCESSFUL in 1m 08s - Kotlin APK compiled!', progress: 95 },
      { delay: 4300, text: '🎉 Artifact created: app/build/outputs/apk/debug/app-debug.apk (12.8 MB)', progress: 100 },
    ];

    steps.forEach((st) => {
      setTimeout(() => {
        setSimLogs((prev) => [...prev, st.text]);
        setSimProgress(st.progress);
        if (st.progress === 100) {
          setSimulating(false);
          setSimComplete(true);
        }
      }, st.delay);
    });
  };

  return (
    <div className="w-full max-w-6xl mx-auto py-6 px-4 space-y-6">
      
      {/* HEADER BANNER */}
      <div className="bg-slate-900/90 backdrop-blur-md rounded-3xl p-6 border border-slate-800 shadow-2xl">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-3.5 rounded-2xl bg-purple-950 border border-purple-500/40 text-purple-400">
              <Code2 className="w-7 h-7" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-white text-base font-mono uppercase tracking-wide">
                  Projeto Kotlin Native & Build CI/CD (Padrão Indriver-analyzer)
                </h3>
                <span className="px-2 py-0.5 rounded-md bg-purple-950 text-purple-400 border border-purple-800 text-[10px] font-mono font-bold">
                  Kotlin 1.9 + JDK 17
                </span>
              </div>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                Código Kotlin nativo com compilação automática via GitHub Actions sem necessidade de Android Studio local.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveTab('analysis')}
              className={`px-3 py-2 rounded-xl text-xs font-mono font-semibold transition-all ${
                activeTab === 'analysis'
                  ? 'bg-purple-600 text-white shadow-md'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Análise
            </button>
            <button
              onClick={() => setActiveTab('files')}
              className={`px-3 py-2 rounded-xl text-xs font-mono font-semibold transition-all ${
                activeTab === 'files'
                  ? 'bg-purple-600 text-white shadow-md'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Arquivos Kotlin
            </button>
            <button
              onClick={() => setActiveTab('simulator')}
              className={`px-3 py-2 rounded-xl text-xs font-mono font-semibold transition-all ${
                activeTab === 'simulator'
                  ? 'bg-purple-600 text-white shadow-md'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Simulador CI/CD
            </button>
          </div>
        </div>
      </div>

      {/* TAB 1: REPOSITORY ANALYSIS */}
      {activeTab === 'analysis' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            <div className="bg-slate-900/90 p-5 rounded-2xl border border-slate-800 space-y-3">
              <div className="p-2.5 w-fit rounded-xl bg-purple-950 border border-purple-800 text-purple-400">
                <Code2 className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-sm text-slate-200 font-mono">1. Migração para Kotlin Native</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                Seguindo o padrão do <code className="text-purple-400">Indriver-analyzer</code>, o código do app foi convertido de Java para <b>Kotlin idiomatico</b>, trazendo <code className="text-slate-300">data classes</code>, extensões, síntaxe limpa e segurança de nullability.
              </p>
            </div>

            <div className="bg-slate-900/90 p-5 rounded-2xl border border-slate-800 space-y-3">
              <div className="p-2.5 w-fit rounded-xl bg-indigo-950 border border-indigo-800 text-indigo-400">
                <Cpu className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-sm text-slate-200 font-mono">2. DiLink Reflection em Kotlin</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                Invocação via Reflection em Kotlin para <code className="text-slate-300">com.byd.service.BYDAutoLightBus</code> com fallback de <code className="text-indigo-300">BroadcastIntent</code> em Kotlin conciso, permitindo build limpo no Gradle.
              </p>
            </div>

            <div className="bg-slate-900/90 p-5 rounded-2xl border border-slate-800 space-y-3">
              <div className="p-2.5 w-fit rounded-xl bg-emerald-950 border border-emerald-800 text-emerald-400">
                <Zap className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-sm text-slate-200 font-mono">3. Build na Nuvem com Gradle + Kotlin</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                O arquivo <code className="text-emerald-400">.github/workflows/build-apk.yml</code> executa o plugin <code className="text-slate-300">org.jetbrains.kotlin.android</code> no runner do GitHub, gerando o APK compilado automaticamente.
              </p>
            </div>
          </div>

          {/* WORKFLOW STEPS DIAGRAM */}
          <div className="bg-slate-950 p-6 rounded-3xl border border-slate-800 space-y-4">
            <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center gap-2">
              <Terminal className="w-4 h-4 text-purple-400" /> Fluxo de Compilação Kotlin (Sem Android Studio)
            </h4>
            
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 pt-2">
              {[
                { step: '01', title: 'Crie o Repositório', desc: 'No GitHub, crie um novo repositório em Kotlin chamado BydControllerKotlin.' },
                { step: '02', title: 'Copie os Arquivos Kotlin', desc: 'Faça upload de MainActivity.kt, BYDDiLinkServiceHelper.kt e das configurações do Gradle.' },
                { step: '03', title: 'Compilação Kotlin CI/CD', desc: 'O GitHub Actions compila os fontes Kotlin com o plugin org.jetbrains.kotlin.android.' },
                { step: '04', title: 'Baixe o APK para o BYD', desc: 'Acesse a aba Actions e faça o download de BYD-Controller-Kotlin-Debug.apk.' }
              ].map((st) => (
                <div key={st.step} className="bg-slate-900/80 p-4 rounded-2xl border border-slate-800 space-y-2">
                  <span className="font-mono font-bold text-xs text-purple-400 bg-purple-950 px-2.5 py-0.5 rounded-full border border-purple-800">
                    PASSO {st.step}
                  </span>
                  <h5 className="font-bold text-sm text-slate-200 font-mono">{st.title}</h5>
                  <p className="text-xs text-slate-400 leading-relaxed">{st.desc}</p>
                </div>
              ))}
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => setActiveTab('files')}
                className="px-5 py-2.5 bg-purple-600 hover:bg-purple-500 text-white font-mono text-xs font-bold rounded-xl flex items-center gap-2 transition-all shadow-lg shadow-purple-950/60"
              >
                Ver Arquivos Kotlin <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* TROUBLESHOOTING GITHUB ACTIONS CARD */}
          <div className="bg-gradient-to-r from-amber-950/40 via-slate-900 to-purple-950/40 p-6 rounded-3xl border border-amber-500/30 space-y-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-amber-500/20 text-amber-400 border border-amber-500/40">
                <AlertCircle className="w-6 h-6" />
              </div>
              <div>
                <h4 className="font-bold text-sm text-amber-200 font-mono">
                  ⚠️ Nada apareceu na aba Actions do GitHub? Veja como resolver:
                </h4>
                <p className="text-xs text-slate-300 font-mono mt-0.5">
                  Siga estas 4 verificações rápidas para ativar a geração automática do seu APK:
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
              <div className="bg-slate-900/90 p-3.5 rounded-xl border border-slate-800 text-xs text-slate-300 font-mono space-y-1">
                <span className="text-amber-400 font-bold">1. Ponto inicial na pasta:</span>
                <p className="text-slate-400">O arquivo deve ficar estritamente em <code className="text-purple-300 bg-slate-950 px-1 py-0.5 rounded">.github/workflows/build-apk.yml</code> (não esqueça o <b>ponto</b> em <code className="text-slate-300">.github</code>!).</p>
              </div>

              <div className="bg-slate-900/90 p-3.5 rounded-xl border border-slate-800 text-xs text-slate-300 font-mono space-y-1">
                <span className="text-amber-400 font-bold">2. Habilitar Actions no GitHub:</span>
                <p className="text-slate-400">Na aba <b>Actions</b> do GitHub, se houver o botão verde <b>"I understand my workflows, go ahead and enable them"</b>, clique nele!</p>
              </div>

              <div className="bg-slate-900/90 p-3.5 rounded-xl border border-slate-800 text-xs text-slate-300 font-mono space-y-1">
                <span className="text-amber-400 font-bold">3. Botão de Disparo Manual:</span>
                <p className="text-slate-400">Vá em <b>Actions</b> &gt; selecione <b>Build BYD Car Control APK (Kotlin Native)</b> &gt; clique em <b>Run workflow</b> &gt; <b>Run workflow</b>.</p>
              </div>

              <div className="bg-slate-900/90 p-3.5 rounded-xl border border-slate-800 text-xs text-slate-300 font-mono space-y-1">
                <span className="text-amber-400 font-bold">4. Suporte a Qualquer Branch:</span>
                <p className="text-slate-400">Atualizamos a trigger no arquivo YAML de workflow para disparar em <b>qualquer branch</b> (<code className="text-purple-300">push:</code> aberto e <code className="text-purple-300">workflow_dispatch:</code>).</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: FILES VIEW */}
      {activeTab === 'files' && (
        <div className="bg-slate-950 rounded-3xl p-6 border border-slate-800 shadow-2xl space-y-4">
          
          {/* File Tabs */}
          <div className="flex items-center gap-2 overflow-x-auto pb-3 border-b border-slate-800 scrollbar-none">
            {(Object.keys(fileMap) as (keyof typeof fileMap)[]).map((key) => (
              <button
                key={key}
                onClick={() => setActiveFile(key)}
                className={`px-3.5 py-2 rounded-xl font-mono text-xs font-semibold flex items-center gap-2 transition-all shrink-0 ${
                  activeFile === key
                    ? 'bg-purple-600 text-white shadow-md shadow-purple-950/80'
                    : 'bg-slate-900 text-slate-400 hover:text-slate-200 hover:bg-slate-850'
                }`}
              >
                <FileCode className="w-3.5 h-3.5" />
                <span>{fileMap[key].name}</span>
              </button>
            ))}
          </div>

          <div className="flex items-center justify-between text-xs font-mono text-slate-400 px-2">
            <span>Caminho no Repositório: <code className="text-purple-400">{currentFileObj.path}</code></span>
            <button
              onClick={() => handleCopyCode(currentFileObj.code, activeFile)}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 rounded-lg text-slate-200 flex items-center gap-1.5 border border-slate-700 transition"
            >
              {copiedKey === activeFile ? (
                <>
                  <Check className="w-3.5 h-3.5 text-purple-400" /> Copiado!
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5" /> Copiar Código
                </>
              )}
            </button>
          </div>

          {/* Code View Area */}
          <pre className="p-5 bg-slate-900/90 rounded-2xl border border-slate-800/80 text-purple-300 text-xs font-mono overflow-x-auto leading-relaxed max-h-[500px]">
            <code>{currentFileObj.code}</code>
          </pre>
        </div>
      )}

      {/* TAB 3: SIMULATOR */}
      {activeTab === 'simulator' && (
        <div className="bg-slate-950 rounded-3xl p-6 border border-slate-800 shadow-2xl space-y-5">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
            <div>
              <h4 className="font-mono text-sm font-bold text-white flex items-center gap-2">
                <Terminal className="w-4 h-4 text-purple-400" />
                Simulador do Executor GitHub Actions (Kotlin Compiler)
              </h4>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                Veja em tempo real como o runner <code className="text-purple-400">ubuntu-latest</code> compila o APK Kotlin do seu BYD.
              </p>
            </div>

            <button
              onClick={runBuildSimulation}
              disabled={simulating}
              className="px-4 py-2.5 bg-purple-600 hover:bg-purple-500 disabled:opacity-50 text-white font-mono text-xs font-bold rounded-xl flex items-center gap-2 transition-all shadow-lg shadow-purple-950/60"
            >
              {simulating ? (
                <>
                  <RotateCcw className="w-4 h-4 animate-spin" /> Compilando Kotlin...
                </>
              ) : (
                <>
                  <Play className="w-4 h-4" /> Iniciar Simulação Kotlin Build
                </>
              )}
            </button>
          </div>

          {/* Progress Bar */}
          <div className="space-y-1.5">
            <div className="flex justify-between text-xs font-mono text-slate-400">
              <span>Status do Runner</span>
              <span>{simProgress}%</span>
            </div>
            <div className="w-full bg-slate-900 h-2.5 rounded-full overflow-hidden border border-slate-800">
              <div
                className="bg-purple-500 h-full transition-all duration-300 ease-out"
                style={{ width: `${simProgress}%` }}
              />
            </div>
          </div>

          {/* Terminal Box */}
          <div className="bg-slate-900/90 rounded-2xl border border-slate-800 p-4 font-mono text-xs text-slate-300 min-h-[220px] max-h-[300px] overflow-y-auto space-y-2">
            {simLogs.length === 0 ? (
              <p className="text-slate-500 italic">Clique em "Iniciar Simulação Kotlin Build" para testar a pipeline do GitHub Actions.</p>
            ) : (
              simLogs.map((log, index) => (
                <div key={index} className="flex items-start gap-2">
                  <span className="text-slate-600 font-bold shrink-0">&gt;</span>
                  <span className={log.includes('SUCCESSFUL') || log.includes('Artifact') ? 'text-purple-400 font-bold' : ''}>
                    {log}
                  </span>
                </div>
              ))
            )}
          </div>

          {/* Simulation Artifact Result */}
          {simComplete && (
            <div className="p-4 rounded-2xl bg-purple-950/60 border border-purple-800 flex flex-col sm:flex-row items-center justify-between gap-4 animate-fade-in">
              <div className="flex items-center gap-3">
                <CheckCircle2 className="w-6 h-6 text-purple-400 shrink-0" />
                <div>
                  <h5 className="font-bold text-sm text-purple-200 font-mono">Build Kotlin Concluído com Sucesso!</h5>
                  <p className="text-xs text-purple-300 font-mono">
                    O artefato <code className="text-white">BYD-Controller-Kotlin-Debug.apk</code> foi gerado.
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <a
                  href="#download-sim"
                  onClick={(e) => {
                    e.preventDefault();
                    alert("No seu repositório real do GitHub, o download do arquivo 'BYD-Controller-Kotlin-Debug.apk' estará disponível na aba Actions > Summary.");
                  }}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white font-mono text-xs font-bold rounded-xl flex items-center gap-2 transition"
                >
                  <Download className="w-4 h-4" /> Download Simulado APK
                </a>
              </div>
            </div>
          )}
        </div>
      )}

    </div>
  );
};


