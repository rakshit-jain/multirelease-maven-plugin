package org.codehaus.openxma.mojo.multirelease.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.project.MavenProject;

/**
 * Maps the dependency between parent and child projects and interdependent projects.
 * @author Rakshit Jain
 * 
 */
public class DependencyMapper implements Comparable<DependencyMapper> {

	/**
	 * Parent project.
	 */
	private MavenProject mavenProject;

	/**
	 * List of projects in which this project is referenced.
	 */
	private List<MavenProject> dependencyProject;

	/**
	 * Child projects of the parent project (If any).
	 */
	private List<MavenProject> childProject;

	public DependencyMapper() {
		dependencyProject = new ArrayList<MavenProject>();
		childProject = new ArrayList<MavenProject>();
	}

	public DependencyMapper(MavenProject mavenProject) {
		dependencyProject = new ArrayList<MavenProject>();
		childProject = new ArrayList<MavenProject>();
		this.mavenProject = mavenProject;
	}

	public MavenProject getMavenProject() {
		return mavenProject;
	}

	public void setMavenProject(MavenProject mavenProject) {
		this.mavenProject = mavenProject;
	}

	public List<MavenProject> getDependencyProject() {
		return dependencyProject;
	}

	public void setDependencyProject(List<MavenProject> dependency) {
		this.dependencyProject = dependency;
	}

	public List<MavenProject> getChildProject() {
		return childProject;
	}

	public void setChildProject(List<MavenProject> childProject) {
		this.childProject = childProject;
	}

	public int compareTo(DependencyMapper dependencyMapper) {
		// The project which has minimum number of dependent project will be built first.
		Set<MavenProject> dependentProjects = new HashSet<MavenProject>();
		for (MavenProject mavenProject : dependencyProject) {
			while (mavenProject.getParent() != null) {
				mavenProject = mavenProject.getParent();
			}
			dependentProjects.add(mavenProject);
		}
		int size = dependentProjects.size();
		dependentProjects.clear();
		for (MavenProject mavenProject : dependencyMapper
				.getDependencyProject()) {
			while (mavenProject.getParent() != null) {
				mavenProject = mavenProject.getParent();
			}
			dependentProjects.add(mavenProject);
		}
		return size - dependentProjects.size();
	}

	@Override
	public String toString() {
		return "GroupID: " + mavenProject.getGroupId() + " ArtifactID: "
				+ mavenProject.getArtifactId();
	}
}
