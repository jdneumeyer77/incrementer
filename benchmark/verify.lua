-- Run with only 10 threads and less then 10 seconds

-- require "socket"
-- json = require "json"

math.randomseed(os.time())

names = { "Maverick", "Goose", "Viper", "Iceman", "Merlin", "Sundown", "Cougar", "Hollywood", "Wolfman", "Jester" }

local counter = 1
local threads = {}

function setup(thread)
   thread:set("id", counter)
   table.insert(threads, thread)
   counter = counter + 1

   -- Bummer this doesn't work either.
   -- headers = {}
   -- wrk.format("DELETE", "/increment", headers)
end

function init(args)
   total  = 0
   key = names[id]
end

function request()
    headers = {}
    headers["Content-Type"] = "application/json"
    rand = math.random(1,10)
    total = total + rand    
    body = '{"key": "' .. key .. '","value": ' .. rand .. '}'
   -- print(body)
    return wrk.format("POST", "/increment", headers, body)
end

-- Doesn't work... :(
-- function verify(id) 
--    data = wrk.format("GET", "/increment/" .. id)
--    extracted = json.decode(data.body)

--    return extracted["value"]
-- end

function done(summary, latency, requests)
   for index, thread in ipairs(threads) do
      local id        = thread:get("key")
      local total  = thread:get("total")
      -- local actual = verify(id)
      -- manually verify a few. I think there's
      -- cases where it incremented the total then
      -- the thread before it made the request.
      local msg = "thread %s total %d"
      print(msg:format(id, total))
   end
end