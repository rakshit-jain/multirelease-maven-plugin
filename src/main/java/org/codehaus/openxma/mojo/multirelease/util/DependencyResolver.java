package org.codehaus.openxma.mojo.multirelease.util;

import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

/**
 * Resolves the dependency of one project on the other project.
 * @author Rakshit Jain
 * 
 */
public class DependencyResolver {
	public List<DependencyMapper> getBuildOrder(List<DependencyMapper> availableProjects) {
		buildDependencyTree(availableProjects);
		Collections.sort(availableProjects);
		return availableProjects;
	}

	/**
	 * Builds the dependency tree by resolving the available project dependency.
	 * @param availableProjects list of available projects of type {@link DependencyMapper}.
	 */
	private void buildDependencyTree(List<DependencyMapper> availableProjects) {
		for (DependencyMapper availableProject : availableProjects) {
			Artifact availableArtifact = availableProject.getMavenProject()
					.getArtifact();
			for (DependencyMapper project : availableProjects) {
				if (!project.getMavenProject().getArtifact()
						.equals(availableArtifact)) {
					List<Dependency> dependencies = null;
					// Check if dependency management section is available.
					// Dependency management section will be used in case of multi module project.
					if (project.getMavenProject().getDependencyManagement() != null) {
						dependencies = project.getMavenProject()
								.getDependencyManagement().getDependencies();
					} else {
						dependencies = project.getMavenProject()
								.getOriginalModel().getDependencies();
					}
					// Check If available project dependency exist in other project dependencies.
					for (Dependency dependency : dependencies) {
						if (compareDependencyWithArtifact(dependency,
								availableArtifact)) {
							project.getDependencyProject().add(
									availableProject.getMavenProject());
						} else {
							for (MavenProject mavenProject : availableProject
									.getChildProject()) {
								if (compareDependencyWithArtifact(dependency,
										mavenProject.getArtifact())) {
									// Add dependent project to the list.
									project.getDependencyProject().add(
											mavenProject);
								}
							}

						}
					}
				}
			}

		}

	}

	/**
	 * Compare dependency with artifact. If artifact and group id are same then both are considered to be equal.
	 * @param dependency {@link Dependency}
	 * @param artifact {@link Artifact}
	 * @return true, if equal
	 */
	private boolean compareDependencyWithArtifact(Dependency dependency,
			Artifact artifact) {
		boolean equal = true;
		if (!artifact.getGroupId().equals(dependency.getGroupId())) {
			equal = false;
		} else if (!artifact.getArtifactId().equals(dependency.getArtifactId())) {
			equal = false;
		}
		// We don't consider the version range in the comparison, just the
		// resolved version
		return equal;
	}
}
