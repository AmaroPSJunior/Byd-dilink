export type TabType = 'overview' | 'lighting' | 'transports' | 'doors_windows' | 'climate' | 'can_sniffer';

export interface TransportBus {
  id: string;
  name: string;
  type: string;
  speed: string;
  protocol: string;
  status: 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'BUSY';
  latencyMs: number;
  packetsPerSec: number;
  errorCount: number;
  description: string;
  lastPacket: string;
}

export interface DoorState {
  frontLeft: boolean;
  frontRight: boolean;
  rearLeft: boolean;
  rearRight: boolean;
  trunk: boolean;
  frunk: boolean;
}

export interface WindowState {
  frontLeft: number; // 0 (closed) - 100 (fully open)
  frontRight: number;
  rearLeft: number;
  rearRight: number;
  sunroof: number;
}

export interface SeatbeltState {
  driver: { occupied: boolean; buckled: boolean };
  passenger: { occupied: boolean; buckled: boolean };
  rearLeft: { occupied: boolean; buckled: boolean };
  rearCenter: { occupied: boolean; buckled: boolean };
  rearRight: { occupied: boolean; buckled: boolean };
}

export interface LightingState {
  interiorDome: boolean;
  readingLightsLeft: boolean;
  readingLightsRight: boolean;
  ambientLight: boolean;
  ambientColor: string;
  ambientBrightness: number;
  headlights: 'off' | 'auto' | 'low' | 'high';
  fogLights: boolean;
  hazardLights: boolean;
  welcomeLights: boolean;
  hardwareConfirmStatus: 'idle' | 'transmitting' | 'bus_ack' | 'ecu_confirm' | 'confirmed' | 'failed';
}

export interface TirePressure {
  psi: number;
  tempC: number;
  status: 'normal' | 'low' | 'high';
}

export interface VehicleState {
  speedKmh: number;
  batterySoc: number; // 0-100
  batteryTempC: number;
  rangeKm: number;
  powerKw: number;
  driveMode: 'ECO' | 'NORMAL' | 'SPORT' | 'SNOW';
  locked: boolean;
  gear: 'P' | 'R' | 'N' | 'D';
  climate: {
    power: boolean;
    targetTempC: number;
    fanSpeed: number;
    autoMode: boolean;
    acOn: boolean;
    driverSeatHeat: number; // 0-3
    passSeatHeat: number; // 0-3
  };
  doors: DoorState;
  windows: WindowState;
  seatbelts: SeatbeltState;
  lighting: LightingState;
  tpms: {
    frontLeft: TirePressure;
    frontRight: TirePressure;
    rearLeft: TirePressure;
    rearRight: TirePressure;
  };
}

export interface CanPacket {
  id: string;
  timestamp: string;
  busId: string;
  canId: string;
  dlc: number;
  data: string;
  direction: 'RX' | 'TX';
  description?: string;
}
