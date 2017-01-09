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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

import static org.codehaus.plexus.util.StringUtils.*;

/**
 * The git flow feature start mojo.
 *
 * @author Aleksandr Mashchenko
 */
@Mojo(name = "feature-start", aggregator = true)
public class GitFlowFeatureStartMojo extends AbstractGitFlowMojo {

    /**
     * Whether to skip changing project version. Default is <code>false</code>
     * (the feature name will be appended to project version).
     *
     * @since 1.0.5
     */
    @Parameter(property = "skipFeatureVersion", defaultValue = "false")
    private boolean skipFeatureVersion = false;

    /**
     * Whether feature already in progress and use the uncommitted changes to start new branch. Default is <code>false</code>
     * (the feature name will be appended to project version).
     *
     * @since 1.3.2
     */
    @Parameter(property = "featureInProgress", defaultValue = "false")
    private boolean featureInProgress = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            initGitFlowConfig();

            if (!featureInProgress) {
                checkUncommittedChanges();
            }

            if (!featureInProgress && fetchRemote) {
                gitFetchRemoteAndCompare(gitFlowConfig.getDevelopmentBranch());
            }

            String featureName = null;
            try {
                while (isBlank(featureName)) {
                    featureName = prompter.prompt("What is a name of feature branch? " + gitFlowConfig.getFeatureBranchPrefix());
                }
            } catch (PrompterException e) {
                getLog().error(e);
            }

            featureName = deleteWhitespace(featureName);

            final boolean featureBranchExists = gitCheckBranchExists(gitFlowConfig.getFeatureBranchPrefix() + featureName);

            if (featureBranchExists) {
                throw new MojoFailureException("Feature branch with that name already exists. Cannot start feature.");
            }

            gitCreateAndCheckout(gitFlowConfig.getFeatureBranchPrefix() + featureName, gitFlowConfig.getDevelopmentBranch());

            if (!skipFeatureVersion) {
                final String currentVersion = getCurrentProjectVersion();

                String version = null;
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                    version = versionInfo.getReleaseVersionString() + "-" + featureName + "-" + Artifact.SNAPSHOT_VERSION;
                } catch (VersionParseException e) {
                    getLog().error(e);
                }

                if (isNotBlank(version)) {
                    mvnSetVersions(version);
                    gitCommit(commitMessages.getFeatureStartMessage());
                }
            }

            if (pushRemote) {
                gitPushAndTrack(gitFlowConfig.getFeatureBranchPrefix() + featureName);
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
