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
  Zap
} from 'lucide-react';
import {
  ANDROID_MANIFEST_XML,
  MAIN_ACTIVITY_JAVA,
  BYD_DILINK_SERVICE_HELPER_JAVA,
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
    mainActivity: { name: 'MainActivity.java', code: MAIN_ACTIVITY_JAVA, path: 'app/src/main/java/com/byd/carcontrol/MainActivity.java', lang: 'java' },
    serviceHelper: { name: 'BYDDiLinkServiceHelper.java', code: BYD_DILINK_SERVICE_HELPER_JAVA, path: 'app/src/main/java/com/byd/carcontrol/BYDDiLinkServiceHelper.java', lang: 'java' },
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
      { delay: 900, text: '🐧 Setting up Ubuntu 22.04 LTS runner environment...', progress: 30 },
      { delay: 1400, text: '📦 actions/checkout@v4: Source code fetched from repository.', progress: 45 },
      { delay: 2000, text: '☕ actions/setup-java@v4: Installing OpenJDK 17 (Temurin)...', progress: 60 },
      { delay: 2600, text: '⚙️ Executing: chmod +x gradlew && ./gradlew assembleDebug', progress: 75 },
      { delay: 3300, text: 'BUILD SUCCESSFUL in 1m 12s - 24 actionable tasks: 24 executed', progress: 90 },
      { delay: 3900, text: '🎉 Artifact created: app/build/outputs/apk/debug/app-debug.apk (14.2 MB)', progress: 100 },
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
            <div className="p-3.5 rounded-2xl bg-emerald-950 border border-emerald-500/40 text-emerald-400">
              <Github className="w-7 h-7" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-white text-base font-mono uppercase tracking-wide">
                  Análise & Estrutura de Build: AmaroPSJunior/BydController
                </h3>
                <span className="px-2 py-0.5 rounded-md bg-emerald-950 text-emerald-400 border border-emerald-800 text-[10px] font-mono font-bold">
                  CI/CD Sem Android Studio
                </span>
              </div>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                Compilação 100% automatizada via GitHub Actions. O APK é gerado em nuvem e pronto para download.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveTab('analysis')}
              className={`px-3 py-2 rounded-xl text-xs font-mono font-semibold transition-all ${
                activeTab === 'analysis'
                  ? 'bg-emerald-600 text-white shadow-md'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Análise
            </button>
            <button
              onClick={() => setActiveTab('files')}
              className={`px-3 py-2 rounded-xl text-xs font-mono font-semibold transition-all ${
                activeTab === 'files'
                  ? 'bg-emerald-600 text-white shadow-md'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Arquivos
            </button>
            <button
              onClick={() => setActiveTab('simulator')}
              className={`px-3 py-2 rounded-xl text-xs font-mono font-semibold transition-all ${
                activeTab === 'simulator'
                  ? 'bg-emerald-600 text-white shadow-md'
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
              <div className="p-2.5 w-fit rounded-xl bg-emerald-950 border border-emerald-800 text-emerald-400">
                <Zap className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-sm text-slate-200 font-mono">1. GitHub Actions Pipeline</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                A compilação do APK ocorre em uma máquina virtual Linux (<code className="text-emerald-400">ubuntu-latest</code>) na nuvem do GitHub. Você não precisa instalar Android Studio, SDK, Gradle ou Java no seu computador.
              </p>
            </div>

            <div className="bg-slate-900/90 p-5 rounded-2xl border border-slate-800 space-y-3">
              <div className="p-2.5 w-fit rounded-xl bg-indigo-950 border border-indigo-800 text-indigo-400">
                <Cpu className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-sm text-slate-200 font-mono">2. Acesso ao DiLink por Reflexão</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                Assim como no <code className="text-indigo-300">BydController</code>, o app usa <i>Java Reflection</i> para interagir com <code className="text-slate-300">com.byd.service.*</code>, eliminando a necessidade de arquivos <code className="text-slate-300">.jar</code> proprietários durante o build.
              </p>
            </div>

            <div className="bg-slate-900/90 p-5 rounded-2xl border border-slate-800 space-y-3">
              <div className="p-2.5 w-fit rounded-xl bg-cyan-950 border border-cyan-800 text-cyan-400">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-sm text-slate-200 font-mono">3. Instalação e Teste no Carro</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                Após o commit no GitHub, a aba <b>Actions</b> disponibiliza o arquivo <code className="text-cyan-300">app-debug.apk</code>. Basta copiá-lo para um pendrive ou instalar no multimídia do BYD via Aurora Store / Files.
              </p>
            </div>
          </div>

          {/* WORKFLOW STEPS DIAGRAM */}
          <div className="bg-slate-950 p-6 rounded-3xl border border-slate-800 space-y-4">
            <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center gap-2">
              <Terminal className="w-4 h-4 text-emerald-400" /> Fluxo Simplificado de Compilação (Sem Android Studio)
            </h4>
            
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 pt-2">
              {[
                { step: '01', title: 'Crie o Repositório', desc: 'No GitHub, crie um novo repositório chamado BydController.' },
                { step: '02', title: 'Adicione os Arquivos', desc: 'Faça upload ou commit dos arquivos fornecidos na aba Arquivos.' },
                { step: '03', title: 'Trigger do Workflow', desc: 'O arquivo .github/workflows/build-apk.yml dispara a compilação automática.' },
                { step: '04', title: 'Baixe o APK', desc: 'Acesse a aba Actions do repositório e baixe o arquivo app-debug.apk.' }
              ].map((st) => (
                <div key={st.step} className="bg-slate-900/80 p-4 rounded-2xl border border-slate-800 space-y-2">
                  <span className="font-mono font-bold text-xs text-emerald-400 bg-emerald-950 px-2.5 py-0.5 rounded-full border border-emerald-800">
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
                className="px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-mono text-xs font-bold rounded-xl flex items-center gap-2 transition-all"
              >
                Ver Arquivos do Projeto <ArrowRight className="w-4 h-4" />
              </button>
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
                    ? 'bg-emerald-600 text-white shadow-md shadow-emerald-950/80'
                    : 'bg-slate-900 text-slate-400 hover:text-slate-200 hover:bg-slate-850'
                }`}
              >
                <FileCode className="w-3.5 h-3.5" />
                <span>{fileMap[key].name}</span>
              </button>
            ))}
          </div>

          <div className="flex items-center justify-between text-xs font-mono text-slate-400 px-2">
            <span>Caminho no Repositório: <code className="text-emerald-400">{currentFileObj.path}</code></span>
            <button
              onClick={() => handleCopyCode(currentFileObj.code, activeFile)}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 rounded-lg text-slate-200 flex items-center gap-1.5 border border-slate-700 transition"
            >
              {copiedKey === activeFile ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-400" /> Copiado!
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5" /> Copiar Código
                </>
              )}
            </button>
          </div>

          {/* Code View Area */}
          <pre className="p-5 bg-slate-900/90 rounded-2xl border border-slate-800/80 text-emerald-300 text-xs font-mono overflow-x-auto leading-relaxed max-h-[500px]">
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
                <Terminal className="w-4 h-4 text-emerald-400" />
                Simulador do Executor GitHub Actions
              </h4>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                Veja em tempo real como o runner <code className="text-emerald-400">ubuntu-latest</code> compila o APK do seu BYD.
              </p>
            </div>

            <button
              onClick={runBuildSimulation}
              disabled={simulating}
              className="px-4 py-2.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white font-mono text-xs font-bold rounded-xl flex items-center gap-2 transition-all shadow-lg shadow-emerald-950/60"
            >
              {simulating ? (
                <>
                  <RotateCcw className="w-4 h-4 animate-spin" /> Compilando...
                </>
              ) : (
                <>
                  <Play className="w-4 h-4" /> Iniciar Simulação de Build
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
                className="bg-emerald-500 h-full transition-all duration-300 ease-out"
                style={{ width: `${simProgress}%` }}
              />
            </div>
          </div>

          {/* Terminal Box */}
          <div className="bg-slate-900/90 rounded-2xl border border-slate-800 p-4 font-mono text-xs text-slate-300 min-h-[220px] max-h-[300px] overflow-y-auto space-y-2">
            {simLogs.length === 0 ? (
              <p className="text-slate-500 italic">Clique em "Iniciar Simulação de Build" para testar a pipeline do GitHub Actions.</p>
            ) : (
              simLogs.map((log, index) => (
                <div key={index} className="flex items-start gap-2">
                  <span className="text-slate-600 font-bold shrink-0">&gt;</span>
                  <span className={log.includes('SUCCESSFUL') || log.includes('Artifact') ? 'text-emerald-400 font-bold' : ''}>
                    {log}
                  </span>
                </div>
              ))
            )}
          </div>

          {/* Simulation Artifact Result */}
          {simComplete && (
            <div className="p-4 rounded-2xl bg-emerald-950/60 border border-emerald-800 flex flex-col sm:flex-row items-center justify-between gap-4 animate-fade-in">
              <div className="flex items-center gap-3">
                <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0" />
                <div>
                  <h5 className="font-bold text-sm text-emerald-200 font-mono">Build Concluído com Sucesso!</h5>
                  <p className="text-xs text-emerald-400 font-mono">
                    O artefato <code className="text-white">app-debug.apk</code> foi gerado e validado.
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <a
                  href="#download-sim"
                  onClick={(e) => {
                    e.preventDefault();
                    alert("No seu repositório real do GitHub, o download do arquivo 'BYD-Controller-Debug.apk' estará disponível diretamente na aba Actions > Summary.");
                  }}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-mono text-xs font-bold rounded-xl flex items-center gap-2 transition"
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

