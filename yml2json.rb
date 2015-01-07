#!/usr/bin/env ruby
require 'json'
require 'yaml'

if(ARGV.count != 2)
  raise "Wrong amount of arguments, use yml2json.rb [yml_in_filename] [json_out_filename]"
end

ymlin,jsonout = ARGV

#p ymlin
#p jsonout

yml = YAML::load(IO.read(ymlin))
File.open( jsonout, 'w') {
  |f| f.write(yml.to_json)
}

