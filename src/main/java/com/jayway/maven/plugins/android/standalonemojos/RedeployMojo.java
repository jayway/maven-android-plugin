/*
 * Copyright (C) 2009 Jayway AB
 * Copyright (C) 2007-2008 JVending Masa
 * Copyright (C) 2010 akwuinet A.G.
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

import static com.jayway.maven.plugins.android.common.AndroidExtension.APK;

import com.jayway.maven.plugins.android.AbstractAndroidMojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Redeploys the built apk of the current project to a connected device.
 * This simply tries to undeploy the APK and re-deploy it.
 *
 * @author clement.escoffier@akquinet.de
 * @author Manfred Moser <manfred@simpligility.com>
 * 
 * @goal redeploy
 * @requiresProject true
 * @requiresDependencyResolution runtime
 */
public class RedeployMojo extends AbstractAndroidMojo
{

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if ( project.getPackaging().equals( APK ) )
        {
            String packageToUndeploy = extractPackageNameFromAndroidManifest( androidManifestFile );
            undeployApk( packageToUndeploy );
            deployBuiltApk();
        } 
        else 
        {
            getLog().info( "Project packaging is not apk, skipping redeployment" );
        }
    }

}
