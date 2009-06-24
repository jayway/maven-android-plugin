/*
 * Copyright (C) 2009 Jayway AB
 * Copyright (C) 2007-2008 JVending Masa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.maven.plugins.android.standalonemojos;

import com.jayway.maven.plugins.android.AbstractIntegrationtestMojo;
import com.jayway.maven.plugins.android.CommandExecutor;
import com.jayway.maven.plugins.android.ExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.util.List;
import java.util.ArrayList;

/**
 * @author hugo.josefson@jayway.com
 */
public abstract class AbstractPlatformTestMojo extends AbstractIntegrationtestMojo {
    /**
     * Package name of the apk we wish to test. If not specified, it is inferred from <code>AndroidManifest.xml</code>.
     * @optional
     * @parameter expression="${android.test.targetPackage}
     */
    private String testsPackage;
    /**
     * Class name of test runner. If not specified, it is inferred from <code>AndroidManifest.xml</code>.
     * @optional
     * @parameter expression="${android.test.testRunner}"
     */
    private String testRunner;

    protected void platformTest() throws MojoExecutionException, MojoFailureException {
        if(testsPackage == null) {
            testsPackage = extractPackageNameFromAndroidManifest(androidManifestFile);
        }

        if(testRunner == null) {
            testRunner = extractTestRunnerFromAndroidManifest(androidManifestFile);
        }

        CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
        executor.setLogger(this.getLog());

        List<String> commands = new ArrayList<String>();
        commands.add("shell");
        commands.add("am");
        commands.add("instrument");
        commands.add( "-w");
        commands.add( testsPackage + "/" + testRunner);

        getLog().info(getAndroidSdk().getPathForTool("adb") + " " + commands.toString());
        try {
            executor.executeCommand(getAndroidSdk().getPathForTool("adb"), commands, project.getBasedir(), true);
            final String standardOut   = executor.getStandardOut  ();
            final String standardError = executor.getStandardError();
            getLog().debug(standardOut);
            getLog().debug(standardError);
            // Fail when tests on device fail. adb does not exit with errorcode!=0 or even print to stderr, so we have to parse stdout.
            if (standardOut == null || !standardOut.matches(".*?OK \\([0-9]+ tests?\\)\\s*")){
                throw new MojoFailureException("Tests failed on device.");
            }
        } catch (ExecutionException e) {
            getLog().error(executor.getStandardOut());
            getLog().error(executor.getStandardError());
            throw new MojoFailureException("Tests failed on device.");
        }
    }
}
