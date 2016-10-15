package org.codehaus.openxma.mojo.multirelease.mojo;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.openxma.mojo.multirelease.exception.ProcessException;
import org.codehaus.openxma.mojo.multirelease.util.MavenReleasePluginExecutor;

/**
 * Clean up after a release preparation. This is done automatically after a successful <tt>multirelease:release</tt>, so
 * is best served for cleaning up a failed or abandoned release, or a dry run. Note that only the working copy is
 * cleaned up, no previous steps are rolled back.
 * 
 * @author Rakshit Jain
 */
@Mojo(name = "clean", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, aggregator = true)
public class CleanReleaseMojo extends AbstractReleaseMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			new MavenReleasePluginExecutor().clean(getReactorProjects());
			File releaseProperty = getReleasePropertyfile();
			if (releaseProperty.exists()) {
				releaseProperty.delete();
			}
		} catch (IOException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		} catch (ProcessException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}
}
