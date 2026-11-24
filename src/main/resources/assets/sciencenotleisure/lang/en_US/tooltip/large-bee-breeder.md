{gold:Automatically breeds bees to meet your bee needs}
Select a breeding target in the controller; the machine automatically breeds recorded species to unlock it.
The default success chance is the species' base mutation chance.
Insert a princess, queen or drone to record its species without consuming it.
Place the required bee foundation blocks in an {white:input bus}; they are {green:not consumed}
Bee species have seven tiers from HV to UHV; the GUI shows the required voltage

Each cycle takes {white:{var:attemptSeconds} seconds} by default
Each parallel consumes {white:{var:baseEu} EU/t}
Each higher voltage tier {gold:doubles the parallel limit} and applies a lossy overclock.
Each parallel consumes 2 royal jelly for one extra lossy overclock.
Starting from {yellow:HV} glass, each higher glass tier {red:doubles} EU/t and adds {gold:+{var:glassChance}%} success chance
Breeding uses a princess or queen and a drone matching the registered route parents, prepared internally when needed.
Success produces the resulting princess, keeping intermediates inside the machine; the original female has a 1% retention chance and the drone has a 0.1% consumption chance.
Failed attempts leave both parents in their inventories. Interrupted batches refund consumed parents, returning internal bees to the bank.

Insert primitive species once to store seeds; intermediate parents are prepared internally.
Additional owned bee species can be inserted to shorten the remaining route.
Every successful species retains its genome in the machine; shutdowns and controller items preserve the bank.
Automatic mode exports princesses after the target is stored; drone and princess export modes are also available.
Parent preparation and seed export each take exactly {white:6.4 seconds per bee}, without overclocking or parallels, and still require the species voltage.
