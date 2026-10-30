# Fixture profiles for the tests

A few profiles of the fixture library the units download (`https://www.rocketshow.net/designer/downloads/fixtures.zip`),
which is built from the [Open Fixture Library](https://github.com/OpenLightingProject/open-fixture-library) (MIT
license). Each one stands for something the designer engine has to handle:

| Profile | Why |
|---|---|
| `stairville/mh-100` | Moving head with 16 bit pan and tilt, the fixture of the designer template. |
| `dts/xr4-spot` | A channel with slots of a wheel the profile doesn't have. |
| `adb/alc4` | Pixel matrix with a pixel group. |
| `etc/source-four-led-series-2-daylight-hd` | Unused channels (`null`) inside the modes. |
| `elation/uni-bar` | A simple RGB bar. |
| `chauvet-dj/colorband-pix` | Pixels given by their count (keys "1" to "12"), channels repeated for pixel groups (halves, thirds, quarters), channels named after the group of all pixels. |
| `ibiza-light/ls-005led` | A dimmer sharing its channel with a strobe, its brightness not given. |
| `varytec/easy-move-xs-hp-wash-7x8w-rgbw` | A dimmer sharing its channel with a strobe, its brightness in percent. |
| `panasonic/pt-rz120` | A dimmer going from bright to off. |
| `hong-yi/hy-g60` | Split colors of a color wheel (slot 1.5). |

The profiles of `test` are written for the cases: `rgb-par`, `rgb` and `moving-head` light the scenarios, while
`shared-dimmer` (several ranges of brightness on one channel), `wheels` (a gobo wheel before a color wheel, a channel
named differently than its wheel, wheels named in a list) and `pixel-bar` (channels per channel, a dimmer of its own,
the channels of a pixel named directly) are lit on the edges of what they have (`channel-values-edges.json`).
`beam` (a shutter, a strobe speed, a zoom, a focus, an iris, a frost, a color temperature and a prism, their ranges
running both ways round, in words and in numbers), `strobe-par` (a strobe sharing its channel with the dimmer, "no
function" switching another one off, white, amber and UV), `cmy-spot` (colors mixed with filters), `mover` (a movement
speed given as a speed and as the time a movement takes, a flash duration in milliseconds and seconds, a beam angle),
`hazer` (a kind of fog with "no function" below it, a range putting out nothing, an output in cubic metres per minute)
and `macro-par` (color macros, alone and next to colors of its own) play the capabilities set as a position, the colors
of a fixture mixing them with filters or showing them on its macros and the fixtures identified from the fixture pool
(`channel-values-capabilities.json`). `wide-mover` (a pan over 630°, a tilt running from 90° to -90° and
commands to reset itself and to switch its lamp on) and `effects-spot` (gobos shaking, rotations turning either way,
on one range or on two, a rotation turning to an angle only, the pulse of an iris and of a frost sharing their channels
and the blades of a framing shutter) point, aim and move the moving heads, next to `moving-head` and `mover`, and play
the rotations, the blades, the pulses and the shakes (`channel-values-movements.json`).
