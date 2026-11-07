-- KVR base controller for CC: Tweaked (0029). Put a wireless (or ender) modem on the computer,
-- save as "startup.lua". Glasses with a modem in their slot within range talk to it:
--
--   JS in the glasses:  kvr.emit('base:toggle_power', { state: true })
--                       kvr.on('base:sensor_update', (data, meta) => gauge.setValue(data.value))
--                       kvr.rednet.send(<this computer's id>, { cmd: 'ping' }, 'kvr_rpc')
--
-- Protocol 'kvr_events' carries {type = '<ns>:<event>', ...data} both ways.

local SIDE_OUT = "top"         -- redstone output for base:toggle_power
local SENSOR_SIDE = "back"     -- analog input reported every 5 s

peripheral.find("modem", rednet.open)
print("KVR base, rednet id " .. os.getComputerID())

local function sensorLoop()
  while true do
    rednet.broadcast({ type = "base:sensor_update", value = redstone.getAnalogInput(SENSOR_SIDE) }, "kvr_events")
    sleep(5)
  end
end

local function eventLoop()
  while true do
    local id, msg, protocol = rednet.receive()
    if protocol == "kvr_events" and type(msg) == "table" then
      print(("#%d %s"):format(id, tostring(msg.type)))
      if msg.type == "base:toggle_power" then
        redstone.setOutput(SIDE_OUT, msg.state == true)
        rednet.send(id, { type = "base:power_state", state = redstone.getOutput(SIDE_OUT) }, "kvr_events")
      end
    elseif protocol == "kvr_rpc" and type(msg) == "table" and msg.cmd == "ping" then
      rednet.send(id, { pong = os.epoch("utc"), label = os.getComputerLabel() }, "kvr_rpc")
    end
  end
end

parallel.waitForAny(sensorLoop, eventLoop)
