math.randomseed(os.time())

names = { "Maverick", "Goose", "Viper", "Iceman", "Merlin", "Sundown", "Cougar", "Hollywood", "Wolfman", "Jester" }

request = function()
    headers = {}
    headers["Content-Type"] = "application/json"
    body = '{"key": "' .. math.random(1, 10000) .. '","value": ' .. math.random(1,10) .. '}'
    -- print(body)
    return wrk.format("POST", "/increment", headers, body)
end