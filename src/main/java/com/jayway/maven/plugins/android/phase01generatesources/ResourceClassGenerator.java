package com.jayway.maven.plugins.android.phase01generatesources;

import com.android.builder.VariantConfiguration;
import com.android.builder.internal.SymbolLoader;
import com.android.builder.internal.SymbolWriter;
import com.android.utils.ILogger;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Generates R classes containing appropriate resource value for dependent libraries.
 *<p>
 * Appropriate means that the values are static for AAR dependencies and have been modified for APKLIB dependencies.
 *</p>
 *
 * Created by William on 16/12/13.
 */
final class ResourceClassGenerator
{
    private final GenerateSourcesMojo mojo;
    private final List<Artifact> libraries;
    private final File targetDirectory;
    private final File genDirectory;
    private final Log log;
    private final ILogger androidUtilsLog;

    public ResourceClassGenerator( GenerateSourcesMojo mojo, List<Artifact> libraries, File targetDirectory,
                                   File genDirectory, Log log )
    {
        this.mojo = mojo;
        this.libraries = libraries;
        this.targetDirectory = targetDirectory;
        this.genDirectory = genDirectory;
        this.log = log;
        this.androidUtilsLog = new MavenILogger( log );
    }

    public void generateLibraryRs() throws MojoExecutionException
    {
        // list of all the symbol loaders per package names.
        final Multimap<String, SymbolLoader> libMap = ArrayListMultimap.create();

        for ( final Artifact lib : libraries )
        {
            final File unpackedLibDirectory = new File( mojo.getLibraryUnpackDirectory( lib ) );
            final File rFile = new File( unpackedLibDirectory, "R.txt" );

            if ( rFile.isFile() )
            {
                final File libManifestFile = new File( unpackedLibDirectory, "AndroidManifest.xml" );
                final String packageName = VariantConfiguration.getManifestPackage( libManifestFile );
                log.debug( "Reading R for " + packageName  + " at " + rFile );

                // store these symbols by associating them with the package name.
                final SymbolLoader libSymbols = loadSymbols( rFile );
                libMap.put( packageName, libSymbols );
            }
            else
            {
                log.warn( "No R.txt found for library dependency : " + unpackedLibDirectory );
                // TODO Attempt to generate one using #generateRForApkLibDependency (commented out below)
            }
        }

        if ( libMap.isEmpty() )
        {
            return;
        }

        // load the full resources values from the R.txt calculated for the project.
        final File projectR = new File( targetDirectory, "R.txt" );
        final SymbolLoader fullSymbolValues = loadSymbols( projectR );

        // now loop on all the package name, merge all the symbols to write, and write them
        for ( final String packageName : libMap.keySet() )
        {
            log.debug( "Writing R for " + packageName );
            final Collection<SymbolLoader> symbols = libMap.get( packageName );

            final SymbolWriter writer = new SymbolWriter(
                    genDirectory.getAbsolutePath(), packageName, fullSymbolValues );
            for ( SymbolLoader symbolLoader : symbols )
            {
                writer.addSymbolsToWrite( symbolLoader );
            }
            writeSymbols( writer, packageName );
        }
    }

    private SymbolLoader loadSymbols( File file ) throws MojoExecutionException
    {
        final SymbolLoader libSymbols = new SymbolLoader( file, androidUtilsLog );
        try
        {
            libSymbols.load();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not load " + file, e );
        }
        return libSymbols;
    }

    private void writeSymbols( SymbolWriter writer, String packageName ) throws MojoExecutionException
    {
        try
        {
            writer.write();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not write R for " + packageName, e );
        }
    }

    /**
     * Executes aapt to generate the R class for the given apklib.
     *
     * @param apklibArtifact    apklib for which to generate the R class.
     * @throws MojoExecutionException if it fails.
     */
/*
    private void generateRForApkLibDependency( Artifact apklibArtifact ) throws MojoExecutionException
    {
        final String unpackDir = getLibraryUnpackDirectory( apklibArtifact );
        log.debug( "Generating R file for apklibrary: " + apklibArtifact.getGroupId()
                + ":" + apklibArtifact.getArtifactId() );

        CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
        executor.setLogger( log );

        List<String> commands = new ArrayList<String>();
        commands.add( "package" );
        commands.add( "--non-constant-id" );
        commands.add( "-m" );
        commands.add( "-J" );
        commands.add( genDirectory.getAbsolutePath() );
        commands.add( "--custom-package" );
        commands.add( extractPackageNameFromAndroidManifest( new File( unpackDir + "/" + "AndroidManifest.xml" ) ) );
        commands.add( "-M" );
        commands.add( androidManifestFile.getAbsolutePath() );
        if ( resourceDirectory.exists() )
        {
            commands.add( "-S" );
            commands.add( resourceDirectory.getAbsolutePath() );
        }
        for ( Artifact artifact : getAllRelevantDependencyArtifacts() )
        {
            if ( artifact.getType().equals( APKLIB ) || artifact.getType().equals( AAR ) )
            {
                final String apkLibResDir = getLibraryUnpackDirectory( artifact ) + "/res";
                if ( new File( apkLibResDir ).exists() )
                {
                    commands.add( "-S" );
                    commands.add( apkLibResDir );
                }
            }
        }
        commands.add( "--auto-add-overlay" );
        if ( assetsDirectory.exists() )
        {
            commands.add( "-A" );
            commands.add( assetsDirectory.getAbsolutePath() );
        }
        for ( Artifact artifact : getAllRelevantDependencyArtifacts() )
        {
            if ( artifact.getType().equals( APKLIB ) )
            {
                final String apkLibAssetsDir = getLibraryUnpackDirectory( artifact ) + "/assets";
                if ( new File( apkLibAssetsDir ).exists() )
                {
                    commands.add( "-A" );
                    commands.add( apkLibAssetsDir );
                }
            }
        }
        commands.add( "-I" );
        commands.add( getAndroidSdk().getAndroidJar().getAbsolutePath() );
        if ( StringUtils.isNotBlank( configurations ) )
        {
            commands.add( "-c" );
            commands.add( configurations );
        }

        for ( String aaptExtraArg : aaptExtraArgs )
        {
            commands.add( aaptExtraArg );
        }

        getLog().info( getAndroidSdk().getAaptPath() + " " + commands.toString() );
        try
        {
            executor.executeCommand( getAndroidSdk().getAaptPath(), commands, project.getBasedir(), false );
        }
        catch ( ExecutionException e )
        {
            throw new MojoExecutionException( "", e );
        }
    }
*/
}
