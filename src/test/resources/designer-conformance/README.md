# Conformance cases of the designer

A copy of two kinds of the cases of `web/projects/designer/conformance` of the designer (Rocket-Show/show-designer,
see its README there), with the fixture profiles they name:

- `channelValues` (`channel-values-*.json`): the shared lighting engine plays them here, through engine.wasm in the
  JVM, and comes to the digests of the engine (`src/test/resources/lighting-engine/core-digests.json`):
  `LightingEngineConformanceTest` plays the JSON of the cases, `DesignerProjectJsonTest` hands them over as Rocket Show
  does (read into its model, migrated and written again).
- `migration` (`migration*.json`): `DesignerMigrationConformanceTest` migrates the projects of older versions and has
  to come to exactly the projects the designer migrated them to.

Don't change `cases` and `profiles` here: change them in the designer, record them there and copy them with
`testing/sync-designer-conformance.sh`. CI checks that they are the ones of the designer (master against its master,
every other branch against its develop).
