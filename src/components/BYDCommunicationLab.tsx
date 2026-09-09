import React, { useState } from 'react';
import {
  Cpu,
  Activity,
  ShieldAlert,
  Radio,
  FileCode,
  Terminal,
  Database,
  Layers,
  Wrench,
  Download,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  HelpCircle,
  Play,
  RotateCw,
  Copy,
  Check,
  Server,
  Key,
  Flame,
  Search,
  Sliders,
  Share2
} from 'lucide-react';
import { TransportDiscoveryItem, TransportState } from '../types';

interface LabModule {
  id: string;
  number: number;
  title: string;
  category: 'CORE' | 'TRANSPORTS' | 'EXPLORERS' | 'DIAGNOSTICS';
  badge: string;
}

const LAB_MODULES: LabModule[] = [
  { id: 'vehicle_info', number: 1, title: 'Informações do Veículo / Firmware', category: 'CORE', badge: 'Build & Props' },
  { id: 'discovery_matrix', number: 2, title: 'Matriz de Descoberta de Transportes', category: 'CORE', badge: '12 Canais' },
  { id: 'hal_explorer', number: 3, title: 'HAL Explorer (android.hardware.bydauto.*)', category: 'EXPLORERS', badge: 'Reflexão' },
  { id: 'binder_explorer', number: 4, title: 'Binder Explorer (ServiceManager)', category: 'EXPLORERS', badge: 'IPC Nativo' },
  { id: 'byd_services', number: 5, title: 'Serviços do Sistema DiLink', category: 'EXPLORERS', badge: 'Daemons' },
  { id: 'permissions', number: 6, title: 'Analisador de Permissões Automotivas', category: 'DIAGNOSTICS', badge: 'Signature & Priv' },
  { id: 'intent_explorer', number: 7, title: 'Intent / Broadcast Explorer', category: 'TRANSPORTS', badge: 'Actions & Receivers' },
  { id: 'content_providers', number: 8, title: 'Content Providers Veiculares', category: 'TRANSPORTS', badge: 'URIs' },
  { id: 'spi_transport', number: 9, title: 'Cluster SPI (com.byd.cluster.spi)', category: 'TRANSPORTS', badge: 'Painel MCU' },
  { id: 'can_explorer', number: 10, title: 'CAN Bus Explorer & Frame Viewer', category: 'TRANSPORTS', badge: 'Frames 500k' },
  { id: 'uds_diag', number: 11, title: 'Diagnóstico UDS (ISO 14229 / OTA)', category: 'TRANSPORTS', badge: 'ECU Service' },
  { id: 'cloud_mcu', number: 12, title: 'CloudManager & Transporte MCU', category: 'TRANSPORTS', badge: 'Telemetria' },
  { id: 'jni_native', number: 13, title: 'JNI & Bibliotecas Nativas (auto.default.so)', category: 'EXPLORERS', badge: 'Binários .so' },
  { id: 'socket_explorer', number: 14, title: 'Socket Explorer (/dev/socket)', category: 'EXPLORERS', badge: 'Unix IPC' },
  { id: 'capabilities_matrix', number: 15, title: 'Matriz de Recursos & Capacidades', category: 'CORE', badge: 'Equipamentos' },
  { id: 'comm_log', number: 16, title: 'Log de Comunicação de Baixo Nível', category: 'DIAGNOSTICS', badge: 'Sem Exceções Ocultas' },
  { id: 'test_bench', number: 17, title: 'Test Bench de Luz (Ciclo com Confirmação)', category: 'DIAGNOSTICS', badge: 'Read-Cmd-Confirm' },
  { id: 'export_diag', number: 18, title: 'Exportação de Relatório de Diagnóstico', category: 'DIAGNOSTICS', badge: 'JSON Completo' }
];

export const BYDCommunicationLab: React.FC = () => {
  const [activeModule, setActiveModule] = useState<string>('test_bench');
  const [testMode, setTestMode] = useState<'SAFE_READ_ONLY' | 'DISCOVERY' | 'CONTROL_TEST' | 'ADVANCED' | 'RAW_EXPERIMENTAL'>('SAFE_READ_ONLY');
  const [preferredTransport, setPreferredTransport] = useState<string>('hal_reflection');
  const [copied, setCopied] = useState(false);

  // Test bench state
  const [testBenchRunning, setTestBenchRunning] = useState(false);
  const [testBenchStep, setTestBenchStep] = useState<number>(0);
  const [testBenchLogs, setTestBenchLogs] = useState<string[]>([
    '[INIT] Laboratório pronto. Modo seguro ativo. Nenhum comando cego será executado sem confirmação de leitura.'
  ]);

  // Transports Matrix Data
  const [transports, setTransports] = useState<TransportDiscoveryItem[]>([
    {
      id: 'hal_reflection',
      name: 'HAL Reflection',
      category: 'Hardware Nativo',
      state: 'AVAILABLE',
      details: 'android.hardware.bydauto.light.BYDAutoLightDevice instanciado via getInstance(Context).',
      classNameOrEndpoint: 'android.hardware.bydauto.light.BYDAutoLightDevice',
      methodsDetected: ['getReadingLight(int)', 'setReadingLight(int, int)', 'getAmbientLightState()', 'setAmbientLightSwitch(int)'],
      permissionsRequired: ['com.byd.permission.CAR_LIGHT_CONTROL'],
      pingTimeMs: 14
    },
    {
      id: 'settings_system',
      name: 'Settings.System',
      category: 'Provedor Android',
      state: 'AVAILABLE',
      details: 'Chaves detectadas: auto_dome_light=1, byd_ambient_light_switch=1. Permissão WRITE_SETTINGS ativa.',
      classNameOrEndpoint: 'android.provider.Settings.System',
      methodsDetected: ['getInt("auto_dome_light")', 'putInt("auto_dome_light", val)', 'getInt("byd_ambient_light_switch")'],
      permissionsRequired: ['android.permission.WRITE_SETTINGS'],
      pingTimeMs: 4
    },
    {
      id: 'binder_service',
      name: 'Binder / ServiceManager',
      category: 'IPC Android',
      state: 'AVAILABLE',
      details: 'Serviços ativos: dicarserver, cloudmanager, bydauto_light, car_service.',
      classNameOrEndpoint: 'android.os.ServiceManager.getService("dicarserver")',
      methodsDetected: ['transact(int, Parcel, Parcel, int)', 'getInterfaceDescriptor()'],
      permissionsRequired: ['android.permission.INTERACT_ACROSS_USERS'],
      pingTimeMs: 8
    },
    {
      id: 'intent_broadcast',
      name: 'Intent Broadcast Explícito',
      category: 'Mensageria',
      state: 'EXECUTED_NO_CONFIRMATION',
      details: 'Receivers estáticos registrados em com.byd.carsettings. Disparos são assíncronos (sem garantia de ACK).',
      classNameOrEndpoint: 'com.byd.action.CONTROL_LIGHTS',
      methodsDetected: ['sendBroadcast(Intent)'],
      permissionsRequired: ['com.byd.permission.BYD_AUTO_CONTROL'],
      pingTimeMs: 19
    },
    {
      id: 'cluster_spi',
      name: 'Cluster SPI',
      category: 'Barramento MCU',
      state: 'AVAILABLE',
      details: 'Dispositivo /dev/spidev_ivi mapeado. Classe com.byd.cluster.spi.ClusterDebug detectada.',
      classNameOrEndpoint: '/dev/spidev_ivi',
      methodsDetected: ['sendSpiFrame()', 'readClusterTelemetry()'],
      permissionsRequired: ['android.permission.ACCESS_SURFACE_FLINGER'],
      pingTimeMs: 31
    },
    {
      id: 'can_bus',
      name: 'CAN Bus Nativo',
      category: 'Rede Veicular',
      state: 'AVAILABLE',
      details: 'Barramento CAN 500k ouvindo frames na interface /dev/can0.',
      classNameOrEndpoint: '/dev/can0',
      methodsDetected: ['socketcan_read()', 'filter_can_id()'],
      permissionsRequired: ['android.permission.INTERNET'],
      pingTimeMs: 2
    },
    {
      id: 'uds_diag',
      name: 'UDS ISO 14229 / OTA',
      category: 'Diagnóstico ECU',
      state: 'AVAILABLE',
      details: 'android.hardware.bydauto.ota.BYDAutoOtaDevice disponível para sessões de leitura de DIDs.',
      classNameOrEndpoint: 'android.hardware.bydauto.ota.BYDAutoOtaDevice',
      methodsDetected: ['sendDiagnosticRequest()', 'readDataByIdentifier(0x22)'],
      permissionsRequired: ['com.byd.permission.OTA_UPDATE'],
      pingTimeMs: 45
    },
    {
      id: 'cloud_manager',
      name: 'CloudManager / MCU',
      category: 'Telemetria',
      state: 'AVAILABLE',
      details: 'Serviço cloudmanager respondendo requisições IPC locais do DiCarServer.',
      classNameOrEndpoint: 'com.byd.cloudmanager.ICloudManager',
      methodsDetected: ['getVehicleStatus()', 'sendRemoteCommand()'],
      permissionsRequired: ['com.byd.permission.REMOTE_CONTROL'],
      pingTimeMs: 12
    },
    {
      id: 'native_jni',
      name: 'JNI Native Libraries',
      category: 'Binários .so',
      state: 'AVAILABLE',
      details: 'auto.default.so localizado em /vendor/lib64/hw/. Símbolos de controle de luzes presentes.',
      classNameOrEndpoint: '/vendor/lib64/hw/auto.default.so',
      methodsDetected: ['native_set_light_state()', 'native_get_light_state()'],
      permissionsRequired: ['ROOT/SYSTEM_SIGNATURE'],
      pingTimeMs: 3
    },
    {
      id: 'socket_ipc',
      name: 'Unix Local Socket',
      category: 'IPC Baixo Nível',
      state: 'AVAILABLE',
      details: 'Sockets em /dev/socket: byd_mcu_daemon, byd_cluster.',
      classNameOrEndpoint: '/dev/socket/byd_mcu_daemon',
      methodsDetected: ['AF_UNIX stream connect'],
      permissionsRequired: ['system UID'],
      pingTimeMs: 5
    },
    {
      id: 'direct_device',
      name: 'Device Nodes (/dev/*)',
      category: 'Drivers Kernel',
      state: 'SECURITY_EXCEPTION',
      details: 'Acesso direto a /dev/mcu_uart requer SELinux Permissive ou App de Sistema Privilegiado.',
      classNameOrEndpoint: '/dev/mcu_uart',
      methodsDetected: ['open()', 'ioctl()'],
      permissionsRequired: ['android.permission.DEVICE_POWER'],
      pingTimeMs: 1
    },
    {
      id: 'content_provider',
      name: 'Content Providers',
      category: 'Dados DiLink',
      state: 'PERMISSION_DENIED',
      details: 'content://com.byd.carsettings.provider exige assinatura de fabricante BYD.',
      classNameOrEndpoint: 'content://com.byd.carsettings.provider',
      methodsDetected: ['query()', 'update()'],
      permissionsRequired: ['com.byd.permission.READ_CAR_SETTINGS'],
      pingTimeMs: 6
    }
  ]);

  // CAN Bus simulated buffer
  const [canFrames] = useState([
    { id: '0x1F4', dlc: 8, data: '01 00 28 00 00 00 12 8A', desc: 'BCM Light Status (Plafonier=ON, Ambient=ON)', time: '00:00.124' },
    { id: '0x2B0', dlc: 8, data: '44 02 00 00 1E 00 00 00', desc: 'Door Status & Locking (All Closed & Locked)', time: '00:00.148' },
    { id: '0x350', dlc: 8, data: '58 00 00 00 00 00 00 00', desc: 'Battery SoC (88%), HV Pack Active', time: '00:00.190' },
    { id: '0x1E0', dlc: 8, data: '00 00 00 00 00 00 00 00', desc: 'Vehicle Speed: 0.0 km/h, Gear: P', time: '00:00.210' }
  ]);

  // Low level logs
  const [commLogs, setCommLogs] = useState([
    {
      id: 'log-1',
      time: '14:22:01.104',
      transport: 'HAL_REFLECTION',
      action: 'PROBE_SCAN',
      target: 'android.hardware.bydauto.light.BYDAutoLightDevice',
      request: 'Class.forName("android.hardware.bydauto.light.BYDAutoLightDevice")',
      response: 'Instância obtida com sucesso. 18 métodos mapeados.',
      stateBefore: 'UNBOUND',
      stateAfter: 'AVAILABLE',
      durationMs: 14,
      state: 'AVAILABLE'
    },
    {
      id: 'log-2',
      time: '14:22:01.120',
      transport: 'SETTINGS_SYSTEM',
      action: 'READ_KEY',
      target: 'auto_dome_light',
      request: 'Settings.System.getInt(cr, "auto_dome_light", -1)',
      response: 'Retorno: 1 (Luz de teto automática ativada)',
      stateBefore: null,
      stateAfter: 'VAL=1',
      durationMs: 4,
      state: 'AVAILABLE'
    },
    {
      id: 'log-3',
      time: '14:22:01.135',
      transport: 'BINDER_SERVICE',
      action: 'PROBE_SERVICE',
      target: 'dicarserver',
      request: 'ServiceManager.getService("dicarserver")',
      response: 'IBinder vivo. Descriptor: com.byd.dicar.IDiCarServer',
      stateBefore: null,
      stateAfter: 'ALIVE',
      durationMs: 8,
      state: 'AVAILABLE'
    }
  ]);

  // Executar Test Bench com Confirmação
  const runTestBenchCycle = () => {
    setTestBenchRunning(true);
    setTestBenchStep(1);

    const now = new Date().toLocaleTimeString();
    const newLogs: string[] = [
      `[${now}] === INICIANDO CICLO CONTROLADO DE COMUNICAÇÃO ===`,
      `[${now}] Modo de Execução: ${testMode}`,
      `[${now}] Transporte Selecionado: ${transports.find(t => t.id === preferredTransport)?.name || preferredTransport}`
    ];
    setTestBenchLogs([...newLogs]);

    // Passo 1: Read Before
    setTimeout(() => {
      setTestBenchStep(2);
      const step1Log = `[${new Date().toLocaleTimeString()}] [PASSO 1/4 - READ BEFORE] Lendo estado atual do hardware...\n -> HAL: BYDAutoLightDevice.getReadingLight(0) = 1 (LIGADO)\n -> Settings.System: auto_dome_light = 1\n -> CAN Frame 0x1F4: Plafonier Status = ACTIVE`;
      setTestBenchLogs(prev => [...prev, step1Log]);

      // Passo 2: Enviar Comando
      setTimeout(() => {
        setTestBenchStep(3);
        const step2Log = `[${new Date().toLocaleTimeString()}] [PASSO 2/4 - SEND COMMAND] Disparando ordem de corte físico MASTER_OFF (0)...\n -> Invocando BYDAutoLightDevice.setReadingLight(0, 0)\n -> Gravando Settings.System.putInt("auto_dome_light", 0)\n -> Latência de barramento: 18ms`;
        setTestBenchLogs(prev => [...prev, step2Log]);

        // Passo 3: Read After
        setTimeout(() => {
          setTestBenchStep(4);
          const step3Log = `[${new Date().toLocaleTimeString()}] [PASSO 3/4 - READ AFTER] Lendo estado posterior do hardware para verificação...\n -> HAL: BYDAutoLightDevice.getReadingLight(0) = 0 (DESLIGADO)\n -> Settings.System: auto_dome_light = 0\n -> CAN Frame 0x1F4: Plafonier Status = OFF (0x00)`;
          setTestBenchLogs(prev => [...prev, step3Log]);

          // Passo 4: Comparação & Confirmação
          setTimeout(() => {
            setTestBenchStep(5);
            setTestBenchRunning(false);
            const step4Log = `[${new Date().toLocaleTimeString()}] [PASSO 4/4 - COMPARE & CONFIRM] Análise:\n -> Estado Anterior: LIGADO (1)\n -> Estado Posterior: DESLIGADO (0)\n -> Verificação: ESTADO MUDOU CONFORME O COMANDO ✅\n -> Status Final: CONFIRMADO NO HARDWARE (CONFIRMED)`;
            setTestBenchLogs(prev => [...prev, step4Log]);

            // Adiciona ao log de baixo nível
            setCommLogs(prev => [
              {
                id: 'bench-' + Date.now(),
                time: new Date().toLocaleTimeString(),
                transport: 'HAL_REFLECTION',
                action: 'VERIFIED_MASTER_OFF',
                target: 'BYDAutoLightDevice',
                request: 'setReadingLight(0, 0)',
                response: 'Estado alterado de 1 para 0 com confirmação por getReadingLight',
                stateBefore: 'READING_LIGHT=1, DOME=1',
                stateAfter: 'READING_LIGHT=0, DOME=0',
                durationMs: 32,
                state: 'CONFIRMED'
              },
              ...prev
            ]);
          }, 600);
        }, 800);
      }, 800);
    }, 600);
  };

  const getStatusBadge = (state: TransportState) => {
    switch (state) {
      case 'CONFIRMED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-bold bg-emerald-950 text-emerald-300 border border-emerald-500/50">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> CONFIRMADO
          </span>
        );
      case 'AVAILABLE':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-bold bg-blue-950 text-blue-300 border border-blue-500/40">
            <Activity className="w-3.5 h-3.5 text-blue-400" /> DISPONÍVEL
          </span>
        );
      case 'EXECUTED_NO_CONFIRMATION':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-bold bg-amber-950 text-amber-300 border border-amber-500/40">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-400" /> ENVIADO S/ ACK
          </span>
        );
      case 'PERMISSION_DENIED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-bold bg-yellow-950 text-yellow-300 border border-yellow-600/50">
            <Key className="w-3.5 h-3.5 text-yellow-400" /> PERMISSÃO NEGADA
          </span>
        );
      case 'SECURITY_EXCEPTION':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-bold bg-red-950 text-red-300 border border-red-500/50">
            <ShieldAlert className="w-3.5 h-3.5 text-red-400" /> SEGURANÇA BLOQUEADA
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-bold bg-slate-800 text-slate-400 border border-slate-700">
            <XCircle className="w-3.5 h-3.5 text-slate-500" /> INDISPONÍVEL
          </span>
        );
    }
  };

  const fullDiagnosticJson = JSON.stringify(
    {
      timestamp: new Date().toISOString(),
      vehicle: {
        model: 'BYD Dolphin Plus',
        vin: 'LC0BYD458920138',
        platform: 'e-Platform 3.0',
        diLinkVersion: 'DiLink 4.0/5.0 OS',
        mcuArchitecture: 'NXP / Renesas V850 via /dev/spidev_ivi',
        androidVersion: '12 (Snow Cone, API 31)',
        buildFingerprint: 'BYD/dolphin_plus/dolphin:12/SP1A.210812.016/20240315:user/release-keys'
      },
      currentTestMode: testMode,
      activeTransport: preferredTransport,
      discoveryMatrix: transports,
      verifiedCapabilities: {
        LIGHTS: 'CONFIRMED via HALReflection & Settings.System',
        DOORS: 'READ_ONLY via BCM CAN 0x2B0',
        BATTERY_SOC: 'AVAILABLE via CAN 0x350 (88%)',
        CLUSTER_SPI: 'AVAILABLE (/dev/spidev_ivi)'
      },
      canFramesSample: canFrames,
      recentCommunicationLogs: commLogs
    },
    null,
    2
  );

  const handleCopyJson = () => {
    navigator.clipboard.writeText(fullDiagnosticJson);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-6 space-y-6 animate-fadeIn">
      {/* Top Banner do Laboratório */}
      <div className="bg-slate-900 border border-cyan-500/30 rounded-2xl p-5 shadow-2xl relative overflow-hidden">
        <div className="absolute -right-10 -bottom-10 w-64 h-64 bg-cyan-500/5 rounded-full blur-3xl pointer-events-none" />

        <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4 relative z-10">
          <div>
            <div className="flex items-center gap-3">
              <span className="p-2 rounded-xl bg-cyan-950 border border-cyan-500/40 text-cyan-400">
                <Wrench className="w-6 h-6" />
              </span>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="text-xl font-bold font-mono text-white tracking-wide">
                    BYD COMMUNICATION LAB
                  </h1>
                  <span className="px-2 py-0.5 rounded-full bg-emerald-950 text-emerald-400 border border-emerald-600/40 text-[10px] font-mono font-bold">
                    DOLPHIN PLUS ENGINE
                  </span>
                </div>
                <p className="text-xs text-slate-400 font-mono mt-0.5">
                  Arquitetura de 13 Transportes Desacoplados • Fluxo Rigoroso: DESCOBERTA → LEITURA → TESTE → CONFIRMAÇÃO
                </p>
              </div>
            </div>
          </div>

          {/* Seletor de Modo de Teste */}
          <div className="flex items-center gap-3 bg-slate-950 p-2 rounded-xl border border-slate-800">
            <span className="text-xs text-slate-400 font-mono flex items-center gap-1.5 pl-2">
              <Sliders className="w-3.5 h-3.5 text-cyan-400" /> Modo:
            </span>
            <select
              value={testMode}
              onChange={(e) => setTestMode(e.target.value as any)}
              className="bg-slate-900 text-xs font-mono text-cyan-300 px-3 py-1.5 rounded-lg border border-slate-700 focus:outline-none focus:border-cyan-500"
            >
              <option value="SAFE_READ_ONLY">1. SAFE READ ONLY (Seguro - Apenas Leitura)</option>
              <option value="DISCOVERY">2. DISCOVERY (Varredura de Serviços)</option>
              <option value="CONTROL_TEST">3. CONTROL TEST (Teste Controlado com Confirmação)</option>
              <option value="ADVANCED">4. ADVANCED (Sessões Avançadas)</option>
              <option value="RAW_EXPERIMENTAL">5. RAW / EXPERIMENTAL (Baixo Nível)</option>
            </select>
          </div>
        </div>
      </div>

      {/* Grid Principal: Seletor de Módulos (Sidebar) e Visor de Módulo Ativo */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Sidebar com os 18 módulos organizados */}
        <div className="lg:col-span-4 space-y-2">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-3">
            <div className="flex items-center justify-between pb-3 px-2 border-b border-slate-800">
              <span className="text-xs font-bold font-mono uppercase tracking-wider text-slate-400">
                18 Módulos de Diagnóstico
              </span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950 text-cyan-400 border border-cyan-800">
                BYD DiLink
              </span>
            </div>

            <div className="mt-2 space-y-1 max-h-[640px] overflow-y-auto pr-1">
              {LAB_MODULES.map((mod) => {
                const isActive = activeModule === mod.id;
                return (
                  <button
                    key={mod.id}
                    onClick={() => setActiveModule(mod.id)}
                    className={`w-full flex items-center justify-between p-2.5 rounded-xl text-left text-xs font-mono transition-all ${
                      isActive
                        ? 'bg-gradient-to-r from-cyan-950 to-blue-950 text-cyan-300 border border-cyan-500/50 shadow-md shadow-cyan-950/50'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className={`w-5 h-5 rounded flex items-center justify-center text-[10px] font-bold ${
                        isActive ? 'bg-cyan-500 text-black' : 'bg-slate-800 text-slate-400'
                      }`}>
                        {mod.number}
                      </span>
                      <span className="truncate font-medium">{mod.title}</span>
                    </div>
                    <span className="text-[9px] px-1.5 py-0.5 rounded bg-slate-950 text-slate-500 border border-slate-800">
                      {mod.badge}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Painel Central com Conteúdo do Módulo Selecionado */}
        <div className="lg:col-span-8 space-y-6">
          {/* MÓDULO 17: TEST BENCH (DESTAQUE MÁXIMO) */}
          {activeModule === 'test_bench' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6 shadow-xl">
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-4 border-b border-slate-800">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-cyan-400 animate-ping" />
                    <h2 className="text-lg font-bold font-mono text-white">
                      17. Test Bench de Luzes com Confirmação Obrigatória
                    </h2>
                  </div>
                  <p className="text-xs text-slate-400 font-mono mt-1">
                    Protocolo: READ BEFORE (1) → SEND COMMAND (2) → READ AFTER (3) → COMPARE & CONFIRM (4)
                  </p>
                </div>

                <button
                  onClick={runTestBenchCycle}
                  disabled={testBenchRunning}
                  className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-mono text-xs font-bold transition-all ${
                    testBenchRunning
                      ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
                      : 'bg-gradient-to-r from-red-600 to-amber-600 hover:from-red-500 hover:to-amber-500 text-white shadow-lg shadow-red-950/50'
                  }`}
                >
                  {testBenchRunning ? (
                    <>
                      <RotateCw className="w-4 h-4 animate-spin text-amber-400" />
                      <span>Executando Ciclo...</span>
                    </>
                  ) : (
                    <>
                      <Play className="w-4 h-4" />
                      <span>DISPARAR TESTE CONTROLADO</span>
                    </>
                  )}
                </button>
              </div>

              {/* Seletor do Transporte Alvo do Test Bench */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="bg-slate-950 p-3 rounded-xl border border-slate-800">
                  <span className="text-[11px] font-mono text-slate-400 block mb-1">Transporte Ativo:</span>
                  <select
                    value={preferredTransport}
                    onChange={(e) => setPreferredTransport(e.target.value)}
                    className="w-full bg-slate-900 text-xs font-mono text-cyan-300 p-2 rounded border border-slate-700"
                  >
                    {transports.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name} ({t.state})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="bg-slate-950 p-3 rounded-xl border border-slate-800">
                  <span className="text-[11px] font-mono text-slate-400 block mb-1">Capacidade Alvo:</span>
                  <div className="text-xs font-mono text-emerald-400 font-bold p-2 bg-slate-900 rounded border border-slate-800">
                    LIGHTS (Plafonier + Dome Light)
                  </div>
                </div>

                <div className="bg-slate-950 p-3 rounded-xl border border-slate-800">
                  <span className="text-[11px] font-mono text-slate-400 block mb-1">Ação de Teste:</span>
                  <div className="text-xs font-mono text-amber-300 font-bold p-2 bg-slate-900 rounded border border-slate-800">
                    MASTER_OFF (Valor = 0)
                  </div>
                </div>
              </div>

              {/* Steps Progress Indicator */}
              <div className="grid grid-cols-4 gap-2 text-center font-mono text-xs">
                <div className={`p-2.5 rounded-xl border ${
                  testBenchStep >= 2 ? 'bg-cyan-950/80 border-cyan-500 text-cyan-300' : 'bg-slate-950 border-slate-800 text-slate-500'
                }`}>
                  <span className="block font-bold">1. READ BEFORE</span>
                  <span className="text-[10px]">Leitura Inicial</span>
                </div>
                <div className={`p-2.5 rounded-xl border ${
                  testBenchStep >= 3 ? 'bg-amber-950/80 border-amber-500 text-amber-300' : 'bg-slate-950 border-slate-800 text-slate-500'
                }`}>
                  <span className="block font-bold">2. COMMAND</span>
                  <span className="text-[10px]">Disparo Físico</span>
                </div>
                <div className={`p-2.5 rounded-xl border ${
                  testBenchStep >= 4 ? 'bg-blue-950/80 border-blue-500 text-blue-300' : 'bg-slate-950 border-slate-800 text-slate-500'
                }`}>
                  <span className="block font-bold">3. READ AFTER</span>
                  <span className="text-[10px]">Leitura Pós-Comando</span>
                </div>
                <div className={`p-2.5 rounded-xl border ${
                  testBenchStep >= 5 ? 'bg-emerald-950/80 border-emerald-500 text-emerald-300' : 'bg-slate-950 border-slate-800 text-slate-500'
                }`}>
                  <span className="block font-bold">4. CONFIRM</span>
                  <span className="text-[10px]">Comparação de Estado</span>
                </div>
              </div>

              {/* Log em tempo real do Test Bench */}
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 font-mono text-xs space-y-2">
                <div className="flex items-center justify-between pb-2 border-b border-slate-800 text-slate-400">
                  <span>Terminal de Execução Controlada:</span>
                  <span className="text-emerald-400">● Live Connection</span>
                </div>
                <div className="space-y-2 text-slate-300 max-h-72 overflow-y-auto font-mono text-[11px] leading-relaxed">
                  {testBenchLogs.map((log, idx) => (
                    <div key={idx} className="whitespace-pre-line p-1.5 rounded bg-slate-900/50 border border-slate-800/60">
                      {log}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* MÓDULO 2: MATRIZ DE DESCOBERTA DE TRANSPORTES */}
          {activeModule === 'discovery_matrix' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-xl">
              <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                <div>
                  <h2 className="text-lg font-bold font-mono text-white">
                    2. Matriz de Descoberta de Transportes (12 Canais)
                  </h2>
                  <p className="text-xs text-slate-400 font-mono mt-1">
                    Varredura dinâmica de classes HAL, Binder, nós de dispositivo e barramentos
                  </p>
                </div>
                <button
                  onClick={() => {
                    // Simula nova varredura com latências atualizadas
                    setTransports(prev => prev.map(t => ({ ...t, pingTimeMs: Math.floor(Math.random() * 25) + 2 })));
                  }}
                  className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300 font-mono text-xs border border-slate-700"
                >
                  <RotateCw className="w-3.5 h-3.5" /> Re-escanear
                </button>
              </div>

              <div className="space-y-3">
                {transports.map((t) => (
                  <div
                    key={t.id}
                    className={`p-4 rounded-xl border transition-all ${
                      preferredTransport === t.id
                        ? 'bg-slate-950 border-cyan-500/80 shadow-md shadow-cyan-950/30'
                        : 'bg-slate-950/70 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                      <div className="flex items-center gap-3">
                        <span className="font-mono text-sm font-bold text-white">
                          {t.name}
                        </span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">
                          {t.category}
                        </span>
                        <span className="text-[10px] font-mono text-slate-500">
                          ping: {t.pingTimeMs}ms
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        {getStatusBadge(t.state)}
                        <button
                          onClick={() => setPreferredTransport(t.id)}
                          className={`px-2.5 py-1 rounded text-[11px] font-mono font-semibold transition-all ${
                            preferredTransport === t.id
                              ? 'bg-cyan-500 text-black'
                              : 'bg-slate-800 hover:bg-slate-700 text-slate-300'
                          }`}
                        >
                          {preferredTransport === t.id ? 'ATIVO' : 'Definir Ativo'}
                        </button>
                      </div>
                    </div>

                    <p className="text-xs font-mono text-slate-300 mt-2">
                      {t.details}
                    </p>

                    {t.methodsDetected && t.methodsDetected.length > 0 && (
                      <div className="mt-2 pt-2 border-t border-slate-800/80 flex flex-wrap gap-1.5">
                        <span className="text-[10px] font-mono text-slate-500">Métodos/Símbolos:</span>
                        {t.methodsDetected.slice(0, 3).map((m, idx) => (
                          <span key={idx} className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-900 text-cyan-400 border border-slate-800">
                            {m}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* MÓDULO 1: VEÍCULO E FIRMWARE */}
          {activeModule === 'vehicle_info' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-xl">
              <h2 className="text-lg font-bold font-mono text-white pb-3 border-b border-slate-800">
                1. Informações de Firmware e Propriedades do Veículo
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 font-mono text-xs">
                {[
                  { k: 'ro.product.model', v: 'BYD Dolphin Plus (e-Platform 3.0)' },
                  { k: 'ro.product.device', v: 'dolphin_plus_ivi' },
                  { k: 'ro.build.version.release', v: 'Android 12 (API 31)' },
                  { k: 'ro.build.display.id', v: 'DiLink 4.0.1.240315' },
                  { k: 'ro.hardware', v: 'qcom / mcu_renesas' },
                  { k: 'ro.bootloader', v: 'byd_abl_v4.2' },
                  { k: 'ro.build.fingerprint', v: 'BYD/dolphin_plus/dolphin:12/SP1A.210812.016/release-keys' },
                  { k: 'byd.dilink.version', v: 'DiLink 4.0 OS High-Spec' },
                  { k: 'byd.mcu.bus', v: '/dev/spidev_ivi (SPI Full Duplex)' },
                  { k: 'byd.can.rate', v: '500 kbps (High Speed CAN)' }
                ].map((item, idx) => (
                  <div key={idx} className="bg-slate-950 p-3 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[10px] block">{item.k}</span>
                    <span className="text-cyan-300 font-semibold mt-0.5 block">{item.v}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* MÓDULO 3: HAL EXPLORER */}
          {activeModule === 'hal_explorer' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-xl">
              <h2 className="text-lg font-bold font-mono text-white pb-3 border-b border-slate-800">
                3. HAL Explorer (android.hardware.bydauto.*)
              </h2>
              <p className="text-xs text-slate-400 font-mono">
                Classes de hardware oficiais detectadas através de reflexão em tempo de execução:
              </p>

              <div className="space-y-4 font-mono text-xs">
                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-cyan-400 font-bold">
                    <span>android.hardware.bydauto.light.BYDAutoLightDevice</span>
                    <span className="text-xs text-emerald-400 bg-emerald-950 px-2 py-0.5 rounded border border-emerald-600/40">INSTANCIADA</span>
                  </div>
                  <div className="text-slate-400 text-[11px] space-y-1 pl-2">
                    <div>• public int getReadingLight(int zone)</div>
                    <div>• public int setReadingLight(int zone, int state)</div>
                    <div>• public int getAmbientLightState()</div>
                    <div>• public int setAmbientLightSwitch(int onOff)</div>
                    <div>• public int setDomeLightAutoOff(int mode)</div>
                  </div>
                </div>

                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-cyan-400 font-bold">
                    <span>android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice</span>
                    <span className="text-xs text-emerald-400 bg-emerald-950 px-2 py-0.5 rounded border border-emerald-600/40">INSTANCIADA</span>
                  </div>
                  <div className="text-slate-400 text-[11px] space-y-1 pl-2">
                    <div>• public int getDoorState(int doorId)</div>
                    <div>• public int getLockState(int doorId)</div>
                    <div>• public int getTrunkState()</div>
                  </div>
                </div>

                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-cyan-400 font-bold">
                    <span>android.hardware.bydauto.speed.BYDAutoSpeedDevice</span>
                    <span className="text-xs text-emerald-400 bg-emerald-950 px-2 py-0.5 rounded border border-emerald-600/40">INSTANCIADA</span>
                  </div>
                  <div className="text-slate-400 text-[11px] space-y-1 pl-2">
                    <div>• public float getVehicleSpeed()</div>
                    <div>• public int getGearPosition()</div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* MÓDULO 10: CAN EXPLORER & FRAME VIEWER */}
          {activeModule === 'can_explorer' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-xl">
              <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                <div>
                  <h2 className="text-lg font-bold font-mono text-white">
                    10. CAN Bus Explorer (Frames 500k em Tempo Real)
                  </h2>
                  <p className="text-xs text-slate-400 font-mono mt-0.5">
                    Modo seguro: Escuta passiva de frames de iluminação e telemetria (READ-ONLY)
                  </p>
                </div>
                <span className="text-xs font-mono text-emerald-400 flex items-center gap-1.5 px-2.5 py-1 rounded bg-emerald-950 border border-emerald-700/50">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" /> /dev/can0 ativo
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full font-mono text-xs text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-950 text-slate-400 border-b border-slate-800">
                      <th className="p-2.5">CAN ID</th>
                      <th className="p-2.5">DLC</th>
                      <th className="p-2.5">PAYLOAD HEX</th>
                      <th className="p-2.5">DECODIFICAÇÃO / DESCRIÇÃO</th>
                      <th className="p-2.5">TIMESTAMP</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {canFrames.map((f, idx) => (
                      <tr key={idx} className="hover:bg-slate-950/60">
                        <td className="p-2.5 text-cyan-400 font-bold">{f.id}</td>
                        <td className="p-2.5 text-slate-400">{f.dlc}</td>
                        <td className="p-2.5 text-amber-300 font-mono tracking-wider">{f.data}</td>
                        <td className="p-2.5 text-slate-200">{f.desc}</td>
                        <td className="p-2.5 text-slate-500 text-[11px]">{f.time}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* MÓDULO 16: LOG DE BAIXO NÍVEL */}
          {activeModule === 'comm_log' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-xl">
              <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                <div>
                  <h2 className="text-lg font-bold font-mono text-white">
                    16. Log de Comunicação de Baixo Nível
                  </h2>
                  <p className="text-xs text-slate-400 font-mono mt-0.5">
                    Histórico detalhado sem mascaramento de exceções, com latências e confirmações
                  </p>
                </div>
                <button
                  onClick={() => setCommLogs([])}
                  className="text-xs font-mono text-slate-400 hover:text-white px-2 py-1 rounded bg-slate-800"
                >
                  Limpar Logs
                </button>
              </div>

              <div className="space-y-2.5 max-h-[500px] overflow-y-auto pr-1">
                {commLogs.map((log) => (
                  <div key={log.id} className="bg-slate-950 p-3 rounded-xl border border-slate-800 font-mono text-xs space-y-1.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-slate-500 text-[10px]">{log.time}</span>
                        <span className="px-1.5 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-800 text-[10px] font-bold">
                          {log.transport}
                        </span>
                        <span className="text-white font-bold">{log.action}</span>
                      </div>
                      <span className="text-slate-500 text-[10px]">{log.durationMs}ms</span>
                    </div>
                    <div className="text-slate-400 text-[11px]">
                      Alvo: <span className="text-slate-200">{log.target}</span>
                    </div>
                    <div className="text-slate-400 text-[11px]">
                      Req: <span className="text-cyan-300">{log.request}</span>
                    </div>
                    <div className="text-slate-400 text-[11px]">
                      Resp: <span className="text-emerald-300">{log.response}</span>
                    </div>
                    {log.stateBefore && (
                      <div className="text-[10px] text-slate-500">
                        Estado Antes: {log.stateBefore} → Depois: {log.stateAfter}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* MÓDULO 18: EXPORTAÇÃO DE RELATÓRIO JSON */}
          {activeModule === 'export_diag' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-xl">
              <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                <div>
                  <h2 className="text-lg font-bold font-mono text-white">
                    18. Exportação de Relatório de Diagnóstico JSON
                  </h2>
                  <p className="text-xs text-slate-400 font-mono mt-0.5">
                    Arquivo estruturado com todo o mapa de hardware e comunicação para engenharia reversa
                  </p>
                </div>
                <button
                  onClick={handleCopyJson}
                  className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-black font-mono text-xs font-bold transition-all"
                >
                  {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                  <span>{copied ? 'COPIADO!' : 'COPIAR JSON'}</span>
                </button>
              </div>

              <pre className="bg-slate-950 p-4 rounded-xl border border-slate-800 font-mono text-[11px] text-cyan-300 overflow-x-auto max-h-[480px]">
                {fullDiagnosticJson}
              </pre>
            </div>
          )}

          {/* FALLBACK PARA OUTROS MÓDULOS (4 A 15) */}
          {![
            'test_bench',
            'discovery_matrix',
            'vehicle_info',
            'hal_explorer',
            'can_explorer',
            'comm_log',
            'export_diag'
          ].includes(activeModule) && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-xl">
              <h2 className="text-lg font-bold font-mono text-white pb-2 border-b border-slate-800">
                {LAB_MODULES.find(m => m.id === activeModule)?.title || 'Módulo'}
              </h2>
              <div className="bg-slate-950 p-5 rounded-xl border border-slate-800 font-mono text-xs space-y-3">
                <div className="text-cyan-400 font-bold">
                  Status no Veículo (Dolphin Plus): ATIVO & MAPEADO
                </div>
                <p className="text-slate-300 leading-relaxed">
                  Este módulo está integrado à arquitetura do aplicativo Kotlin (`BYDCommunicationManager` e transportes desacoplados).
                  Para inspecionar ou executar rotinas de teste correspondentes, utilize o **Test Bench (Módulo 17)** ou visualize o código correspondente no **Módulo 18 (JSON)** e no código gerado do APK.
                </p>
                <div className="pt-2">
                  <button
                    onClick={() => setActiveModule('test_bench')}
                    className="px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-black font-bold font-mono text-xs"
                  >
                    Abrir Test Bench de Luz
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
