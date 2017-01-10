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

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;

import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * The git flow release finish mojo.
 *
 * @author Aleksandr Mashchenko
 */
@Mojo(name = "release-finish", aggregator = true)
public class GitFlowReleaseFinishMojo extends AbstractGitFlowMojo {

    /**
     * Whether to skip tagging the release in Git.
     */
    @Parameter(property = "skipTag", defaultValue = "false")
    private boolean skipTag = false;

    /**
     * Whether to keep release branch after finish.
     */
    @Parameter(property = "keepBranch", defaultValue = "false")
    private boolean keepBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     *
     * @since 1.0.5
     */
    @Parameter(property = "skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * Whether to rebase branch or merge. If <code>true</code> then rebase will
     * be performed.
     *
     * @since 1.2.3
     */
    @Parameter(property = "releaseRebase", defaultValue = "false")
    private boolean releaseRebase = false;

    /**
     * Whether to use <code>--no-ff</code> option when merging.
     *
     * @since 1.2.3
     */
    @Parameter(property = "releaseMergeNoFF", defaultValue = "true")
    private boolean releaseMergeNoFF = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            checkUncommittedChanges();

            if (!allowSnapshots) {
                checkSnapshotDependencies();
            }

            final String releaseBranch = gitFindBranches(gitFlowConfig.getReleaseBranchPrefix(), false).trim();

            if (isBlank(releaseBranch)) {
                throw new MojoFailureException("There is no release branch.");
            } else if (countMatches(releaseBranch, gitFlowConfig.getReleaseBranchPrefix()) > 1) {
                throw new MojoFailureException("More than one release branch exists. Cannot finish release.");
            }

            if (fetchRemote) {
                gitFetchRemoteAndCompare(gitFlowConfig.getProductionBranch());
            }

            if (!skipTestProject) {
                gitCheckout(releaseBranch);
                mvnCleanTest();
            }

            gitCheckout(gitFlowConfig.getProductionBranch());
            gitMerge(releaseBranch, releaseRebase, releaseMergeNoFF);

            final String currentVersion = getCurrentProjectVersion();

            if (!skipTag) {
                gitCheckout(releaseBranch);
                String tagVersion = getCurrentProjectVersion();
                gitTag(gitFlowConfig.getVersionTagPrefix() + tagVersion, format(commitMessages.getTagReleaseMessage(), gitFlowConfig.getVersionTagPrefix() + tagVersion));
            }

            gitCheckout(gitFlowConfig.getProductionBranch());

            String nextSnapshotVersion = null;

            //Not using DefaultVersionInfo here because it increments the incremental version rather than the minor version
            final DefaultArtifactVersion v = new DefaultArtifactVersion(currentVersion);

            switch(countMatches(currentVersion, '.')){
                case 0:
                    nextSnapshotVersion = format("{0}-SNAPSHOT",v.getMajorVersion() + 1);
                    break;
                case 1:
                    nextSnapshotVersion = format("{0}.{1}-SNAPSHOT",v.getMajorVersion(), v.getMinorVersion() + 1);
                    break;
                case 2:
                    nextSnapshotVersion = format("{0}.{1}.{2}-SNAPSHOT",v.getMajorVersion(), v.getMinorVersion() + 1, v.getIncrementalVersion());
                    break;
                default:
                    throw new MojoExecutionException("Expected version with <=2 periods(.) but '" + currentVersion + "' had more.");
            }


            if (isBlank(nextSnapshotVersion)) {
                throw new MojoFailureException("Next snapshot version is blank.");
            }

            mvnSetVersions(nextSnapshotVersion);

            gitCommit(format(commitMessages.getReleaseFinishMessage(),releaseBranch));

            if (installProject) {
                mvnCleanDeploy();
            }

            if (!keepBranch) {
                gitBranchDelete(releaseBranch);
            }

            if (pushRemote) {
                gitPush(gitFlowConfig.getProductionBranch(), !skipTag);
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
