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
