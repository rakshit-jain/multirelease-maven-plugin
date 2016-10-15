package org.codehaus.openxma.mojo.multirelease.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.PrintStreamLogger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Reads property defined in multirelease.properties file used to configure plugin.
 * @author Rakshit Jain
 * 
 */
public class PropertyResolver {

	private final static String MULTIRELEASE_PROPERTIES = "multirelease.properties";

	private static final String pluginKey = "multirelease-maven-plugin";
	/** Instance logger */
	private final static PrintStreamLogger log = new PrintStreamLogger();

	private Map<String, String> mergedProperties = new HashMap<String, String>();

	private final Map<String, String> individualProperties = new HashMap<String, String>();

	private static final PropertyResolver PROPERTY_RESOLVER = new PropertyResolver();

	private static final String DRY_RUN = "dryRun";

	/** Regex to identify property defined for all projects. **/
	private static final String propertyRegex = "^[A-Z a-z]+$";

	private PropertyResolver() {

	}

	public static PropertyResolver getInstance() {
		return PROPERTY_RESOLVER;
	}

	/**
	 * Gets the properties defined for a project in properties file.
	 * @param groupID Group Id of the maven project.
	 * @param artifactID Artifact Id of the maven project.
	 * @return the project properties
	 */
	public Map<String, String> getProjectProperties(String groupID, String artifactID) {
		// Key of the property files is groupID.artifactID.property
		String regex = groupID + "." + artifactID + ".([a-z A-Z]*)";
		Map<String, String> props = new HashMap<String, String>();
		if (!mergedProperties.isEmpty()) {
			Pattern pattern = Pattern.compile(regex);
			for (String key : mergedProperties.keySet()) {
				Matcher matcher = pattern.matcher(key);
				if (matcher.find()) {
					String match = matcher.group(1);
					if ((!match.equals(DRY_RUN)) && (!match.equals("resume"))) {
						props.put(match, mergedProperties.get(key));
					}
				}
			}
		}
		for (String key : individualProperties.keySet()) {
			// Add Individual property if project specific property is not present.
			if (props.get(key) == null) {
				props.put(key, individualProperties.get(key));
			}
		}
		// Remove dryRun property if present.
		props.remove(DRY_RUN);
		props.remove("allowTimestampedSnapshots");
		props.remove("autoVersionSubmodules");
		props.remove("releaseVersion");
		return props;
	}

	/**
	 * Merge properties defined in the system properties are given maximum preference following the
	 * multirelease.properties configuration and then the properties given in the configuration section.
	 * @param commandLineProperties the command line properties
	 * @param parentProject the parent project
	 * @throws MojoFailureException
	 */
	public void mergeProperties(Properties commandLineProperties, MavenProject parentProject, File propertyFile)
			throws MojoFailureException {
		Map<String, String> props = new HashMap<String, String>();
		// Add default SCM comment prefix.
		props.put("scmCommentPrefix", "[multi-release-plugin ] ");
		// Read Configuration properties.
		props.putAll(readConfigurationProperties(parentProject));
		// Read Multi-release property file properties.
		Properties properties = readMultiReleasePropertiesFile(propertyFile);
		if (!properties.isEmpty()) {
			for (Entry<Object, Object> entry : properties.entrySet()) {
				props.put((String) entry.getKey(), (String) entry.getValue());
			}
		}
		if (!commandLineProperties.isEmpty()) {
			for (String supportedProperty : getSupportedProperties()) {
				if (commandLineProperties.get(supportedProperty) != null) {
					props.put(supportedProperty, commandLineProperties.getProperty(supportedProperty));
				}
			}
		}
		findIndividualProperties(props);

		// Remove unsupported properties if present.
		props.remove(DRY_RUN);
		props.remove("allowTimestampedSnapshots");
		props.remove("autoVersionSubmodules");
		props.remove("releaseVersion");
		mergedProperties = props;
	}

	/**
	 * Add individual properties that are applicable to all projects.
	 */
	private void findIndividualProperties(Map<String, String> properties) {
		Pattern pattern = Pattern.compile(propertyRegex);
		for (String key : properties.keySet()) {
			Matcher matcher = pattern.matcher(key);
			if (matcher.find()) {
				individualProperties.put(key, properties.get(key));
			}
		}
	}

	/**
	 * Read configuration properties given in configuration section of maven plugin.
	 * @param mavenProject {@link MavenProject}
	 * @return map of properties defined.
	 */
	private Map<String, String> readConfigurationProperties(MavenProject mavenProject) {
		Map<String, String> properties = new HashMap<String, String>();
		Xpp3Dom xpp3Dom = null;
		for (String key : mavenProject.getOriginalModel().getBuild().getPluginsAsMap().keySet()) {
			if (key.contains(pluginKey)) {
				xpp3Dom = (Xpp3Dom) mavenProject.getOriginalModel().getBuild().getPluginsAsMap().get(key)
						.getConfiguration();
				break;
			}
		}
		if (xpp3Dom != null) {
			for (Xpp3Dom dom : xpp3Dom.getChildren()) {
				properties.put(dom.getName(), dom.getValue());
			}
		}
		return properties;
	}

	/**
	 * Read properties defined in the multirelease.properties file.
	 * @return the properties
	 */
	private Properties readMultiReleasePropertiesFile(File file) {
		FileInputStream fileInputStream = null;
		Properties properties = new Properties();
		try {
			if (file != null) {
				fileInputStream = new FileInputStream(file);
			} else {
				fileInputStream = new FileInputStream(MULTIRELEASE_PROPERTIES);
			}
			properties.load(fileInputStream);
		} catch (FileNotFoundException e) {
			log.info("multirelease.properties file not found");
		} catch (IOException e) {
			log.error("multirelease.properties file not found", e);

		} finally {
			try {
				if (fileInputStream != null) {
					fileInputStream.close();
				}
			} catch (IOException e) {
				// Do nothing.
			}
		}
		return properties;
	}

	/**
	 * Gets the properties supported by multi-release plugin.
	 * @return names of supported properties.
	 * @throws MojoFailureException the mojo failure exception
	 */
	private String[] getSupportedProperties() throws MojoFailureException {
		String[] props = { "addSchema", "checkModificationExcludeList", "checkModificationExcludes", "commitByProject",
				"completionGoals", "developmentVersion",
				"generateReleasePoms", "localRepoDirectory", "password", "mavenHome,mavenExecutorId", "pomFileName",
				"preparationGoals", "providerImplementations", "pushChanges", "remoteTagging",
				"resume", "scmCommentPrefix", "dryRun", "suppressCommitBeforeTag", "tag", "tagBase", "tagNameFormat",
				"useEditMode", "username", "waitBeforeTagging", "updateWorkingCopyVersions",
				"useReleaseProfile", "javaHome", "localCheckout", "releaseProfiles", "rollback" };

		return props;
	}

	public Map<String, String> getMergedProperties() {
		return mergedProperties;
	}
}
