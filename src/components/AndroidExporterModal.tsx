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
  ArrowRight
} from 'lucide-react';
import {
  ANDROID_MANIFEST_XML,
  MAIN_ACTIVITY_JAVA,
  BYD_DILINK_SERVICE_HELPER_JAVA,
  BUILD_GRADLE,
  GITHUB_ACTIONS_WORKFLOW
} from '../data/dilinkCodeTemplates';

export const AndroidExporterModal: React.FC = () => {
  const [activeFile, setActiveFile] = useState<
    'manifest' | 'mainActivity' | 'serviceHelper' | 'gradle' | 'workflow'
  >('mainActivity');

  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const fileMap = {
    manifest: { name: 'AndroidManifest.xml', code: ANDROID_MANIFEST_XML, lang: 'xml' },
    mainActivity: { name: 'MainActivity.java', code: MAIN_ACTIVITY_JAVA, lang: 'java' },
    serviceHelper: { name: 'BYDDiLinkServiceHelper.java', code: BYD_DILINK_SERVICE_HELPER_JAVA, lang: 'java' },
    gradle: { name: 'app/build.gradle', code: BUILD_GRADLE, lang: 'groovy' },
    workflow: { name: '.github/workflows/build-apk.yml', code: GITHUB_ACTIONS_WORKFLOW, lang: 'yaml' },
  };

  const currentFileObj = fileMap[activeFile];

  const handleCopyCode = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
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
              <h3 className="font-bold text-white text-base font-mono uppercase tracking-wide flex items-center gap-2">
                Gerador de Projeto Android Native APK & GitHub Actions
              </h3>
              <p className="text-xs text-slate-400 font-mono">
                Código Java/Android completo pronto para compilar e testar na multimídia do seu BYD.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => handleCopyCode(currentFileObj.code, activeFile)}
              className="flex items-center gap-2 px-4 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl font-mono text-xs font-bold transition-all shadow-lg shadow-emerald-900/40"
            >
              {copiedKey === activeFile ? (
                <>
                  <Check className="w-4 h-4 text-white" /> Copiado!
                </>
              ) : (
                <>
                  <Copy className="w-4 h-4" /> Copiar {currentFileObj.name}
                </>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* STEP BY STEP GUIDE ON HOW TO BUILD APK ON GITHUB */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          {
            step: '01',
            title: 'Copie os Arquivos',
            desc: 'Use os seletores abaixo para salvar o código na estrutura standard de um projeto Android Studio.',
            icon: FileCode
          },
          {
            step: '02',
            title: 'Envie para o GitHub',
            desc: 'Faça o commit para o seu repositório GitHub incluindo a pasta `.github/workflows/build-apk.yml`.',
            icon: Github
          },
          {
            step: '03',
            title: 'Compilação Automática',
            desc: 'O GitHub Actions gerará o arquivo `.apk` compilado na aba "Actions" em ~2 minutos.',
            icon: Terminal
          },
          {
            step: '04',
            title: 'Instale no BYD',
            desc: 'Transfira o arquivo `app-debug.apk` para um pendrive FAT32 ou via Aurora Store e instale no carro.',
            icon: Smartphone
          }
        ].map((st) => (
          <div key={st.step} className="bg-slate-900/80 p-4 rounded-2xl border border-slate-800 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-mono font-bold text-xs text-emerald-400 bg-emerald-950 px-2.5 py-0.5 rounded-full border border-emerald-800">
                PASSO {st.step}
              </span>
              <st.icon className="w-4 h-4 text-slate-500" />
            </div>
            <h4 className="font-bold text-sm text-slate-200 font-mono">{st.title}</h4>
            <p className="text-xs text-slate-400 leading-relaxed font-sans">{st.desc}</p>
          </div>
        ))}
      </div>

      {/* FILE CODE VIEWER */}
      <div className="bg-slate-950 rounded-3xl p-6 border border-slate-800 shadow-2xl">
        
        {/* File Tabs */}
        <div className="flex items-center gap-2 overflow-x-auto pb-3 mb-4 border-b border-slate-800 scrollbar-none">
          {(Object.keys(fileMap) as (keyof typeof fileMap)[]).map((key) => (
            <button
              key={key}
              onClick={() => setActiveFile(key)}
              className={`px-4 py-2 rounded-xl font-mono text-xs font-semibold flex items-center gap-2 transition-all shrink-0 ${
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

        {/* Code View Area */}
        <div className="relative">
          <div className="absolute top-3 right-3 z-10">
            <button
              onClick={() => handleCopyCode(currentFileObj.code, activeFile)}
              className="p-2 bg-slate-800 hover:bg-slate-700 rounded-xl text-slate-300 font-mono text-xs flex items-center gap-1.5 border border-slate-700 shadow"
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

          <pre className="p-5 bg-slate-900/90 rounded-2xl border border-slate-800/80 text-emerald-300 text-xs font-mono overflow-x-auto leading-relaxed max-h-[500px]">
            <code>{currentFileObj.code}</code>
          </pre>
        </div>

      </div>

    </div>
  );
};
