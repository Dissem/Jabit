package ch.dissem.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Sets the version as follows:
 * <ul>
 *     <li>If the branch is 'master', the version is set to the latest tag (which is expected to be set by Git flow)</li>
 *     <li>Otherwise, the version is set to the branch name, with '-SNAPSHOT' appended</li>
 * </ul>
 */
class GitFlowVersion implements Plugin<Project> {
    def getBranch(Project project) {
        def stdout = new ByteArrayOutputStream()
        project.exec {
            commandLine 'git', 'rev-parse', '--abbrev-ref', 'HEAD'
            standardOutput = stdout
        }
        return stdout.toString().trim()
    }

    def getTag(Project project) {
        def stdout = new ByteArrayOutputStream()
        project.exec {
            commandLine 'git', 'describe', '--abbrev=0'
            standardOutput = stdout
        }
        return stdout.toString().trim()
    }

    def isRelease(Project project) {
        return "master" == getBranch(project);
    }

    def getVersion(Project project) {
        if (project.ext.isRelease) {
            return getTag(project)
        } else {
            def branch = getBranch(project)
            if ("develop" == branch) {
                return "development-SNAPSHOT"
            }
            return branch.replaceAll("/", "-") + "-SNAPSHOT"
        }
    }

    @Override
    void apply(Project project) {
        project.ext.isRelease = isRelease(project)
        project.version = getVersion(project)

        project.task('version') << {
            println "Version deduced from git: '${project.version}'"
        }
    }
}
