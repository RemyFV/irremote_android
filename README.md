# irremote

IR remote for my three devices, replacing irplus and IrCode Finder. needs a phone with an IR blaster.

- LG LHB home theater (codes from irplus, Pronto hex)
- Panasonic TX-48CX400E TV (codes from IrCode Finder, RC5)
- Samsung UE24H4003AW TV (codes from IrCode Finder, NECx2 / Samsung32)

one tab per remote, same button grid for all of them, plus a home screen widget: pick a remote and 1-4 buttons when you place it.

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

- `protocol`: `pronto` (full Pronto hex string), `rc5` (3 hex digits, IrCode Finder style) or `necx2` (8 hex digits)
- `color`: `red`, `green`, `blue`, `yellow`, or leave it out for grey

the RC5 and NECx2 encoders produce the same timings as IrCode Finder. the LG codes only send the first frame of the Pronto sequence, irplus also sent a cut-off repeat frame after it.

## build

JDK 21 and Android SDK 36, then:

```
./gradlew installDebug
```

minimum Android 12.
