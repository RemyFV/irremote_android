# irremote

IR remote for my devices, replacing irplus and IrCode Finder. needs a phone with an IR blaster.

- LG LHB home theater (codes from irplus, Pronto hex)
- Panasonic TX-48CX400E TV (codes from IrCode Finder, RC5)
- Samsung UE24H4003AW TV (codes from IrCode Finder, NECx2 / Samsung32)
- Hitachi heat pump with the RAR-6NE1 remote (frame from HeatpumpIR's RAR-5E1 encoder, same protocol)

one tab per remote, same button grid for all of them, plus a home screen widget: pick a remote and 1-4 buttons when you place it.

## hitachi ac

the AC remote is stateful: every press sends the full state (power, mode, temperature, fan speed, swing), so the tab is a form instead of a button grid. set the fields and hit ON / SEND, or OFF to turn the unit off with the same fields. no toggle on purpose: with several units the app cannot know which one is on. the last state sent is kept and shown at the top.

the weekly schedule of the real remote is not supported, nobody has decoded that frame yet.

## codes

everything lives in `app/src/main/assets/remotes.json`, edit it and rebuild:

```json
{
  "name": "Samsung TV",
  "protocol": "necx2",
  "columns": 4,
  "buttons": [
    { "label": "POWER", "code": "E0E040BF", "color": "red" }
  ]
}
```

- `protocol`: `pronto` (full Pronto hex string), `rc5` (3 hex digits, IrCode Finder style), `necx2` (8 hex digits) or `hitachi_ac` (actions: `on`, `off`, `power` (toggle), `temp+`, `temp-`, `fan`, `swingV`, `swingH`, `mode:heat` etc, only used by widgets, the app shows the form)
- `color`: `red`, `green`, `blue`, `yellow`, or leave it out for grey

the RC5 and NECx2 encoders produce the same timings as IrCode Finder. the LG codes only send the first frame of the Pronto sequence, irplus also sent a cut-off repeat frame after it.

## reorder buttons

`tools/reorder.py` opens a page on localhost:8765 with the same grid as the app: drag a button onto the spot it should take, save, rebuild. stdlib only.

## build

JDK 21 and Android SDK 36, then:

```
./gradlew installDebug
```

minimum Android 12.
