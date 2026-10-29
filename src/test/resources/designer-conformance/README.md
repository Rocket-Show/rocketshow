# Conformance cases of the designer

A copy of `web/projects/designer/conformance` of the designer (Rocket-Show/show-designer, see its README there), which
`DesignerConformanceTest` plays: the engine has to come to exactly the values the preview of the designer comes to, for
every case. The expected values are recorded from the designer.

Don't change `cases` and `profiles` here: change them in the designer, record them there and copy them with
`testing/sync-designer-conformance.sh`. CI checks that they are the ones of the designer (master against its master,
every other branch against its develop).
