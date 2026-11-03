Insert an analyzed seed into an input bus to archive its species and stats. The machine consumes one seed per new species
Select a target in the controller to breed it from archived parents, including intermediate generations
Newly bred species start at 1/1/1. Subsequent production uses their archived stats
Seed improvement uses {white:{var:washUU} L of UU-Matter} per tick at {white:{var:baseEu} EU/t}
Every {white:{var:washSeconds} seconds}, it attempts to raise one stat below 31 by one point
Place required substrates in an {white:input bus}; they are {green:not consumed}. Vanilla substrates need no item

Breeding grades 1–5 require {yellow:HV} / {dark_gray:EV} / {blue:IV} / {light_purple:LuV} / {aqua:ZPM}; check the GUI for each crop
Grade 1 takes {white:{var:firstSeconds} seconds}; each grade doubles the time. Higher voltage applies lossy overclocks
Starts with 1 parallel at {yellow:HV}; every {gold:two voltage tiers} doubles the parallel limit
Each parallel starts at {white:{var:baseEu} EU/t} and consumes {white:{var:fertilizer} L enriched fertilizer} per attempt
Breeding consumes {white:{var:washUU} L of UU-Matter} per tick for the whole machine. Breeding and seed improvement pause when UU-Matter runs out

Default chances for grades 1–5 are 100% / 80% / 60% / 40% / 20%; each parallel rolls independently
Failed attempts still consume fertilizer and catalysts; substrates are always retained
Cold starts reduce the base chance by 40 percentage points, down to zero
Each minute of breeding recovers 2 percentage points up to the base chance. Ten idle minutes reset the bonus
