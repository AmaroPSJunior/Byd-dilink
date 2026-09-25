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
  Info,
  Copy,
  Share2,
  FileText
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'inspector' | 'report_schema'>('overview');
  const [copied, setCopied] = useState(false);

  const sampleReportStructure = `===== BYD SETTING DEVICE & INTERIOR LIGHT DISCOVERY =====
STRICT SAFE READ-ONLY MODE ACTIVE

SUMMARY OF PREVIOUS HAL ANALYSES
--------------------------------
• BYDAutoLightDevice: FOUND (DeviceType = 1004 / 0x3EC) - Exterior/headlight focus (Daytime, HighBeam, LowBeam, Fog, Footwell).
• BYDAutoBodyworkDevice: FOUND (DeviceType = 1005 / 0x3ED) - Body hardware focus (Doors, windows, locks, bodywork).
• Current Deep Target: android.hardware.bydauto.setting.BYDAutoSettingDevice

SETTING CLASS
-------------
Target Class: android.hardware.bydauto.setting.BYDAutoSettingDevice
Status: FOUND
Modifiers: public
Package: android.hardware.bydauto.setting
Inheritance Hierarchy:
  [0] android.hardware.bydauto.setting.BYDAutoSettingDevice
  [1] android.hardware.bydauto.AbsBYDAutoDevice

SETTING INSTANCE
----------------
Status: FOUND / ACCESSIBLE
Obtained Via: getInstance(Context)

SETTING DEVICE TYPE
-------------------
getDevicetype:
  Decimal: 1006
  Hex: 0x3EE
getType:
  Decimal: 1006
  Hex: 0x3EE

SETTING FEATURE LIST
--------------------
getFeatureList() = null

SETTING FEATURE STRINGS
-----------------------
Discovered 24 FEATURE constants:
  FIELD: FEATURE_INTERIOR_LIGHT_DELAY => VALUE: "interior_light_delay_feature"
  FIELD: FEATURE_WELCOME_LIGHT => VALUE: "welcome_light_feature"

SETTING PERMISSIONS
-------------------
getGetPermission(): android.permission.BYDAUTO_SETTING_GET
getSetPermission(): android.permission.BYDAUTO_SETTING_SET

===== SETTING FULL METHOD LIST =====
Total Setting Methods Found Across Hierarchy: 268

METHOD [1/268]
DECLARING CLASS: android.hardware.bydauto.setting.BYDAutoSettingDevice
MODIFIERS: public
RETURN TYPE: int
METHOD NAME: getInteriorLightDelay
PARAMETER TYPES: ()

... (All 268 signatures printed without truncation)

===== SETTING FULL FIELD LIST =====
Total Setting Fields Found Across Hierarchy: 540

FIELD [1/540]
DECLARING CLASS: android.hardware.bydauto.setting.BYDAutoSettingDevice
MODIFIERS: public static final
TYPE: int
NAME: INTERIOR_LIGHT_DELAY_30S
VALUE: 30
DECIMAL: 30
HEX: 0x1E

... (All 540 fields printed with decimal and hex values)

===== SETTING INTERIOR / LIGHT / COURTESY CANDIDATES =====
Filtered Candidate Methods Found (18):
  • android.hardware.bydauto.setting.BYDAutoSettingDevice#public int getInteriorLightDelay() [READ CANDIDATE]
  • android.hardware.bydauto.setting.BYDAutoSettingDevice#public int setInteriorLightDelay(int) [WRITE METHOD (BLOCKED - NOT EXECUTED)]

===== OTHER RELEVANT BYDAUTO CLASSES =====
CLASS: android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice -> FOUND
CLASS: android.hardware.bydauto.cabin.BYDAutoCabinDevice -> FOUND

===== ERRORS =====
None. All reflection steps completed safely.

==================================================
END OF COMPLETE REPORT
==================================================
Characters: 2450
UTF-8 Bytes: 2450
Lines: 92`;

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
              <span>Export Features</span>
              <FileText className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="text-lg font-semibold text-emerald-400 flex items-center gap-1.5">
              <CheckCircle2 className="w-5 h-5" /> Export TXT & Parts
            </div>
            <div className="text-xs text-slate-400 font-mono mt-1">MediaStore & FileProvider</div>
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
                  <p>• Export Functions: <span className="text-emerald-400">Copy Full, Copy in Parts (~30k), Export TXT, Share TXT</span></p>
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
                  Use <strong className="text-white">EXPORTAR TXT</strong> or <strong className="text-white">COPIAR EM PARTES</strong> to safely retrieve complete reports without truncation.
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
                  <Lightbulb className="w-4 h-4" /> 3. Export Options
                </div>
                <p className="text-slate-400 leading-relaxed">
                  Provides 4 buttons: Copiar Integral, Copiar em Partes (~30k chars), Exportar TXT (Downloads/BYD-Diagnostics), and Compartilhar TXT via FileProvider.
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
                  Clipboard & Export Structure
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  This exact report structure is exported when using the export actions in the Android app.
                </p>
              </div>
              <button
                onClick={copyReportSchema}
                className="flex items-center gap-2 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 rounded-lg border border-slate-700 transition-colors"
              >
                {copied ? <CheckCircle2 className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
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
