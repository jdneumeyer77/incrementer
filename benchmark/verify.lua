math.randomseed(os.time())

names = { "Maverick", "Goose", "Viper", "Iceman", "Merlin", "Sundown", "Cougar", "Hollywood", "Wolfman", "Jester" }

key = "test"

local counter = 1
local threads = {}

function setup(thread)
   thread:set("id", counter)
   table.insert(threads, thread)
   counter = counter + 1
end

function init(args)
   total  = 0
end

function request()
    headers = {}
    headers["Content-Type"] = "application/json"
    rand = math.random(1,10)
    total = total + rand    
    body = '{"key": "' .. key .. '","value": ' .. rand .. '}'
    --print(body)
    return wrk.format("POST", "/increment", headers, body)
end


function done(summary, latency, requests)
   print(summary)
   print(latency)
   for index, thread in ipairs(threads) do
      local id        = thread:get("id")
      local total  = thread:get("total")
      local msg = "thread %d total %d"
      print(msg:format(id, total))
   end
end