package com.amashchenko.maven.plugin.gitflow;

import java.text.MessageFormat;

import static java.text.MessageFormat.*;

/**
 * Created by krs on 1/8/17.
 */
public enum Marker {
    PUSH_TO_REMOTE("Push '{0}' to remote: git push; {1}"),
    DEPLOY("Deploy '{0}': mvn deploy; {1}"),
    DEL_BRANCH("Delete '{0}' locally + remotely: git branch -d {0} then git push {1} --delete {0}; {2}"),
    DEL_BRANCH_FORCE("Delete '{0}' locally + remotely: git branch -D {0} then git push {1} --delete {0}; {2}"),
    MERGE_SQUASH("Squash merge: git merge --squash {0}; {1}"),
    MERGE_FFWD("FFWD Merge: git merge {0}; {1}"),
    MERGE_REBASE("Rebase: git rebase {0}; {1}"),
    MERGE_NO_FFWD("Merge No FFWD: git merge --no-ff {0}; {1}"),
    DEL_TAG("Delete '{0}' locally + remotely: git tag -d {0} then git push {1} :refs/tags/{0}; {2}")
    ;

    private final String prefix;

    Marker(String prefix){
        this.prefix = prefix;
    }

    public String str(String... addIns){
        return format(prefix, addIns);
    }
}
