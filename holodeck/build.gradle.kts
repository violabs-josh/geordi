version = "1.0.0-SNAPSHOT"

dependencies {
    testImplementation(project(":unitSim"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}