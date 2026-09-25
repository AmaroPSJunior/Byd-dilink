import React, { useState, useEffect, useCallback } from 'react';
import { Navbar } from './components/Navbar';
import { DashboardOverview } from './components/DashboardOverview';
import { LightingControl } from './components/LightingControl';
import { TransportsMatrix } from './components/TransportsMatrix';
import { DoorsWindowsBelts } from './components/DoorsWindowsBelts';
import { ClimateAndBattery } from './components/ClimateAndBattery';
import { CanSniffer } from './components/CanSniffer';
import { TabType, VehicleState, TransportBus, CanPacket } from './types';

export function App() {
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const [isSnifferPaused, setIsSnifferPaused] = useState<boolean>(false);

  // Initial Vehicle State
  const [vehicleState, setVehicleState] = useState<VehicleState>({
    speedKmh: 0,
    batterySoc: 88,
    batteryTempC: 28,
    rangeKm: 420,
    powerKw: 0,
    driveMode: 'ECO',
    locked: true,
    gear: 'P',
    climate: {
      power: true,
      targetTempC: 22.0,
      fanSpeed: 3,
      autoMode: true,
      acOn: true,
      driverSeatHeat: 0,
      passSeatHeat: 0,
    },
    doors: {
      frontLeft: false,
      frontRight: false,
      rearLeft: false,
      rearRight: false,
      trunk: false,
      frunk: false,
    },
    windows: {
      frontLeft: 0,
      frontRight: 0,
      rearLeft: 0,
      rearRight: 0,
      sunroof: 0,
    },
    seatbelts: {
      driver: { occupied: true, buckled: true },
      passenger: { occupied: false, buckled: false },
      rearLeft: { occupied: false, buckled: false },
      rearCenter: { occupied: false, buckled: false },
      rearRight: { occupied: false, buckled: false },
    },
    lighting: {
      interiorDome: true,
      readingLightsLeft: false,
      readingLightsRight: false,
      ambientLight: true,
      ambientColor: '#06b6d4',
      ambientBrightness: 80,
      headlights: 'auto',
      fogLights: false,
      hazardLights: false,
      welcomeLights: true,
      hardwareConfirmStatus: 'idle',
    },
    tpms: {
      frontLeft: { psi: 36, tempC: 29, status: 'normal' },
      frontRight: { psi: 36, tempC: 29, status: 'normal' },
      rearLeft: { psi: 35, tempC: 28, status: 'normal' },
      rearRight: { psi: 35, tempC: 28, status: 'normal' },
    },
  });

  // 13 Transports Initial Matrix
  const [transports, setTransports] = useState<TransportBus[]>([
    { id: 'CAN1_PWR', name: 'CAN1 High-Speed Powertrain', type: 'CAN 2.0B', speed: '500 kbps', protocol: 'ISO 11898-2', status: 'ONLINE', latencyMs: 1.2, packetsPerSec: 142, errorCount: 0, description: 'Comunicação crítica entre Inversor MCU, Bateria Blade BMS e Unidade de Tração.', lastPacket: '0x102 08 42 00 00 FF' },
    { id: 'CAN2_BODY', name: 'CAN2 Medium-Speed Body (BCM)', type: 'CAN 2.0B', speed: '250 kbps', protocol: 'ISO 11898-3', status: 'ONLINE', latencyMs: 2.8, packetsPerSec: 98, errorCount: 0, description: 'Módulo de controle da carroceria: travamento central, vidros elétricos, retrovisores e limpadores.', lastPacket: '0x1A0 08 FF 01 00 00' },
    { id: 'LIN1_DOME', name: 'LIN1 Interior Lighting & Dome', type: 'LIN Bus', speed: '19.2 kbps', protocol: 'LIN 2.2A', status: 'ONLINE', latencyMs: 8.5, packetsPerSec: 34, errorCount: 0, description: 'Barramento de iluminação do teto, optossensores de fotoluminescência e interruptores capacitivos.', lastPacket: '0x03B 08 00 11 00 00' },
    { id: 'LIN2_HVAC', name: 'LIN2 HVAC & Comfort Flaps', type: 'LIN Bus', speed: '19.2 kbps', protocol: 'LIN 2.1', status: 'ONLINE', latencyMs: 9.1, packetsPerSec: 28, errorCount: 0, description: 'Atuadores de passo do ar-condicionado dual zone e controle de fluxo dos difusores.', lastPacket: '0x04C 04 16 03 01 00' },
    { id: 'AUTO_ETH', name: 'Automotive Ethernet (BroadR-Reach)', type: '100BASE-T1', speed: '100 Mbps', protocol: 'IEEE 802.3bw', status: 'ONLINE', latencyMs: 0.4, packetsPerSec: 1250, errorCount: 0, description: 'Link de altíssima velocidade para streaming das câmeras 360°, ADAS e painel digital.', lastPacket: '0xFA0 64 VIDEO_FRAME_OK' },
    { id: 'ADB_SERIAL', name: 'ADB Debug UART Bridge', type: 'Serial UART', speed: '115200 bps', protocol: 'Android ADB', status: 'ONLINE', latencyMs: 3.2, packetsPerSec: 52, errorCount: 0, description: 'Ponte serial de depuração de baixo nível com o ecossistema Android DiLink OS.', lastPacket: '0x001 LOGCAT_SYS_OK' },
    { id: 'BLE_KEY', name: 'BLE 5.2 Smart Key Proximity', type: 'Wireless BLE', speed: '2 Mbps', protocol: 'Bluetooth 5.2', status: 'ONLINE', latencyMs: 14.0, packetsPerSec: 18, errorCount: 0, description: 'Recepção de chave digital do smartphone, NFC e aproximação por ultra-wideband (UWB).', lastPacket: '0x0A1 RSSI_-58dBm_OK' },
    { id: 'OBD2_DIAG', name: 'OBD-II ISO 15765-4 Gateway', type: 'CAN OBD-II', speed: '500 kbps', protocol: 'ISO 15765-4', status: 'ONLINE', latencyMs: 4.1, packetsPerSec: 12, errorCount: 0, description: 'Interface padronizada de diagnósticos automotivos e emissão de parâmetros de telemetria.', lastPacket: '0x7DF 08 02 01 0C 00' },
    { id: 'DILINK_IPC', name: 'DiLink Android IPC Service', type: 'UNIX Socket', speed: '1 Gbps', protocol: 'Android HAL', status: 'ONLINE', latencyMs: 0.1, packetsPerSec: 840, errorCount: 0, description: 'Comunicação interna de processos no chip Qualcomm Snapdragon Automotive.', lastPacket: '0x999 HAL_CAR_SERVICE' },
    { id: 'MQTT_TELE', name: 'MQTT Telematics 4G/5G Gate', type: 'Cellular WAN', speed: '50 Mbps', protocol: 'MQTT/TLS', status: 'ONLINE', latencyMs: 45.0, packetsPerSec: 5, errorCount: 0, description: 'Conexão em nuvem para comandos remotos do aplicativo, localização GPS e atualizações OTA.', lastPacket: 'BYD_TELEMETRY_ACK' },
    { id: 'FLEXRAY', name: 'FlexRay Safety Critical Bus', type: 'FlexRay ChA/B', speed: '10 Mbps', protocol: 'FlexRay 3.0', status: 'ONLINE', latencyMs: 0.8, packetsPerSec: 620, errorCount: 0, description: 'Barramento tolerante a falhas para controle eletrônico de estabilidade (ESP) e direção.', lastPacket: '0x012 SYNC_SLOT_01' },
    { id: 'UDS_DIAG', name: 'UDS Diagnostics ISO 14229', type: 'UDS Service', speed: '500 kbps', protocol: 'ISO 14229-1', status: 'ONLINE', latencyMs: 2.2, packetsPerSec: 8, errorCount: 0, description: 'Serviços unificados de diagnóstico para calibração, leitura de memória DTC e flashing.', lastPacket: '0x7E8 08 06 50 01 00' },
    { id: 'USB_DONGLE', name: 'USB OTG DiLink Dongle', type: 'USB 3.0 OTG', speed: '5 Gbps', protocol: 'USB Host', status: 'ONLINE', latencyMs: 1.0, packetsPerSec: 210, errorCount: 0, description: 'Interface USB para acoplamento de analisadores físicos de bancada e dongles de captura.', lastPacket: '0x888 USB_DEVICE_CONNECTED' },
  ]);

  // CAN Stream Log
  const [canPackets, setCanPackets] = useState<CanPacket[]>([
    { id: '1', timestamp: '12:00:01.012', busId: 'LIN1_DOME', canId: '0x03B', dlc: 8, data: '00 11 00 00 00 00 00 00', direction: 'RX', description: 'Estado Iluminação Teto' },
    { id: '2', timestamp: '12:00:01.145', busId: 'CAN1_PWR', canId: '0x102', dlc: 8, data: '42 00 00 FF 00 00 00 00', direction: 'RX', description: 'Estado Bateria Blade SoC' },
    { id: '3', timestamp: '12:00:01.290', busId: 'CAN2_BODY', canId: '0x1A0', dlc: 8, data: 'FF 01 00 00 00 00 00 00', direction: 'RX', description: 'Trava Central OK' },
  ]);

  // Helper to add packet to CAN sniffer
  const addCanPacket = useCallback((busId: string, canId: string, dlc: number, data: string, direction: 'RX' | 'TX', description: string) => {
    if (isSnifferPaused) return;
    const now = new Date();
    const timeStr = `${now.toLocaleTimeString('pt-BR')}.${Math.floor(now.getMilliseconds()).toString().padStart(3, '0')}`;
    const newPacket: CanPacket = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: timeStr,
      busId,
      canId,
      dlc,
      data,
      direction,
      description,
    };
    setCanPackets(prev => [newPacket, ...prev.slice(0, 99)]);
  }, [isSnifferPaused]);

  // Periodic heartbeat packet tick to simulate active automotive gateway
  useEffect(() => {
    const interval = setInterval(() => {
      if (isSnifferPaused) return;
      const busOptions = ['CAN1_PWR', 'CAN2_BODY', 'LIN1_DOME', 'UDS_DIAG'];
      const randomBus = busOptions[Math.floor(Math.random() * busOptions.length)];
      
      if (randomBus === 'CAN1_PWR') {
        addCanPacket('CAN1_PWR', '0x102', 8, `42 ${Math.floor(Math.random() * 99).toString(16).padStart(2, '0')} 00 FF 00 00 00 00`, 'RX', 'Telemetria Inversor MCU');
      } else if (randomBus === 'CAN2_BODY') {
        addCanPacket('CAN2_BODY', '0x1A0', 8, 'FF 01 00 00 00 00 00 00', 'RX', 'Status Portas & Vidros BCM');
      } else if (randomBus === 'LIN1_DOME') {
        addCanPacket('LIN1_DOME', '0x03B', 8, '00 11 00 00 00 00 00 00', 'RX', 'Heartbeat LIN1 Optosensor');
      }
    }, 2500);

    return () => clearInterval(interval);
  }, [addCanPacket, isSnifferPaused]);

  // Handler for "Apagar Luzes Internas com Confirmação em Hardware"
  const handleTurnOffAllLights = () => {
    setVehicleState(prev => ({
      ...prev,
      lighting: {
        ...prev.lighting,
        interiorDome: false,
        readingLightsLeft: false,
        readingLightsRight: false,
        hardwareConfirmStatus: 'confirmed'
      }
    }));
    addCanPacket('LIN1_DOME', '0x03B', 8, '00 00 00 00 00 00 00 00', 'TX', 'HARDWARE CONFIRM: Apagar todas as luzes internas');
  };

  // Door toggle
  const handleToggleDoor = (doorKey: keyof VehicleState['doors']) => {
    setVehicleState(prev => {
      const nextVal = !prev.doors[doorKey];
      return {
        ...prev,
        doors: { ...prev.doors, [doorKey]: nextVal }
      };
    });
    addCanPacket('CAN2_BODY', '0x1A0', 8, `01 ${doorKey} OPEN_CHANGE`, 'RX', `Alteração estado da porta: ${doorKey}`);
  };

  // Seatbelt toggle
  const handleToggleSeatbelt = (seatKey: keyof VehicleState['seatbelts']) => {
    setVehicleState(prev => {
      const current = prev.seatbelts[seatKey];
      const nextBuckled = current.occupied ? !current.buckled : false;
      return {
        ...prev,
        seatbelts: {
          ...prev.seatbelts,
          [seatKey]: { ...current, buckled: nextBuckled }
        }
      };
    });
    addCanPacket('CAN2_BODY', '0x2B1', 4, `02 ${seatKey} BUCKLE`, 'RX', `Sensor cinto: ${seatKey}`);
  };

  // Central Lock toggle
  const handleToggleLock = () => {
    setVehicleState(prev => {
      const nextLock = !prev.locked;
      return { ...prev, locked: nextLock };
    });
    addCanPacket('CAN2_BODY', '0x1A0', 8, vehicleState.locked ? '00 UNLOCK_ALL' : 'FF LOCK_ALL', 'TX', 'Comando Trava Central');
  };

  const onlineTransportsCount = transports.filter(t => t.status === 'ONLINE' || t.status === 'BUSY').length;

  return (
    <div className="min-h-screen bg-[#070d1e] text-slate-100 flex flex-col font-sans selection:bg-cyan-500 selection:text-slate-950">
      {/* Top Navigation */}
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        vehicleState={vehicleState}
        onToggleLock={handleToggleLock}
        onlineTransportsCount={onlineTransportsCount}
      />

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 md:p-6 space-y-6">
        {activeTab === 'overview' && (
          <DashboardOverview
            vehicleState={vehicleState}
            setVehicleState={setVehicleState}
            onTurnOffAllLights={handleTurnOffAllLights}
            onToggleDoor={handleToggleDoor}
            onToggleSeatbelt={handleToggleSeatbelt}
          />
        )}

        {activeTab === 'lighting' && (
          <LightingControl
            vehicleState={vehicleState}
            setVehicleState={setVehicleState}
            addCanPacket={addCanPacket}
          />
        )}

        {activeTab === 'transports' && (
          <TransportsMatrix
            transports={transports}
            setTransports={setTransports}
            onInjectPacket={(busId, payload) => {
              addCanPacket(busId, '0x800', 8, payload, 'TX', 'Injeção direta no transporte');
            }}
          />
        )}

        {activeTab === 'doors_windows' && (
          <DoorsWindowsBelts
            vehicleState={vehicleState}
            setVehicleState={setVehicleState}
            onToggleDoor={handleToggleDoor}
            onToggleSeatbelt={handleToggleSeatbelt}
          />
        )}

        {activeTab === 'climate' && (
          <ClimateAndBattery
            vehicleState={vehicleState}
            setVehicleState={setVehicleState}
          />
        )}

        {activeTab === 'can_sniffer' && (
          <CanSniffer
            packets={canPackets}
            onClearPackets={() => setCanPackets([])}
            onSendCustomPacket={(busId, canId, dlc, data, desc) => {
              addCanPacket(busId, canId, dlc, data, 'TX', desc);
            }}
            isPaused={isSnifferPaused}
            setIsPaused={setIsSnifferPaused}
          />
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-900 bg-slate-950/80 py-4 px-6 text-center text-xs text-slate-500 font-mono">
        BYD DiLink Vehicle Controller & Automotive Communication Laboratory • Multi-Transport CAN/LIN/Ethernet Gateway
      </footer>
    </div>
  );
}

export default App;
