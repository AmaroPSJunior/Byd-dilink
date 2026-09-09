import React, { useState } from 'react';
import { CockpitHeader } from './components/CockpitHeader';
import { CentralLightButton } from './components/CentralLightButton';
import { SeatBeltMonitor } from './components/SeatBeltMonitor';
import { DoorsAndWindowsGrid } from './components/DoorsAndWindowsGrid';
import { DiLinkSdkInspector } from './components/DiLinkSdkInspector';
import { AndroidExporterModal } from './components/AndroidExporterModal';
import { GeminiCarAssistant } from './components/GeminiCarAssistant';
import { VehicleFeaturesExplorer } from './components/VehicleFeaturesExplorer';
import {
  AppTab,
  DoorPosition,
  DoorState,
  InternalLightState,
  SeatBeltState,
  SeatPosition,
  VehicleTelemetry,
  WindowPosition,
  WindowState,
  DiLinkLogEntry
} from './types';

export default function App() {
  const [activeTab, setActiveTab] = useState<AppTab>('cockpit');

  // Internal Lights State (Default: ON, so user can press central button to turn ALL off)
  const [lightsState, setLightsState] = useState<InternalLightState>({
    masterState: true,
    driverReadingLight: true,
    passengerReadingLight: true,
    rearReadingLight: true,
    footwellLight: true,
    ambientLight: true,
    ambientColor: '#00d2ff',
    brightnessPercentage: 80,
    autoOffOnDrive: true
  });

  // Seat Belts State
  const [seatBelts, setSeatBelts] = useState<Record<SeatPosition, SeatBeltState>>({
    driver: { id: 'driver', name: 'Motorista (FL)', isBuckled: true, isOccupied: true, warning: false },
    passenger: { id: 'passenger', name: 'Passageiro (FR)', isBuckled: false, isOccupied: true, warning: true },
    rear_left: { id: 'rear_left', name: 'Traseiro Esquerdo', isBuckled: true, isOccupied: true, warning: false },
    rear_center: { id: 'rear_center', name: 'Traseiro Central', isBuckled: false, isOccupied: false, warning: false },
    rear_right: { id: 'rear_right', name: 'Traseiro Direito', isBuckled: true, isOccupied: true, warning: false }
  });

  // Doors & Hatch State
  const [doors, setDoors] = useState<Record<DoorPosition, DoorState>>({
    front_left: { id: 'front_left', name: 'Porta Motorista', isOpen: false, isLocked: true },
    front_right: { id: 'front_right', name: 'Porta Passageiro', isOpen: false, isLocked: true },
    rear_left: { id: 'rear_left', name: 'Porta Traseira Esq.', isOpen: false, isLocked: true },
    rear_right: { id: 'rear_right', name: 'Porta Traseira Dir.', isOpen: false, isLocked: true },
    trunk: { id: 'trunk', name: 'Porta-Malas (Traseira)', isOpen: false, isLocked: true },
    hood: { id: 'hood', name: 'Capô (Dianteiro)', isOpen: false, isLocked: true }
  });

  // Windows State (0% closed to 100% open)
  const [windows, setWindows] = useState<Record<WindowPosition, WindowState>>({
    front_left: { id: 'front_left', name: 'Vidro Motorista', openPercentage: 0, isLocked: false },
    front_right: { id: 'front_right', name: 'Vidro Passageiro', openPercentage: 0, isLocked: false },
    rear_left: { id: 'rear_left', name: 'Vidro Traseiro Esq.', openPercentage: 0, isLocked: false },
    rear_right: { id: 'rear_right', name: 'Vidro Traseiro Dir.', openPercentage: 0, isLocked: false }
  });

  // Vehicle Telemetry
  const [telemetry] = useState<VehicleTelemetry>({
    modelName: 'BYD Dolphin GS',
    vin: 'LC0BYD458920138',
    batteryPercentage: 88,
    remainingRangeKm: 360,
    gear: 'P',
    speedKmh: 0,
    outsideTempCelsius: 28,
    cabinTempCelsius: 22,
    diLinkVersion: '4.0 OS',
    canBusConnected: true
  });

  // Audio Chime Status
  const [chimeEnabled, setChimeEnabled] = useState(true);

  // Live DiLink SDK Logs
  const [logs, setLogs] = useState<DiLinkLogEntry[]>([
    {
      id: 'init-1',
      timestamp: new Date().toLocaleTimeString(),
      category: 'SYSTEM',
      actionName: 'INICIALIZACAO_DILINK_CANBUS',
      javaCodeCall: 'BYDAutoFeature.getInstance(context).connectVehicleBus();',
      intentAction: 'com.byd.action.SYSTEM_READY',
      extraParams: { status: 'CONNECTED', busRateKbps: 500 },
      status: 'SUCCESS'
    }
  ]);

  const addLog = (
    actionName: string,
    javaCall: string,
    intent: string,
    params: Record<string, any>,
    category: 'LIGHT' | 'SEATBELT' | 'DOOR' | 'WINDOW' | 'SYSTEM' | 'CANBUS' = 'CANBUS'
  ) => {
    const newEntry: DiLinkLogEntry = {
      id: Date.now().toString(),
      timestamp: new Date().toLocaleTimeString(),
      category,
      actionName,
      javaCodeCall: javaCall,
      intentAction: intent,
      extraParams: params,
      status: 'SUCCESS'
    };
    setLogs((prev) => [newEntry, ...prev]);
  };

  // Master Central Button Handler: Turn OFF/ON All Internal Lights
  const handleToggleMasterLights = (turnOffAll: boolean) => {
    setLightsState((prev) => ({
      ...prev,
      masterState: !turnOffAll,
      driverReadingLight: !turnOffAll,
      passengerReadingLight: !turnOffAll,
      rearReadingLight: !turnOffAll,
      footwellLight: !turnOffAll,
      ambientLight: !turnOffAll,
      brightnessPercentage: turnOffAll ? 0 : 80
    }));

    addLog(
      turnOffAll ? 'APAGAR_TODAS_LUZES_INTERNAS' : 'LIGAR_LUZES_INTERNAS',
      turnOffAll
        ? 'BYDAutoLightBus.getInstance(context).setReadingLightState(0, 0); BYDAutoLightBus.getInstance(context).setAmbientLightState(0);'
        : 'BYDAutoLightBus.getInstance(context).setReadingLightState(0, 1); BYDAutoLightBus.getInstance(context).setAmbientLightState(1);',
      'com.byd.action.LIGHT_CONTROL',
      { command: turnOffAll ? 'MASTER_OFF' : 'MASTER_ON', target: 'ALL_INTERNAL_LIGHTS' },
      'LIGHT'
    );
  };

  const handleUpdateLightSubState = (updates: Partial<InternalLightState>) => {
    setLightsState((prev) => ({ ...prev, ...updates }));
  };

  // Seatbelts Handlers
  const handleToggleSeatBelt = (position: SeatPosition) => {
    setSeatBelts((prev) => {
      const current = prev[position];
      const updated = { ...current, isBuckled: !current.isBuckled };
      
      addLog(
        `ALTERAR_STATUS_CINTO_${position.toUpperCase()}`,
        `BYDAutoSeatBeltBus.getInstance(context).setSeatbeltStatus("${position}", ${updated.isBuckled});`,
        'com.byd.action.SEATBELT_STATE_CHANGED',
        { position, isBuckled: updated.isBuckled },
        'SEATBELT'
      );

      return { ...prev, [position]: updated };
    });
  };

  const handleToggleOccupied = (position: SeatPosition) => {
    setSeatBelts((prev) => {
      const current = prev[position];
      const updated = { ...current, isOccupied: !current.isOccupied };

      addLog(
        `SENSOR_PESO_BANCO_${position.toUpperCase()}`,
        `BYDAutoSeatBeltBus.getInstance(context).setOccupancySensor("${position}", ${updated.isOccupied});`,
        'com.byd.action.SEAT_OCCUPANCY_CHANGED',
        { position, isOccupied: updated.isOccupied },
        'SEATBELT'
      );

      return { ...prev, [position]: updated };
    });
  };

  // Doors Handlers
  const handleToggleDoor = (position: DoorPosition) => {
    setDoors((prev) => {
      const current = prev[position];
      const updated = { ...current, isOpen: !current.isOpen };

      addLog(
        `ALTERAR_PORTA_${position.toUpperCase()}`,
        `BYDAutoDoorBus.getInstance(context).setDoorOpen("${position}", ${updated.isOpen});`,
        'com.byd.action.DOOR_STATE_CHANGED',
        { position, isOpen: updated.isOpen },
        'DOOR'
      );

      return { ...prev, [position]: updated };
    });
  };

  const handleToggleLockDoor = (position: DoorPosition) => {
    setDoors((prev) => {
      const current = prev[position];
      const updated = { ...current, isLocked: !current.isLocked };

      addLog(
        `TRAVA_PORTA_${position.toUpperCase()}`,
        `BYDAutoDoorBus.getInstance(context).setDoorLock("${position}", ${updated.isLocked});`,
        'com.byd.action.DOOR_LOCK_CHANGED',
        { position, isLocked: updated.isLocked },
        'DOOR'
      );

      return { ...prev, [position]: updated };
    });
  };

  // Windows Handlers
  const handleUpdateWindow = (position: WindowPosition, openPercentage: number) => {
    setWindows((prev) => {
      const current = prev[position];
      const updated = { ...current, openPercentage };

      addLog(
        `POSICAO_VIDRO_${position.toUpperCase()}`,
        `BYDAutoWindowBus.getInstance(context).setWindowPosition("${position}", ${openPercentage});`,
        'com.byd.action.WINDOW_CONTROL',
        { position, openPercentage },
        'WINDOW'
      );

      return { ...prev, [position]: updated };
    });
  };

  const handleGlobalWindowAction = (presetPercentage: number, actionLabel: string) => {
    setWindows((prev) => {
      const next = { ...prev };
      (Object.keys(next) as WindowPosition[]).forEach((pos) => {
        next[pos] = { ...next[pos], openPercentage: presetPercentage };
      });
      return next;
    });

    addLog(
      actionLabel,
      `BYDAutoWindowBus.getInstance(context).setAllWindowsPosition(${presetPercentage});`,
      'com.byd.action.GLOBAL_WINDOW_CONTROL',
      { percentage: presetPercentage },
      'WINDOW'
    );
  };

  const handleGlobalLockAction = (lockAll: boolean) => {
    setDoors((prev) => {
      const next = { ...prev };
      (Object.keys(next) as DoorPosition[]).forEach((pos) => {
        next[pos] = { ...next[pos], isLocked: lockAll };
      });
      return next;
    });

    addLog(
      lockAll ? 'TRANCAR_TODAS_PORTAS' : 'DESTRANCAR_VEICULO',
      `BYDAutoDoorBus.getInstance(context).setAllDoorsLocked(${lockAll});`,
      'com.byd.action.GLOBAL_DOOR_LOCK',
      { lockAll },
      'DOOR'
    );
  };

  const unbuckledCount = (Object.values(seatBelts) as SeatBeltState[]).filter(
    (s) => s.isOccupied && !s.isBuckled
  ).length;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-cyan-500 selection:text-black">
      {/* Top Cockpit Header */}
      <CockpitHeader
        telemetry={telemetry}
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        masterLightsOn={lightsState.masterState}
        unbuckledCount={unbuckledCount}
      />

      {/* Main Content Area */}
      <main className="flex-1 pb-16">
        {activeTab === 'cockpit' && (
          <div className="animate-fadeIn">
            {/* 1. Primary requested feature: Central Master Button to turn off all internal lights */}
            <CentralLightButton
              lightsState={lightsState}
              onToggleMasterLights={handleToggleMasterLights}
              onUpdateLightSubState={handleUpdateLightSubState}
              onLogDiLinkAction={addLog}
            />

            {/* 2. Secondary requested feature: Seat belt status directly below central button */}
            <SeatBeltMonitor
              seatBelts={seatBelts}
              onToggleSeatBelt={handleToggleSeatBelt}
              onToggleOccupied={handleToggleOccupied}
              chimeEnabled={chimeEnabled}
              setChimeEnabled={setChimeEnabled}
              onLogDiLinkAction={addLog}
            />
          </div>
        )}

        {activeTab === 'vehicle_features' && (
          <div className="animate-fadeIn">
            <VehicleFeaturesExplorer onLogDiLinkAction={addLog} />
          </div>
        )}

        {activeTab === 'vehicle_doors_windows' && (
          <div className="animate-fadeIn">
            <DoorsAndWindowsGrid
              doors={doors}
              windows={windows}
              onToggleDoor={handleToggleDoor}
              onToggleLockDoor={handleToggleLockDoor}
              onUpdateWindow={handleUpdateWindow}
              onGlobalWindowAction={handleGlobalWindowAction}
              onGlobalLockAction={handleGlobalLockAction}
              onLogDiLinkAction={addLog}
            />
          </div>
        )}

        {activeTab === 'dilink_inspector' && (
          <div className="animate-fadeIn">
            <DiLinkSdkInspector
              logs={logs}
              onClearLogs={() => setLogs([])}
              onSimulateIntent={(action) =>
                addLog('TESTE_INTENT_MANUAL', 'context.sendBroadcast(intent);', 'com.byd.action.TEST', { action })
              }
            />
          </div>
        )}

        {activeTab === 'apk_export' && (
          <div className="animate-fadeIn">
            <AndroidExporterModal />
          </div>
        )}

        {activeTab === 'ai_assistant' && (
          <div className="animate-fadeIn">
            <GeminiCarAssistant
              lightsState={lightsState}
              seatBelts={seatBelts}
              onToggleMasterLights={handleToggleMasterLights}
              onToggleSeatBelt={handleToggleSeatBelt}
              onLogDiLinkAction={addLog}
            />
          </div>
        )}
      </main>

      {/* Bottom Legal / Car Status Bar */}
      <footer className="bg-slate-900 border-t border-slate-800/80 py-3 px-4 text-center text-xs font-mono text-slate-500">
        <span>BYD DiLink Vehicle Controller • Compatível com Dolphin, Seal, Song Plus, Yuan e Han</span>
      </footer>
    </div>
  );
}
