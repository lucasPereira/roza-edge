
dependencies {
    implementation("fr.inria.gforge.spoon:spoon-core:11.5.0")
    implementation("org.openrewrite:rewrite-java-25:8.90.4")
}

tasks.test {
    val universitySystemSubjectDirectory = rootProject.layout.projectDirectory.dir("subjects/university-system")
    systemProperty("universitySystemSubjectDirectory", universitySystemSubjectDirectory.asFile.absolutePath)
}
