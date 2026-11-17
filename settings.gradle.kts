rootProject.name = "roza-edge"

include("spoon", "openrewrite")

project(":spoon").projectDir = file("spikes/spoon")
project(":openrewrite").projectDir = file("spikes/openrewrite")
