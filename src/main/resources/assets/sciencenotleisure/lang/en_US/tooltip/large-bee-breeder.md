{gold:Automatically breeds bees to meet your bee needs}
Select a breeding target in the controller; the machine automatically breeds recorded species to unlock it.
The default success chance is the species' base mutation chance.
Insert a princess, queen or drone to record its species without consuming it. Only queens can breed.
Place the required bee foundation blocks in an {white:input bus}; they are {green:not consumed}
Bee species have seven tiers from HV to UHV; the GUI shows the required voltage

Each cycle takes {white:{var:attemptSeconds} seconds} by default
Each parallel consumes {white:{var:baseEu} EU/t}
Each higher voltage tier {gold:doubles the parallel limit} and applies a lossy overclock.
Each parallel consumes 2 royal jelly for one extra lossy overclock.
Starting from {yellow:HV} glass, each higher glass tier {red:doubles} EU/t and adds {gold:+{var:glassChance}%} success chance
Requires a drone and a queen matching the two route parents.
Successful breeding produces a princess and a drone; the original drone has a 0.1% consumption chance.
Failed breeding returns the original queen. Interrupted batches preserve their bees.
To reproduce an archived species, both parents must belong to that species.
