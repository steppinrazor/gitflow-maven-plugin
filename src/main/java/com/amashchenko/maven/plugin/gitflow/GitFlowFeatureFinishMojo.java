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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.cli.CommandLineException;

import static java.lang.String.*;
import static org.codehaus.plexus.util.StringUtils.*;

/**
 * The git flow feature finish mojo.
 *
 * @author Aleksandr Mashchenko
 */
@Mojo(name = "feature-finish", aggregator = true)
public class GitFlowFeatureFinishMojo extends AbstractGitFlowMojo {

    /**
     * Whether to keep feature branch after finish.
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
     * Whether to squash feature branch commits into a single commit upon
     * merging.
     *
     * @since 1.2.3
     */
    @Parameter(property = "squashFeature", defaultValue = "true")
    private boolean squashFeature = true;

    /**
     * If you did a rebase -i on a feature branch then set this to true to do a ffwd merge
     *
     * @since 1.3.2
     */
    @Parameter(property = "ffwdFeature", defaultValue = "false")
    private boolean ffwdFeature = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            checkUncommittedChanges();

            final String featureBranches = gitFindBranches(gitFlowConfig.getFeatureBranchPrefix(), false);

            if (isBlank(featureBranches)) {
                throw new MojoFailureException("There are no feature branches.");
            }

            if (fetchRemote) {
                gitFetchRemoteAndCompare(gitFlowConfig.getDevelopmentBranch());
            }

            final String[] branches = featureBranches.split("\\r?\\n");

            List<String> numberedList = new ArrayList<>();
            StringBuilder str = new StringBuilder("Feature branches:").append(LS);
            for (int i = 0; i < branches.length; i++) {
                str.append((i + 1) + ". " + branches[i] + LS);
                numberedList.add(valueOf(i + 1))
            }
            str.append("Choose feature branch to finish:");

            String featureNumber = null;
            try {
                while (isBlank(featureNumber)) {
                    featureNumber = prompter.prompt(str.toString(), numberedList);
                }
            } catch (PrompterException e) {
                getLog().error(e);
            }

            String featureBranchName = null;
            if (featureNumber != null) {
                int num = Integer.parseInt(featureNumber);
                featureBranchName = branches[num - 1];
            }

            if (isBlank(featureBranchName)) {
                throw new MojoFailureException("Feature branch name to finish is blank.");
            }

            if (!skipTestProject) {
                gitCheckout(featureBranchName);
                mvnCleanTest();
            }

            gitCheckout(gitFlowConfig.getDevelopmentBranch());

            if (squashFeature && !ffwdFeature) {
                gitMergeSquash(featureBranchName);
                gitCommit(featureBranchName);
            } else if (ffwdFeature) {
                gitMerge(featureBranchName, false, false);
            } else {
                gitMergeNoff(featureBranchName);
                gitCommit(featureBranchName);
            }

            final String currentVersion = getCurrentProjectVersion();

            final String featureName = featureBranchName.replaceFirst(gitFlowConfig.getFeatureBranchPrefix(), "");

            if (currentVersion.contains("-" + featureName)) {
                final String version = currentVersion.replaceFirst("-" + featureName, "");

                mvnSetVersions(version);

                gitCommit(commitMessages.getFeatureFinishMessage());
            }

            if (installProject) {
                mvnDeploy();
            }

            if (!keepBranch) {
                if (squashFeature || ffwdFeature) {
                    gitBranchDeleteForce(featureBranchName);
                } else {
                    gitBranchDelete(featureBranchName);
                }
            }

            if (pushRemote) {
                gitPush(gitFlowConfig.getDevelopmentBranch(), false);
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
