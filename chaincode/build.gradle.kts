plugins {
    java
    application
}

group = "com.chainsettle"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    implementation("org.slf4j:slf4j-api:2.0.13")

    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.assertj:assertj-core:3.26.0")
}

application {
    mainClass.set("org.hyperledger.fabric.contract.ContractRouter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("token-settlement")
}
