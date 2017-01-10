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

import static org.codehaus.plexus.util.StringUtils.*;

/**
 * The git flow hotfix finish mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "hotfix-finish", aggregator = true)
public class GitFlowHotfixFinishMojo extends AbstractGitFlowMojo {

    /** Whether to skip tagging the hotfix in Git. */
    @Parameter(property = "skipTag", defaultValue = "false")
    private boolean skipTag = false;

    /** Whether to keep hotfix branch after finish. */
    @Parameter(property = "keepBranch", defaultValue = "false")
    private boolean keepBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     * 
     * @since 1.0.5
     */
    @Parameter(property = "skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            checkUncommittedChanges();

            final String hotfixBranches = gitFindBranches(gitFlowConfig.getHotfixBranchPrefix(), false);

            if (isBlank(hotfixBranches)) {
                throw new MojoFailureException("There is no hotfix branches.");
            }

            if (fetchRemote) {
                gitFetchRemoteAndCompare(gitFlowConfig.getProductionBranch());
            }

            String[] branches = hotfixBranches.split("\\r?\\n");

            List<String> numberedList = new ArrayList<String>();
            StringBuilder str = new StringBuilder("Hotfix branches:")
                    .append(LS);
            for (int i = 0; i < branches.length; i++) {
                str.append((i + 1) + ". " + branches[i] + LS);
                numberedList.add(String.valueOf(i + 1));
            }
            str.append("Which hotfix branch to finish");

            String hotfixNumber = null;
            try {
                while (isBlank(hotfixNumber)) {
                    hotfixNumber = prompter.prompt(str.toString(), numberedList);
                }
            } catch (PrompterException e) {
                getLog().error(e);
            }

            String hotfixBranchName = null;
            if (hotfixNumber != null) {
                int num = Integer.parseInt(hotfixNumber);
                hotfixBranchName = branches[num - 1];
            }

            if (isBlank(hotfixBranchName)) {
                throw new MojoFailureException("Hotfix branch name to finish is blank.");
            }

            if (!skipTestProject) {
                gitCheckout(hotfixBranchName);
                mvnCleanTest();
            }

            if (!skipTag) {
                String tagVersion = getCurrentProjectVersion();
                gitTag(gitFlowConfig.getVersionTagPrefix() + tagVersion, commitMessages.getTagHotfixMessage());
            }

            if (installProject) {
                mvnCleanDeploy();
            }

            gitCheckout(gitFlowConfig.getProductionBranch());
            String prodVersion = getCurrentProjectVersion();
            getLog().info("Prod version = " + prodVersion);
            gitCheckout(hotfixBranchName);
            mvnSetVersions(prodVersion);
            gitCommit("Set hotfix pom to " + prodVersion + " from " + gitFlowConfig.getProductionBranch() );
            gitCheckout(gitFlowConfig.getProductionBranch());

            try {
                gitMergeNoff(hotfixBranchName);
            }catch(MojoFailureException | CommandLineException e){
                getLog().error("Error occured with merging '" + hotfixBranchName + "' into '" + gitFlowConfig.getProductionBranch() + "'");
                throw e;
            }

            final String releaseBranch = gitFindBranches(gitFlowConfig.getReleaseBranchPrefix(), true);

            if (isNotBlank(releaseBranch)) {
                gitCheckout(releaseBranch);
                String releaseVersion = getCurrentProjectVersion();
                gitCheckout(hotfixBranchName);
                mvnSetVersions(releaseVersion);
                gitCommit("Set hotfix pom to " + releaseVersion + " from " + releaseBranch);
                gitCheckout(releaseBranch);

                try {
                    gitMergeNoff(hotfixBranchName);
                }catch (MojoFailureException | CommandLineException e){
                    getLog().error("Error occured with merging '" + hotfixBranchName + "' into '" + releaseBranch + "'");
                    throw e;
                }
            }

            if (!keepBranch) {
                gitBranchDeleteForce(hotfixBranchName);
            }

            if (pushRemote) {
                gitPush(gitFlowConfig.getProductionBranch(), !skipTag);
                if (isNotBlank(releaseBranch)) {
                    gitPush(releaseBranch, !skipTag);
                }
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}