#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

static const char* DEVICE_NAME = "ChalecoVR";
static const char* SERVICE_UUID = "7c1f4d8a-9f70-4f5e-9d1a-2d5fbf2f2a01";
static const char* COMMAND_UUID = "7c1f4d8a-9f70-4f5e-9d1a-2d5fbf2f2a02";
static const char* STATUS_UUID = "7c1f4d8a-9f70-4f5e-9d1a-2d5fbf2f2a03";

const uint8_t PELTIER_COUNT = 8;
const uint8_t VIBRATION_COUNT = 4;
const uint8_t THERMAL_ZONE_COUNT = 2;

const uint8_t ZONE_BACK = 0;
const uint8_t ZONE_FRONT = 1;
const uint8_t MODE_NONE = 0;
const uint8_t MODE_HEAT = 1;
const uint8_t MODE_COOL = 2;

struct ThermalChannelConfig {
  uint8_t pin;
  const char* mode;
  const char* kind;
  uint8_t zone;
  const char* zoneName;
  const char* sideName;
  const char* levelName;
};

struct VibrationChannelConfig {
  uint8_t pin;
  const char* zoneName;
  const char* sideName;
  const char* levelName;
};

const ThermalChannelConfig THERMAL_CHANNELS[PELTIER_COUNT] = {
  {2, "heat", "hot", ZONE_BACK, "back", "left", "upper"},
  {4, "heat", "hot", ZONE_BACK, "back", "right", "upper"},
  {5, "heat", "hot", ZONE_FRONT, "front", "right", "upper"},
  {13, "heat", "hot", ZONE_FRONT, "front", "left", "upper"},
  {14, "cool", "cold", ZONE_BACK, "back", "left", "lower"},
  {16, "cool", "cold", ZONE_BACK, "back", "right", "lower"},
  {17, "cool", "cold", ZONE_FRONT, "front", "right", "lower"},
  {18, "cool", "cold", ZONE_FRONT, "front", "left", "lower"},
};

const VibrationChannelConfig VIBRATION_CHANNELS[VIBRATION_COUNT] = {
  {19, "back", "left", "middle"},
  {21, "back", "right", "middle"},
  {22, "front", "right", "middle"},
  {23, "front", "left", "middle"},
};

const uint8_t THERMAL_MAX_DUTY = 70;
const uint8_t MAX_ACTIVE_PELTIERS = 2;
const unsigned long THERMAL_MIN_DURATION_MS = 250;
const unsigned long THERMAL_MAX_DURATION_MS = 10000;
const unsigned long THERMAL_CHANNEL_COOLDOWN_MS = 3000;
const unsigned long THERMAL_OPPOSITE_MODE_PAUSE_MS = 10000;
const unsigned long PELTIER_PWM_PERIOD_MS = 100;
const unsigned long COMMAND_WATCHDOG_MS = 4500;
const unsigned long VIBRATION_MIN_DURATION_MS = 20;
const unsigned long VIBRATION_MAX_DURATION_MS = 10000;

BLECharacteristic* statusCharacteristic = nullptr;
bool deviceConnected = false;
unsigned long lastCommandAt = 0;

bool peltierActive[PELTIER_COUNT] = {false};
uint8_t peltierDuty[PELTIER_COUNT] = {0};
unsigned long peltierOffAt[PELTIER_COUNT] = {0};
unsigned long peltierCooldownUntil[PELTIER_COUNT] = {0};
unsigned long vibrationOffAt[VIBRATION_COUNT] = {0};
uint8_t zoneLastMode[THERMAL_ZONE_COUNT] = {MODE_NONE};
unsigned long zoneOppositeModeAllowedAt[THERMAL_ZONE_COUNT] = {0};

void sendStatus(const String& message) {
  Serial.println("[STATUS] " + message);
  if (statusCharacteristic && deviceConnected) {
    statusCharacteristic->setValue(message.c_str());
    statusCharacteristic->notify();
  }
}

void sendChannelStatus(const char* code, int channel) {
  char message[96];
  snprintf(message, sizeof(message), "%s,ch=%d", code, channel);
  sendStatus(message);
}

bool timeReached(unsigned long now, unsigned long target) {
  return target != 0 && (long)(now - target) >= 0;
}

bool timePending(unsigned long now, unsigned long target) {
  return target != 0 && (long)(target - now) > 0;
}

unsigned long clampDuration(unsigned long value, unsigned long minimum, unsigned long maximum) {
  if (value < minimum) return minimum;
  if (value > maximum) return maximum;
  return value;
}

uint8_t modeCode(const String& mode) {
  if (mode == "heat") return MODE_HEAT;
  if (mode == "cool") return MODE_COOL;
  return MODE_NONE;
}

uint8_t activePeltierCount() {
  uint8_t count = 0;
  for (uint8_t i = 0; i < PELTIER_COUNT; i++) {
    if (peltierActive[i]) count++;
  }
  return count;
}

bool hasActivePeltierInZone(uint8_t zone) {
  for (uint8_t i = 0; i < PELTIER_COUNT; i++) {
    if (peltierActive[i] && THERMAL_CHANNELS[i].zone == zone) return true;
  }
  return false;
}

uint8_t activeModeInZone(uint8_t zone) {
  for (uint8_t i = 0; i < PELTIER_COUNT; i++) {
    if (peltierActive[i] && THERMAL_CHANNELS[i].zone == zone) {
      return modeCode(THERMAL_CHANNELS[i].mode);
    }
  }
  return MODE_NONE;
}

bool hasActiveOutputs() {
  for (uint8_t i = 0; i < PELTIER_COUNT; i++) {
    if (peltierActive[i]) return true;
  }
  for (uint8_t i = 0; i < VIBRATION_COUNT; i++) {
    if (vibrationOffAt[i] != 0) return true;
  }
  return false;
}

void stopPeltierIndex(uint8_t index, bool applyCooldown) {
  const bool wasActive = peltierActive[index];
  const uint8_t zone = THERMAL_CHANNELS[index].zone;
  const uint8_t stoppedMode = modeCode(THERMAL_CHANNELS[index].mode);
  const unsigned long now = millis();

  peltierActive[index] = false;
  peltierDuty[index] = 0;
  peltierOffAt[index] = 0;
  digitalWrite(THERMAL_CHANNELS[index].pin, LOW);

  if (!wasActive) return;
  if (applyCooldown) peltierCooldownUntil[index] = now + THERMAL_CHANNEL_COOLDOWN_MS;

  if (!hasActivePeltierInZone(zone)) {
    zoneLastMode[zone] = stoppedMode;
    zoneOppositeModeAllowedAt[zone] = now + THERMAL_OPPOSITE_MODE_PAUSE_MS;
  }
}

void setPeltierOff(int channel, bool applyCooldown = true) {
  if (channel == 0) {
    for (uint8_t i = 0; i < PELTIER_COUNT; i++) stopPeltierIndex(i, applyCooldown);
    sendStatus("thermal:off,ch=all");
    return;
  }

  if (channel < 1 || channel > PELTIER_COUNT) {
    sendChannelStatus("error:thermal:invalid-channel", channel);
    return;
  }

  stopPeltierIndex(channel - 1, applyCooldown);
  char message[40];
  snprintf(message, sizeof(message), "thermal:off,ch=%d", channel);
  sendStatus(message);
}

bool activatePeltier(int channel, const String& mode, int requestedDuty, unsigned long requestedDuration) {
  if (channel < 1 || channel > PELTIER_COUNT) {
    sendChannelStatus("error:thermal:invalid-channel", channel);
    return false;
  }

  const uint8_t index = channel - 1;
  const ThermalChannelConfig& config = THERMAL_CHANNELS[index];
  const String expectedMode = config.mode;
  if (mode != expectedMode) {
    sendChannelStatus("error:thermal:mode-mismatch", channel);
    return false;
  }

  const unsigned long now = millis();
  if (timePending(now, peltierCooldownUntil[index])) {
    sendChannelStatus("error:thermal:cooldown", channel);
    return false;
  }

  if (peltierActive[index]) {
    sendChannelStatus("error:thermal:already-active", channel);
    return false;
  }

  if (activePeltierCount() >= MAX_ACTIVE_PELTIERS) {
    sendChannelStatus("error:thermal:max-active", channel);
    return false;
  }

  const uint8_t requestedMode = modeCode(mode);
  const uint8_t zoneActiveMode = activeModeInZone(config.zone);
  if (zoneActiveMode != MODE_NONE && zoneActiveMode != requestedMode) {
    sendChannelStatus("error:thermal:zone-mode-conflict", channel);
    return false;
  }

  if (
    zoneActiveMode == MODE_NONE
    && zoneLastMode[config.zone] != MODE_NONE
    && zoneLastMode[config.zone] != requestedMode
    && timePending(now, zoneOppositeModeAllowedAt[config.zone])
  ) {
    sendChannelStatus("error:thermal:opposite-mode-pause", channel);
    return false;
  }

  const uint8_t duty = (uint8_t)constrain(requestedDuty, 1, (int)THERMAL_MAX_DUTY);
  const unsigned long duration = clampDuration(
    requestedDuration,
    THERMAL_MIN_DURATION_MS,
    THERMAL_MAX_DURATION_MS
  );

  peltierActive[index] = true;
  peltierDuty[index] = duty;
  peltierOffAt[index] = now + duration;

  char message[160];
  snprintf(
    message,
    sizeof(message),
    "thermal:on,ch=%d,kind=%s,zone=%s,side=%s,level=%s,duty=%u,dur=%lu",
    channel,
    config.kind,
    config.zoneName,
    config.sideName,
    config.levelName,
    duty,
    duration
  );
  sendStatus(message);
  return true;
}

void setVibrationMask(int requestedMask, bool on, unsigned long requestedDuration = 0) {
  if (requestedMask < 1 || requestedMask > 15) {
    sendStatus("error:vibration:invalid-mask");
    return;
  }

  const uint8_t mask = (uint8_t)requestedMask;
  const unsigned long duration = on
    ? clampDuration(requestedDuration, VIBRATION_MIN_DURATION_MS, VIBRATION_MAX_DURATION_MS)
    : 0;
  const unsigned long offAt = on ? millis() + duration : 0;

  for (uint8_t i = 0; i < VIBRATION_COUNT; i++) {
    if ((mask & (1 << i)) == 0) continue;
    digitalWrite(VIBRATION_CHANNELS[i].pin, on ? HIGH : LOW);
    vibrationOffAt[i] = offAt;
  }

  char message[64];
  snprintf(message, sizeof(message), "vibration:%s,mask=%u,dur=%lu", on ? "on" : "off", mask, duration);
  sendStatus(message);
}

void setVibration(int channel, bool on, unsigned long requestedDuration = 0) {
  const unsigned long duration = on
    ? clampDuration(requestedDuration, VIBRATION_MIN_DURATION_MS, VIBRATION_MAX_DURATION_MS)
    : 0;

  if (channel == 0) {
    const unsigned long offAt = on ? millis() + duration : 0;
    for (uint8_t i = 0; i < VIBRATION_COUNT; i++) {
      digitalWrite(VIBRATION_CHANNELS[i].pin, on ? HIGH : LOW);
      vibrationOffAt[i] = offAt;
    }
    char message[64];
    snprintf(message, sizeof(message), "vibration:%s,ch=all,dur=%lu", on ? "on" : "off", duration);
    sendStatus(message);
    return;
  }

  if (channel < 1 || channel > VIBRATION_COUNT) {
    sendStatus("error:vibration:invalid-channel");
    return;
  }

  const uint8_t index = channel - 1;
  digitalWrite(VIBRATION_CHANNELS[index].pin, on ? HIGH : LOW);
  vibrationOffAt[index] = on ? millis() + duration : 0;

  char message[128];
  snprintf(
    message,
    sizeof(message),
    "vibration:%s,ch=%d,zone=%s,side=%s,level=%s,dur=%lu",
    on ? "on" : "off",
    channel,
    VIBRATION_CHANNELS[index].zoneName,
    VIBRATION_CHANNELS[index].sideName,
    VIBRATION_CHANNELS[index].levelName,
    duration
  );
  sendStatus(message);
}

void allOff() {
  setPeltierOff(0, true);
  setVibration(0, false, 0);
  sendStatus("allOff:ok");
}

int extractInt(const String& json, const String& key, int fallback) {
  const String token = "\"" + key + "\":";
  int start = json.indexOf(token);
  if (start < 0) return fallback;
  start += token.length();
  while (start < json.length() && isspace((unsigned char)json[start])) start++;
  int end = json.indexOf(',', start);
  if (end < 0) end = json.indexOf('}', start);
  if (end < 0) return fallback;
  String raw = json.substring(start, end);
  raw.trim();
  raw.replace("\"", "");
  return raw.toInt();
}

String extractString(const String& json, const String& key, const String& fallback) {
  const String token = "\"" + key + "\":";
  int start = json.indexOf(token);
  if (start < 0) return fallback;
  start += token.length();
  while (start < json.length() && isspace((unsigned char)json[start])) start++;
  if (start >= json.length()) return fallback;

  if (json[start] == '"') {
    start++;
    int end = json.indexOf('"', start);
    if (end < 0) return fallback;
    return json.substring(start, end);
  }

  int end = json.indexOf(',', start);
  if (end < 0) end = json.indexOf('}', start);
  if (end < 0) return fallback;
  String raw = json.substring(start, end);
  raw.trim();
  return raw;
}

int extractChannel(const String& json, int fallback = 1) {
  String raw = extractString(json, "channel", String(fallback));
  raw.trim();
  raw.toLowerCase();
  if (raw == "all") return 0;
  return raw.toInt();
}

void markCommandActivity() {
  lastCommandAt = millis();
}

void handleVibrationJson(const String& json) {
  const int channel = extractChannel(json, 1);
  const int mask = extractInt(json, "mask", 0);
  String action = extractString(json, "action", "");
  action.trim();
  action.toLowerCase();
  const int durationRaw = extractInt(json, "duration", 0);

  if (action == "off" || action == "stop") {
    markCommandActivity();
    if (mask != 0) setVibrationMask(mask, false, 0);
    else setVibration(channel, false, 0);
    return;
  }

  if (action != "on") {
    sendStatus("error:vibration:invalid-action");
    return;
  }

  if (durationRaw <= 0) {
    sendStatus("error:vibration:duration-required");
    return;
  }

  markCommandActivity();
  if (mask != 0) setVibrationMask(mask, true, (unsigned long)durationRaw);
  else setVibration(channel, true, (unsigned long)durationRaw);
}

void handleThermalJson(const String& json) {
  const int channel = extractChannel(json, 1);
  String mode = extractString(json, "mode", "off");
  mode.trim();
  mode.toLowerCase();
  const int duty = extractInt(json, "duty", 0);
  const int durationRaw = extractInt(json, "duration", 0);

  if (mode == "off" || mode == "stop" || duty <= 0) {
    markCommandActivity();
    setPeltierOff(channel, true);
    return;
  }

  if (channel == 0) {
    sendStatus("error:thermal:all-on-disabled,ch=all");
    return;
  }

  if (durationRaw <= 0) {
    sendChannelStatus("error:thermal:duration-required", channel);
    return;
  }

  if (activatePeltier(channel, mode, duty, (unsigned long)durationRaw)) {
    markCommandActivity();
  }
}

void processCommand(String command) {
  command.trim();
  if (!command.length()) return;
  Serial.println("RX: " + command);

  if (!command.startsWith("{")) {
    sendStatus("error:json-required");
    return;
  }

  String type = extractString(command, "type", "");
  type.trim();

  if (type == "heartbeat") {
    markCommandActivity();
    return;
  }

  if (type == "ping") {
    markCommandActivity();
    sendStatus("pong");
    return;
  }

  if (type == "allOff") {
    markCommandActivity();
    allOff();
    return;
  }

  if (type == "vibration") {
    handleVibrationJson(command);
    return;
  }

  if (type == "thermal") {
    handleThermalJson(command);
    return;
  }

  sendStatus("error:unknown-command");
}

void updatePeltierOutputs() {
  const unsigned long now = millis();
  const unsigned long phase = now % PELTIER_PWM_PERIOD_MS;

  for (uint8_t i = 0; i < PELTIER_COUNT; i++) {
    if (peltierActive[i] && timeReached(now, peltierOffAt[i])) {
      stopPeltierIndex(i, true);
      char message[40];
      snprintf(message, sizeof(message), "thermal:auto-off,ch=%u", i + 1);
      sendStatus(message);
      continue;
    }

    const bool outputOn = peltierActive[i] && phase < peltierDuty[i];
    digitalWrite(THERMAL_CHANNELS[i].pin, outputOn ? HIGH : LOW);
  }
}

void updateVibrationOutputs() {
  const unsigned long now = millis();
  for (uint8_t i = 0; i < VIBRATION_COUNT; i++) {
    if (timeReached(now, vibrationOffAt[i])) {
      digitalWrite(VIBRATION_CHANNELS[i].pin, LOW);
      vibrationOffAt[i] = 0;
      char message[44];
      snprintf(message, sizeof(message), "vibration:auto-off,ch=%u", i + 1);
      sendStatus(message);
    }
  }
}

void updateCommandWatchdog() {
  if (!hasActiveOutputs()) return;
  const unsigned long now = millis();
  if ((long)(now - lastCommandAt) < (long)COMMAND_WATCHDOG_MS) return;

  allOff();
  sendStatus("safety:watchdog-all-off");
  lastCommandAt = now;
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    deviceConnected = true;
    markCommandActivity();
    sendStatus("ble:connected");
  }

  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    allOff();
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Desconectado; salidas apagadas");
  }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    String value = characteristic->getValue().c_str();
    value.replace("\r", "");

    int start = 0;
    while (start < value.length()) {
      int end = value.indexOf('\n', start);
      if (end < 0) end = value.length();
      processCommand(value.substring(start, end));
      start = end + 1;
    }
  }
};

void setupPins() {
  for (uint8_t i = 0; i < PELTIER_COUNT; i++) {
    pinMode(THERMAL_CHANNELS[i].pin, OUTPUT);
    digitalWrite(THERMAL_CHANNELS[i].pin, LOW);
    peltierActive[i] = false;
    peltierDuty[i] = 0;
    peltierOffAt[i] = 0;
    peltierCooldownUntil[i] = 0;
  }

  for (uint8_t i = 0; i < VIBRATION_COUNT; i++) {
    pinMode(VIBRATION_CHANNELS[i].pin, OUTPUT);
    digitalWrite(VIBRATION_CHANNELS[i].pin, LOW);
    vibrationOffAt[i] = 0;
  }
}

void setupBLE() {
  BLEDevice::init(DEVICE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* service = server->createService(SERVICE_UUID);
  BLECharacteristic* commandCharacteristic = service->createCharacteristic(
    COMMAND_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  commandCharacteristic->setCallbacks(new CommandCallbacks());

  statusCharacteristic = service->createCharacteristic(
    STATUS_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  statusCharacteristic->addDescriptor(new BLE2902());
  statusCharacteristic->setValue("ready");

  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->setMinPreferred(0x06);
  advertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();
}

void setup() {
  Serial.begin(115200);
  setupPins();
  delay(500);
  setupBLE();
  markCommandActivity();
  Serial.println("[SYSTEM] ChalecoVR listo");
  sendStatus("system:ready");
}

void loop() {
  updatePeltierOutputs();
  updateVibrationOutputs();
  updateCommandWatchdog();

  if (Serial.available()) {
    processCommand(Serial.readStringUntil('\n'));
  }

  delay(5);
}
