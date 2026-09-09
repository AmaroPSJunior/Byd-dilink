export type SeatPosition = 'driver' | 'passenger' | 'rear_left' | 'rear_center' | 'rear_right';

export interface SeatBeltState {
  id: SeatPosition;
  name: string;
  isBuckled: boolean;
  isOccupied: boolean;
  warning: boolean;
}

export type DoorPosition = 'front_left' | 'front_right' | 'rear_left' | 'rear_right' | 'trunk' | 'hood';

export interface DoorState {
  id: DoorPosition;
  name: string;
  isOpen: boolean;
  isLocked: boolean;
}

export type WindowPosition = 'front_left' | 'front_right' | 'rear_left' | 'rear_right';

export interface WindowState {
  id: WindowPosition;
  name: string;
  openPercentage: number; // 0 (closed) to 100 (fully open)
  isLocked: boolean;
}

export interface InternalLightState {
  masterState: boolean; // true = lights ON, false = lights OFF
  driverReadingLight: boolean;
  passengerReadingLight: boolean;
  rearReadingLight: boolean;
  footwellLight: boolean;
  ambientLight: boolean;
  ambientColor: string;
  brightnessPercentage: number; // 0 - 100
  autoOffOnDrive: boolean;
}

export interface VehicleTelemetry {
  modelName: string;
  vin: string;
  batteryPercentage: number;
  remainingRangeKm: number;
  gear: 'P' | 'R' | 'N' | 'D';
  speedKmh: number;
  outsideTempCelsius: number;
  cabinTempCelsius: number;
  diLinkVersion: string;
  canBusConnected: boolean;
}

export interface DiLinkLogEntry {
  id: string;
  timestamp: string;
  category: 'LIGHT' | 'SEATBELT' | 'DOOR' | 'WINDOW' | 'SYSTEM' | 'CANBUS';
  actionName: string;
  javaCodeCall: string;
  intentAction: string;
  extraParams: Record<string, any>;
  status: 'SUCCESS' | 'MOCK' | 'HARDWARE_ACK' | 'ERROR';
}

export type AppTab = 'cockpit' | 'vehicle_doors_windows' | 'dilink_inspector' | 'apk_export' | 'ai_assistant';
