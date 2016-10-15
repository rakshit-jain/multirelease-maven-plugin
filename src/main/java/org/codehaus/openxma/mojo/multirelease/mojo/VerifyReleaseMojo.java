package org.codehaus.openxma.mojo.multirelease.mojo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.openxma.mojo.multirelease.exception.ProcessException;
import org.codehaus.openxma.mojo.multirelease.pojo.Version;
import org.codehaus.openxma.mojo.multirelease.util.DependencyMapper;
import org.codehaus.openxma.mojo.multirelease.util.MavenReleasePluginExecutor;
import org.codehaus.openxma.mojo.multirelease.util.PropertyResolver;

/**
 * Execute maven clean install on all projects.Before executing clean install project dependency versions are updated to
 * verify the compatibility with updated versions.
 * @author Rakshit Jain
 */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, aggregator = true)
public class VerifyReleaseMojo extends AbstractReleaseMojo {

	/**
	 * Resume a previous release attempt from the point where it was stopped.
	 */
	@Parameter(defaultValue = "true", property = "rollback")
	private boolean rollback;

	/**
	 * Map having Group ID and Artifact ID as key and project version before release as value.
	 */
	private final Map<String, String> preReleaseVersion = new HashMap<String, String>();

	private final MavenReleasePluginExecutor executor = new MavenReleasePluginExecutor();

	// Pattern used to identify version is identified in property tag.
	protected final Pattern propertyTagPattern = Pattern.compile("\\$\\{(.*)\\}");

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			mergeProperties();
			List<DependencyMapper> projects = getBuildOrder();
			executeDryRun(projects);
		} catch (IOException e) {
			getLog().error("Plugin execution failed beacuse of I/O error \n", e);
			throw new MojoExecutionException("Plugin execution failed beacuse of I/O error\n", e);
		} catch (ProcessException e) {
			getLog().error("Plugin execution failed because of process executions failure\n", e);
			throw new MojoExecutionException("Plugin execution failed because of process executions failure\n", e);
		}
	}

	/**
	 * Execute dry run (clean, install) on all projects.
	 * 
	 * @param projects the list of available projects
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ProcessException the process exception
	 * @throws MojoExecutionException
	 */
	private void executeDryRun(List<DependencyMapper> projects) throws IOException, ProcessException,
			MojoExecutionException {
		PropertyResolver propertyResolver = PropertyResolver.getInstance();
		for (DependencyMapper dependencyMapper : projects) {
			checkSnapshotdependencies(dependencyMapper);
			MavenProject mavenProject = dependencyMapper.getMavenProject();
			Map<String, String> properties = propertyResolver.getProjectProperties(mavenProject.getGroupId(),
					mavenProject.getArtifactId());
			properties.get("username");
			properties.get("password");
			if (properties.get("developmentVersion") != null) {
				checkDevelopmentVersion(mavenProject, properties.get("developmentVersion"));
			}
			updateReleaseVersionMap(dependencyMapper, preReleaseVersion);
			getLog().info("Executing Dry Run.");
			File backupFile = null;

			if (rollback) {
				backupFile = new File(dependencyMapper.getMavenProject().getOriginalModel().getProjectDirectory()
						.getAbsolutePath() + File.separator + "pom-backup.xml");
				getLog().debug("Creating backup file.");
				FileUtils.copyFile(dependencyMapper.getMavenProject().getOriginalModel().getPomFile(), backupFile);
			}
			boolean dependencyUpdated = updateDependencyVersion(dependencyMapper);
			executor.dryRun(mavenProject);
			if (rollback) {
				if (dependencyUpdated) {
					getLog().debug("Copying backup file to original file");
					FileUtils.copyFile(backupFile, dependencyMapper.getMavenProject().getOriginalModel().getPomFile());
				}
				getLog().debug("Cleaning backup file.");
				backupFile.delete();
			}
		}
	}

	/**
	 * Updates release version map with the given project and its child project.
	 * @param dependencyMapper the dependency mapper
	 * @param mavenProject the maven project
	 */
	private void updateReleaseVersionMap(DependencyMapper dependencyMapper, Map<String, String> releaseVersionMap) {
		MavenProject mavenProject = dependencyMapper.getMavenProject();
		releaseVersionMap.put(
				mavenProject.getGroupId() + "." + mavenProject.getArtifactId(),
				mavenProject.getVersion());
		for (MavenProject project : dependencyMapper.getChildProject()) {
			releaseVersionMap.put(
					project.getGroupId() + "." + project.getArtifactId(),
					project.getVersion());

		}
	}

	/**
	 * Check development version is greater then current version of the project.
	 * @param mavenProject {@link MavenProject}
	 * @param entry the entry
	 * @throws MojoExecutionException the mojo execution exception
	 */
	private void checkDevelopmentVersion(MavenProject mavenProject, String version)
			throws MojoExecutionException {
		int result = Version.valueOf(version.trim()).compareTo(
				Version.valueOf(mavenProject.getVersion().replace("-SNAPSHOT", "").trim()));
		if (result <= 0) {
			throw new MojoExecutionException("Development version of " + mavenProject.getName()
					+ " should be greater then current version. The new development version is "
					+ version.trim() + " and old version is " + mavenProject.getVersion().trim());
		}
	}

	/**
	 * Update dependency version in POM file. IF phase is PRE release then the dependency versions are updated to use
	 * release versions. If phase is POST release then dependencies are updated to use SNAPSHOT version.
	 * @param dependencyMapper {@link DependencyMapper}
	 * @param phase execution phase of the project.
	 * @param projectVersion current version of the project.
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private boolean updateDependencyVersion(DependencyMapper dependencyMapper) throws FileNotFoundException,
			IOException {
		boolean dependencyUpdated = false;

		List<Dependency> dependencies = null;
		// If project is multi module then use dependency management section.
		if (dependencyMapper.getMavenProject().getOriginalModel().getDependencyManagement() == null) {
			dependencies = dependencyMapper.getMavenProject()
					.getOriginalModel().getDependencies();
		} else {
			dependencies = dependencyMapper.getMavenProject()
					.getOriginalModel().getDependencyManagement()
					.getDependencies();
		}
		for (Dependency dependency : dependencies) {
			String version = null;
			version = preReleaseVersion.get(
					dependency.getGroupId()
							+ "." + dependency.getArtifactId());
			if (version != null) {
				dependencyUpdated = true;
				// dependency version is defined in property tag of POM file.
				Matcher matcher = propertyTagPattern.matcher(dependency.getVersion());
				if (matcher.find()) {
					String key = matcher.group(1);
					dependencyMapper.getMavenProject()
							.getOriginalModel().getProperties().setProperty(key, version);
				} else {
					dependency.setVersion(version);
				}
			}
		}
		// Update POM if dependency is updated.
		if (dependencyUpdated) {
			writePOM(dependencyMapper.getMavenProject());
		}
		return dependencyUpdated;
	}

	/**
	 * Merge properties defined by user with class variablesS.
	 * 
	 * @throws MojoExecutionException the mojo execution exception
	 * @throws MojoFailureException the mojo failure exception
	 */
	private void mergeProperties() throws MojoExecutionException, MojoFailureException {
		getLog().info("Reading defined properties.");
		super.execute();
		Map<String, String> properties = PropertyResolver.getInstance().getMergedProperties();
		String property = properties.get("rollback");
		if (property != null) {
			rollback = Boolean.valueOf(property);
		}
	}

	private void checkSnapshotdependencies(DependencyMapper dependencyMapper) throws MojoExecutionException {
		List<MavenProject> projects = new ArrayList<MavenProject>();
		projects.add(dependencyMapper.getMavenProject());
		projects.addAll(dependencyMapper.getChildProject());
		for (MavenProject mavenProject : projects) {
			List<Dependency> dependencies = mavenProject.getOriginalModel().getDependencies();
			if (mavenProject.getOriginalModel().getDependencyManagement() != null) {
				dependencies.addAll(mavenProject.getOriginalModel().getDependencyManagement().getDependencies());
			}
			for (Dependency dependency : dependencies) {
				String version = dependency.getVersion();
				if (version != null) {
					// dependency version is defined in property tag of POM file.
					Matcher matcher = propertyTagPattern.matcher(dependency.getVersion());
					if (matcher.find()) {
						String key = matcher.group(1);
						version = mavenProject.getOriginalModel().getProperties().getProperty(key);
					}

					if (version.contains("SNAPSHOT")) {
						if (!isProjectDependency(dependency)) {
							getLog().error("Can't release " + mavenProject.getName()
									+ " because it contains SNAPSHOT dependency with key "
									+ dependency.getManagementKey());
							throw new MojoExecutionException("Can't release " + mavenProject.getName()
									+ " because it contains SNAPSHOT dependency with key "
									+ dependency.getManagementKey());
						}
					}
				}
			}
		}
	}

	private boolean isProjectDependency(Dependency dependency) {
		boolean projectDependency = false;
		for (MavenProject project : getReactorProjects()) {
			if (project.getArtifactId().equals(dependency.getArtifactId())
					&& project.getGroupId().equals(dependency.getGroupId())) {
				projectDependency = true;
				break;
			}
		}
		return projectDependency;
	}
}
