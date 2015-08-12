/*
 * Copyright (C) 2014 simpligility technologies inc.
 * Copyright (C) 2015 Piotr Soróbka
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
package com.simpligility.maven.plugins.android.sample;

import com.simpligility.maven.plugins.android.PluginInfo;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import java.io.File;
import org.junit.Rule;
import org.junit.runner.RunWith;

/**
 *
 * @author Piotr Soróbka <psorobka@gmail.com>
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.2.3"})
public abstract class AbstractSampleIT {

    @Rule
    public final TestResources resources = new TestResources();
    public final MavenRuntime mavenRuntime;

    public AbstractSampleIT(MavenRuntime.MavenRuntimeBuilder builder) throws Exception {
        this.mavenRuntime = builder.build();
    }

    protected void buildDeployAndRun(String path, String packageName, String activityName) throws Exception {
        File basedir = resources.getBasedir(path);
        MavenExecutionResult result = mavenRuntime
                .forProject(basedir)
                .execute("clean", PluginInfo.getQualifiedGoal("undeploy"),
                        "install", PluginInfo.getQualifiedGoal("deploy"),
                        PluginInfo.getQualifiedGoal("run"));

        result.assertErrorFreeLog();
        result.assertLogText("Successfully installed");
        result.assertLogText("Attempting to start " + packageName
                + "/" + activityName);
    }
}
