import React, { useState } from 'react';
import { 
  ShieldCheck, 
  Cpu, 
  Car, 
  FileCode2, 
  CheckCircle2, 
  Download, 
  Terminal, 
  Search, 
  Lightbulb, 
  Smartphone,
  Lock,
  Layers,
  Sparkles,
  Info
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'inspector' | 'report_schema'>('overview');
  const [copied, setCopied] = useState(false);

  const sampleReportStructure = `==================================================
===== BYD INTERIOR LIGHT DISCOVERY =====
STRICT SAFE READ-ONLY
==================================================

LIGHT HAL SUMMARY
-----------------
BYDAutoLightDevice: FOUND
DeviceType: 1004 / 0x3EC
Interior/Dome explicit candidates: NONE (Focus shifted to Bodywork HAL)

BODYWORK CLASS
--------------
Target Class: android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
Status: FOUND
Modifiers: public
Package: android.hardware.bydauto.bodywork
Inheritance Hierarchy:
  [0] android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
  [1] android.hardware.bydauto.AbsBYDAutoDevice

BODYWORK INSTANCE
-----------------
Status: FOUND / ACCESSIBLE
Obtained Via: getInstance(Context)

BODYWORK DEVICE TYPE
--------------------
getDevicetype:
  Decimal: 1005
  Hex: 0x3ED
getType:
  Decimal: 1005
  Hex: 0x3ED

BODYWORK FEATURE LIST
---------------------
getFeatureList() = null

BODYWORK FEATURE STRINGS
------------------------
Discovered FEATURE constants:
  FIELD: FEATURE_INTERIOR_LIGHT => VALUE: "interior_light_feature"
  FIELD: FEATURE_DOME_LIGHT => VALUE: "dome_light_feature"

BODYWORK PERMISSIONS
--------------------
Permission: android.permission.BYDAUTO_BODYWORK_GET
  Declared in Manifest: YES
  PackageManager checkPermission: GRANTED
  Context.checkSelfPermission: GRANTED
  Protection Level: 0x2
  Permission Owner: com.byd.service

===== BODYWORK FULL METHOD LIST =====
Total Bodywork Methods Found: 42

METHOD [1/42]
DECLARING CLASS: android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
MODIFIERS: public
RETURN TYPE: int
METHOD NAME: getDomeLightState
PARAMETER TYPES: ()

... (All 42 signatures printed without truncation)

===== BODYWORK FULL FIELD LIST =====
Total Bodywork Fields Found: 128

FIELD [1/128]
DECLARING CLASS: android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
MODIFIERS: public static final
TYPE: int
NAME: DOME_LIGHT_ON
VALUE: 1
DECIMAL: 1
HEX: 0x1

... (All 128 fields printed with decimal and hex values)

===== INTERIOR / DOME / COURTESY CANDIDATES =====
Candidate Methods Found (4):
  • android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice#public int getDomeLightState() [READ CANDIDATE]
  • android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice#public int setDomeLightState(int) [WRITE METHOD (BLOCKED - NOT EXECUTED)]

===== OTHER RELEVANT BYDAUTO CLASSES =====
CLASS: android.hardware.bydauto.setting.BYDAutoSettingDevice -> FOUND
CLASS: android.hardware.bydauto.cabin.BYDAutoCabinDevice -> FOUND

===== ERRORS =====
None. All reflection steps completed safely.

===== END =====`;

  const copyReportSchema = () => {
    navigator.clipboard.writeText(sampleReportStructure);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Header Navigation */}
      <header className="bg-slate-900 border-b border-slate-800 px-6 py-4 sticky top-0 z-50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="bg-gradient-to-tr from-cyan-600 to-blue-600 p-2.5 rounded-xl shadow-lg shadow-cyan-950/50">
            <Car className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
              BYD DiLink 3.0 Diagnostic Lab
              <span className="text-xs px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 font-mono">
                Android Native APK
              </span>
            </h1>
            <p className="text-xs text-slate-400">
              BYD Light & Bodywork HAL Deep Inspector (Android 10 / SDK 29)
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-mono">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            STRICT READ-ONLY ACTIVE
          </div>
          <a
            href="/app/applet/.build-outputs/app-debug.apk"
            download="app-debug.apk"
            className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 text-white font-medium text-xs rounded-lg shadow-md transition-all active:scale-95 cursor-pointer"
          >
            <Download className="w-4 h-4" />
            Download APK
          </a>
        </div>
      </header>

      {/* Main Content Body */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 flex flex-col gap-6">
        
        {/* Status Highlights */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium mb-2">
              <span>Target Hardware</span>
              <Smartphone className="w-4 h-4 text-cyan-400" />
            </div>
            <div className="text-lg font-semibold text-white">BYD DiLink 3.0</div>
            <div className="text-xs text-slate-400 font-mono mt-1">Android 10 • API Level 29</div>
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium mb-2">
              <span>Light HAL Device</span>
              <Lightbulb className="w-4 h-4 text-amber-400" />
            </div>
            <div className="text-lg font-semibold text-amber-300">BYDAutoLightDevice</div>
            <div className="text-xs text-slate-400 font-mono mt-1">DeviceType: 1004 (0x3EC)</div>
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium mb-2">
              <span>Bodywork HAL Target</span>
              <Cpu className="w-4 h-4 text-indigo-400" />
            </div>
            <div className="text-lg font-semibold text-indigo-300">BYDAutoBodyworkDevice</div>
            <div className="text-xs text-slate-400 font-mono mt-1">Interior / Dome Light Target</div>
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium mb-2">
              <span>Execution Policy</span>
              <Lock className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="text-lg font-semibold text-emerald-400 flex items-center gap-1.5">
              <CheckCircle2 className="w-5 h-5" /> 100% Read-Only
            </div>
            <div className="text-xs text-slate-400 font-mono mt-1">0 Writes • 0 Hardware Mutated</div>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
          <button
            onClick={() => setActiveTab('overview')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-colors ${
              activeTab === 'overview'
                ? 'bg-slate-800 text-cyan-400 border border-cyan-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Layers className="w-4 h-4" />
            System Architecture
          </button>
          <button
            onClick={() => setActiveTab('inspector')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-colors ${
              activeTab === 'inspector'
                ? 'bg-slate-800 text-cyan-400 border border-cyan-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Search className="w-4 h-4" />
            Interior Light Strategy
          </button>
          <button
            onClick={() => setActiveTab('report_schema')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-colors ${
              activeTab === 'report_schema'
                ? 'bg-slate-800 text-cyan-400 border border-cyan-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Terminal className="w-4 h-4" />
            Report Output Schema
          </button>
        </div>

        {/* Tab 1: Overview */}
        {activeTab === 'overview' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 bg-slate-900/60 border border-slate-800 rounded-xl p-6 flex flex-col gap-4">
              <h2 className="text-md font-semibold text-white flex items-center gap-2">
                <FileCode2 className="w-5 h-5 text-cyan-400" />
                Native Android Application Overview
              </h2>
              <p className="text-xs text-slate-300 leading-relaxed">
                This project contains a native Android Kotlin application compiled into a standalone APK 
                (<code className="bg-slate-800 text-cyan-300 px-1.5 py-0.5 rounded font-mono">app-debug.apk</code>) 
                designed to run directly on the BYD DiLink central screen.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 my-2">
                <div className="bg-slate-950 p-3.5 rounded-lg border border-slate-800">
                  <div className="text-xs font-semibold text-slate-200 mb-1">BYDAutoLightDevice</div>
                  <p className="text-[11px] text-slate-400">
                    Controls exterior lights (high beam, low beam, DRL, turn lights, fog lights, footwell).
                    Confirmed DeviceType: 1004 (0x3EC).
                  </p>
                </div>
                <div className="bg-slate-950 p-3.5 rounded-lg border border-slate-800">
                  <div className="text-xs font-semibold text-indigo-300 mb-1">BYDAutoBodyworkDevice</div>
                  <p className="text-[11px] text-slate-400">
                    Controls body hardware, dome lights, ceiling lamps, doors, courtesy lights, and interior cabin features.
                  </p>
                </div>
              </div>

              <div className="bg-slate-950/80 border border-slate-800 rounded-lg p-4 font-mono text-xs">
                <div className="text-cyan-400 font-semibold mb-2 flex items-center gap-2">
                  <Terminal className="w-4 h-4" />
                  Gradle Android Build Completed Successfully
                </div>
                <div className="text-slate-400 space-y-1 text-[11px]">
                  <p>• Output Location: <span className="text-emerald-400">/app/applet/.build-outputs/app-debug.apk</span></p>
                  <p>• Module Target: <span className="text-slate-200">:app (com.byd.carcontrol)</span></p>
                  <p>• SQLite Migrated: <span className="text-emerald-400">DATABASE_VERSION = 2 (Fixed 'exists' keyword)</span></p>
                  <p>• Strict Read-Only Policy: <span className="text-emerald-400">ENABLED</span></p>
                </div>
              </div>
            </div>

            <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 flex flex-col gap-4">
              <h2 className="text-md font-semibold text-white flex items-center gap-2">
                <Smartphone className="w-5 h-5 text-indigo-400" />
                Installation Guide on BYD
              </h2>
              <ol className="text-xs text-slate-300 space-y-3 list-decimal list-inside">
                <li className="leading-relaxed">
                  Download <strong className="text-cyan-400">app-debug.apk</strong> using the top button.
                </li>
                <li className="leading-relaxed">
                  Copy the file to a FAT32 formatted USB drive.
                </li>
                <li className="leading-relaxed">
                  Plug the USB drive into the BYD central media unit.
                </li>
                <li className="leading-relaxed">
                  Open File Manager in DiLink and install <code className="text-xs bg-slate-800 text-slate-200 px-1 py-0.5 rounded">com.byd.carcontrol</code>.
                </li>
                <li className="leading-relaxed">
                  Launch the app on the vehicle and press <strong className="text-white">EXECUTAR DIAGNÓSTICO</strong>.
                </li>
                <li className="leading-relaxed">
                  Click <strong className="text-white">COPIAR RELATÓRIO</strong> to copy full reflection signatures to clipboard.
                </li>
              </ol>
            </div>
          </div>
        )}

        {/* Tab 2: Inspector Strategy */}
        {activeTab === 'inspector' && (
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 flex flex-col gap-4">
            <h2 className="text-md font-semibold text-white flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-amber-400" />
              Interior Courtesy Light Discovery Strategy
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs text-slate-300">
              <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-2">
                <div className="font-semibold text-cyan-400 flex items-center gap-1.5">
                  <Search className="w-4 h-4" /> 1. Class Hierarchy Reflection
                </div>
                <p className="text-slate-400 leading-relaxed">
                  Traverses <code className="text-slate-200">BYDAutoBodyworkDevice</code> up to <code className="text-slate-200">java.lang.Object</code>.
                  Extracts all declared methods, fields, constructors, and interfaces without filtering or skipping private members.
                </p>
              </div>

              <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-2">
                <div className="font-semibold text-emerald-400 flex items-center gap-1.5">
                  <Lock className="w-4 h-4" /> 2. Full Member Extraction
                </div>
                <p className="text-slate-400 leading-relaxed">
                  Outputs every method signature with modifiers, return type, name, and parameters.
                  Outputs every field with value, decimal, and hexadecimal representations for static and instance fields.
                </p>
              </div>

              <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-2">
                <div className="font-semibold text-indigo-400 flex items-center gap-1.5">
                  <Lightbulb className="w-4 h-4" /> 3. Courtesy Light Target Keywords
                </div>
                <p className="text-slate-400 leading-relaxed">
                  Filters method and field names for keywords: <code className="text-indigo-300 font-mono">ROOM</code>, <code className="text-indigo-300 font-mono">DOME</code>, <code className="text-indigo-300 font-mono">COURTESY</code>, <code className="text-indigo-300 font-mono">READING</code>, <code className="text-indigo-300 font-mono">CEILING</code>, <code className="text-indigo-300 font-mono">ROOF</code>, <code className="text-indigo-300 font-mono">INTERIOR</code>, <code className="text-indigo-300 font-mono">INSIDE</code>, <code className="text-indigo-300 font-mono">DOOR</code>.
                </p>
              </div>

              <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-2">
                <div className="font-semibold text-rose-400 flex items-center gap-1.5">
                  <ShieldCheck className="w-4 h-4" /> 4. Write Methods Blocked
                </div>
                <p className="text-slate-400 leading-relaxed">
                  Any discovered write candidates (containing <code className="text-rose-300 font-mono">set</code> or <code className="text-rose-300 font-mono">write</code>) are explicitly tagged as <code className="text-rose-400 font-mono">[WRITE METHOD (BLOCKED - NOT EXECUTED)]</code>.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: Report Schema */}
        {activeTab === 'report_schema' && (
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-md font-semibold text-white flex items-center gap-2">
                  <Terminal className="w-5 h-5 text-cyan-400" />
                  Clipboard Report Structure
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  This exact report structure is copied to clipboard when pressing "COPIAR RELATÓRIO" in the Android app.
                </p>
              </div>
              <button
                onClick={copyReportSchema}
                className="flex items-center gap-2 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 rounded-lg border border-slate-700 transition-colors"
              >
                {copied ? <CheckCircle2 className="w-4 h-4 text-emerald-400" /> : <Terminal className="w-4 h-4" />}
                {copied ? 'Copied!' : 'Copy Schema'}
              </button>
            </div>

            <pre className="bg-slate-950 p-4 rounded-lg border border-slate-800 font-mono text-xs text-slate-300 overflow-x-auto max-h-[480px] leading-relaxed">
              {sampleReportStructure}
            </pre>
          </div>
        )}

      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800 px-6 py-4 bg-slate-900/80 text-xs text-slate-500 flex items-center justify-between">
        <div>BYD DiLink Car Control Lab • Android Native Module :app</div>
        <div className="flex items-center gap-2 font-mono text-[11px]">
          <Info className="w-3.5 h-3.5 text-cyan-500" />
          Strict Safe Read-Only • 0 Hardware Write Executions
        </div>
      </footer>
    </div>
  );
}
