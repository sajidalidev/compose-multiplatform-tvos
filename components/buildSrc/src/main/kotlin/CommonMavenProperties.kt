import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure

fun Project.configureMavenPublication(
    groupId: String,
    artifactId: String,
    name: String,
    description: String = name
) {
    extensions.configure<PublishingExtension> {
        publications {
            all {
                val publication = this as MavenPublication

                //work around to fix an android publication artifact ID
                //https://youtrack.jetbrains.com/issue/KT-53520
                afterEvaluate {
                    publication.groupId = groupId
                    publication.mppArtifactId = artifactId
                }

                pom {
                    this.name.set(name)
                    this.description.set(description)
                    url.set("https://github.com/sajidalidev/compose-tvos")
                    licenses {
                        license {
                            this.name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    scm {
                        url.set("https://github.com/sajidalidev/compose-multiplatform")
                        connection.set("scm:git:https://github.com/sajidalidev/compose-multiplatform.git")
                    }
                    developers {
                        developer {
                            id.set("sajidalidev")
                            this.name.set("Sajid Ali")
                        }
                    }
                }
            }
        }
    }
}