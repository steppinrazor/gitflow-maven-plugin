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
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

import java.util.ArrayList;
import java.util.List;

/**
 * The git flow hotfix start mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "hotfix-start", aggregator = true)
public class GitFlowHotfixStartMojo extends AbstractGitFlowMojo {

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            final String hotfixTags = gitFindTags(gitFlowConfig.getVersionTagPrefix());

            if (StringUtils.isBlank(hotfixTags)) {
                throw new MojoFailureException("There are not tags that fit pattern " + gitFlowConfig.getVersionTagPrefix() + "*");
            }

            final String[] tags = hotfixTags.split("\\r?\\n");

            List<String> numberedList = new ArrayList<>();
            StringBuilder str = new StringBuilder("Release Tags:")
                    .append(LS);
            for (int i = 0; i < tags.length; i++) {
                str.append((i + 1) + ". " + tags[i] + LS);
                numberedList.add(String.valueOf(i + 1));
            }
            str.append("Choose tag to create hotfix:");

            String tagChoice = null;
            try {
                while (StringUtils.isBlank(tagChoice)) {
                    tagChoice = prompter.prompt(str.toString(),
                            numberedList);
                }
            } catch (PrompterException e) {
                getLog().error(e);
            }

            String tagName = null;
            if (tagChoice != null) {
                int num = Integer.parseInt(tagChoice);
                tagName = tags[num - 1];
            }

            if (StringUtils.isBlank(tagName)) {
                throw new MojoFailureException(
                        "Tag name to create hotfix is empty.");
            }

            String tagNameWoPrefix = tagName.replace(gitFlowConfig.getVersionTagPrefix(), "");

            String defaultVersion = null;
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(tagNameWoPrefix);
                defaultVersion = versionInfo.getNextVersion().getReleaseVersionString();
            } catch (VersionParseException e) {
                getLog().error(e);
            }

            if (defaultVersion == null) {
                throw new MojoFailureException(
                        "Cannot get default project version.");
            }

            String version = null;
            try {
                version = prompter.prompt("What is the hotfix version? ["
                        + defaultVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }

            if (StringUtils.isBlank(version)) {
                version = defaultVersion;
            }

            // git for-each-ref refs/heads/hotfix/...
            final boolean hotfixBranchExists = gitCheckBranchExists(gitFlowConfig
                    .getHotfixBranchPrefix() + version);

            if (hotfixBranchExists) {
                throw new MojoFailureException(
                        "Hotfix branch with that name already exists. Cannot start hotfix.");
            }

            gitCheckoutTag(gitFlowConfig.getHotfixBranchPrefix() + version, tagName);

            gitPushTrack(gitFlowConfig.getHotfixBranchPrefix() + version);

            mvnSetVersions(version);

            // git commit -a -m updating versions for hotfix
            gitCommit(commitMessages.getHotfixStartMessage());

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
