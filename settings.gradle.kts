rootProject.name = "geordi"

include("unitSim")
include("holodeck")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}