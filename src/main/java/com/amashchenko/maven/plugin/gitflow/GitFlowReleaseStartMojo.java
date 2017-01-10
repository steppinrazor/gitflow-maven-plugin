/*
 * Copyright 2014-2016 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amashchenko.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.cli.CommandLineException;

import static org.codehaus.plexus.util.StringUtils.*;
import static java.text.MessageFormat.format;

/**
 * The git flow release start mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "release-start", aggregator = true)
public class GitFlowReleaseStartMojo extends AbstractGitFlowMojo {

    /**
     * Whether to use the same name of the release branch for every release.
     * Default is <code>false</code>, i.e. project version will be added to
     * release branch prefix. <br/>
     * <br/>
     * 
     * Note: By itself the default releaseBranchPrefix is not a valid branch
     * name. You must change it when setting sameBranchName to <code>true</code>
     * .
     * 
     * @since 1.2.0
     */
    @Parameter(property = "sameBranchName", defaultValue = "false")
    private boolean sameBranchName = false;

    /**
     * Release version to use instead of the default next release version in non
     * interactive mode.
     * 
     * @since 1.3.1
     */
    @Parameter(property = "releaseVersion", defaultValue = "")
    private String releaseVersion = "";

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            initGitFlowConfig();
            checkUncommittedChanges();

            if (!allowSnapshots) {
                checkSnapshotDependencies();
            }

            final String releaseBranch = gitFindBranches(gitFlowConfig.getReleaseBranchPrefix(), true);

            if (isNotBlank(releaseBranch)) {
                throw new MojoFailureException("Release branch already exists. Cannot start release.");
            }

            if (fetchRemote) {
                gitFetchRemoteAndCompare(gitFlowConfig.getDevelopmentBranch());
            }

            gitCheckout(gitFlowConfig.getDevelopmentBranch());

            final String currentVersion = getCurrentProjectVersion();

            String defaultVersion = null;

            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                defaultVersion = versionInfo.getReleaseVersionString();
            } catch (VersionParseException e) {
                getLog().error(e);
            }

            if (defaultVersion == null) {
                throw new MojoFailureException("Cannot get default project version.");
            }

            String version = null;
            if (settings.isInteractiveMode()) {
                try {
                    version = prompter.prompt("What is release version? [" + defaultVersion + "]");
                } catch (PrompterException e) {
                    getLog().error(e);
                }
            }

            if (isBlank(version)) {
                version = defaultVersion;
            }

            String branchName = gitFlowConfig.getReleaseBranchPrefix();
            if (!sameBranchName) {
                branchName += version;
            }

            gitCreateAndCheckout(branchName, gitFlowConfig.getDevelopmentBranch());

            if (!version.equals(currentVersion)) {
                mvnSetVersions(version);
                gitCommit(format(commitMessages.getReleaseStartMessage(), branchName));
            }

            if(pushRemote){
                gitPushAndTrack(branchName);
                gitPush(gitFlowConfig.getDevelopmentBranch(), false);
            }

            gitCheckout(branchName);
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
