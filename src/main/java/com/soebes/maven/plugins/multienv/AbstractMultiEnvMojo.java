package com.soebes.maven.plugins.multienv;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

public abstract class AbstractMultiEnvMojo
    extends AbstractMojo
{

    /**
     * The project currently being build.
     */
    @Parameter( defaultValue = "${project}", required = true, readonly = true )
    private MavenProject mavenProject;

    /**
     * The current Maven session.
     */
    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    private MavenSession mavenSession;

    /**
     * The directory for the generated configuration packages.
     */
    @Parameter( defaultValue = "${project.build.directory}", required = true, readonly = true )
    private File outputDirectory;

    /**
     * folder which contains the different environments
     */
    // TODO: src/main ? property?
    @Parameter( defaultValue = "${basedir}/src/main/environments" )
    private File sourceDirectory;

    /**
     * The character encoding scheme to be applied when filtering resources.
     */
    @Parameter( defaultValue = "${project.build.sourceEncoding}" )
    private String encoding;

    /**
     * Name of the generated JAR.
     */
    @Parameter( defaultValue = "${project.build.finalName}", readonly = true )
    private String finalName;

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The archive configuration to use. See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
     * Archiver Reference</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    /**
     * Expression preceded with the String won't be interpolated \${foo} will be replaced with ${foo}
     */
    @Parameter
    private String escapeString;

    /**
     * Whether to escape backslashes and colons in windows-style paths.
     */
    @Parameter( defaultValue = "true" )
    private boolean escapeWindowsPaths;

    /**
     * The list of extra filter properties files to be used along with System properties, project properties, and filter
     * properties files specified in the POM build/filters section, which should be used for the filtering during the
     * current mojo execution. <br/>
     * Normally, these will be configured from a plugin's execution section, to provide a different set of filters for a
     * particular execution. For instance, starting in Maven 2.2.0, you have the option of configuring executions with
     * the id's <code>default-resources</code> and <code>default-testResources</code> to supply different configurations
     * for the two different types of resources. By supplying <code>extraFilters</code> configurations, you can separate
     * which filters are used for which type of resource.
     */
    @Parameter
    private List<String> filters;

    /**
     * Support filtering of filenames folders etc.
     */
    @Parameter( defaultValue = "false" )
    private boolean fileNameFiltering;

    /**
     * <p>
     * Set of delimiters for expressions to filter within the resources. These delimiters are specified in the form
     * 'beginToken*endToken'. If no '*' is given, the delimiter is assumed to be the same for start and end.
     * </p>
     * <p>
     * So, the default filtering delimiters might be specified as:
     * </p>
     * 
     * <pre>
     * &lt;delimiters&gt;
     *   &lt;delimiter&gt;${*}&lt;/delimiter&gt;
     *   &lt;delimiter&gt;@&lt;/delimiter&gt;
     * &lt;/delimiters&gt;
     * </pre>
     * <p>
     * Since the '@' delimiter is the same on both ends, we don't need to specify '@*@' (though we can).
     * </p>
     */
    @Parameter
    private LinkedHashSet<String> delimiters;

    /**
     * Use default delimiters in addition to custom delimiters, if any.
     */
    @Parameter( defaultValue = "true" )
    private boolean useDefaultDelimiters;

    /**
     * Additional file extensions to not apply filtering (already defined are : jpg, jpeg, gif, bmp, png)
     */
    @Parameter
    private List<String> nonFilteredFileExtensions;

    /**
     * stop searching endToken at the end of line
     */
    @Parameter( defaultValue = "false" )
    private boolean supportMultiLineFiltering;

    @Component( role = MavenResourcesFiltering.class, hint = "default" )
    protected MavenResourcesFiltering mavenResourcesFiltering;

    public MavenArchiveConfiguration getArchive()
    {
        return archive;
    }

    public MavenProject getMavenProject()
    {
        return mavenProject;
    }

    public MavenSession getMavenSession()
    {
        return mavenSession;
    }

    public File getOutputDirectory()
    {
        return outputDirectory;
    }

    public MavenProjectHelper getProjectHelper()
    {
        return projectHelper;
    }

    public File getSourceDirectory()
    {
        return sourceDirectory;
    }

    public String getFinalName()
    {
        return finalName;
    }

    public String getEncoding()
    {
        return encoding;
    }

    /**
     * @param resourceResult The folder where to search for different environments.
     * @return The list of identified environments.
     */
    protected String[] getTheEnvironments( File resourceResult )
    {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir( resourceResult );
        // It is necessary to exclude the {@code ""} cause
        // otherwise we would get back this as well.
        // Bug?
        ds.setExcludes( new String[] { "" } );
        ds.addDefaultExcludes();
        ds.scan();

        return ds.getIncludedDirectories();
    }

    /**
     * Returns the archive file to generate, based on an optional classifier.
     *
     * @param basedir the output directory
     * @param finalName the name of the ear file
     * @param classifier an optional classifier
     * @return the file to generate
     */
    protected File getArchiveFile( File basedir, String finalName, String classifier, String archiveExt )
    {
        if ( basedir == null )
        {
            throw new IllegalArgumentException( "basedir is not allowed to be null" );
        }
        if ( finalName == null )
        {
            throw new IllegalArgumentException( "finalName is not allowed to be null" );
        }
        if ( archiveExt == null )
        {
            throw new IllegalArgumentException( "archiveExt is not allowed to be null" );
        }

        if ( finalName.isEmpty() )
        {
            throw new IllegalArgumentException( "finalName is not allowed to be empty." );
        }
        if ( archiveExt.isEmpty() )
        {
            throw new IllegalArgumentException( "archiveExt is not allowed to be empty." );
        }

        StringBuilder fileName = new StringBuilder( finalName );

        if ( hasClassifier( classifier ) )
        {
            fileName.append( "-" ).append( classifier );
        }

        fileName.append( '.' );
        fileName.append( archiveExt );

        return new File( basedir, fileName.toString() );
    }

    public String getEscapeString()
    {
        return escapeString;
    }

    public boolean isEscapeWindowsPaths()
    {
        return escapeWindowsPaths;
    }

    public List<String> getFilters()
    {
        return filters;
    }

    public boolean isFileNameFiltering()
    {
        return fileNameFiltering;
    }

    public LinkedHashSet<String> getDelimiters()
    {
        return delimiters;
    }

    public boolean isUseDefaultDelimiters()
    {
        return useDefaultDelimiters;
    }

    public List<String> getNonFilteredFileExtensions()
    {
        return nonFilteredFileExtensions;
    }

    public boolean isSupportMultiLineFiltering()
    {
        return supportMultiLineFiltering;
    }

    private boolean hasClassifier( String classifier )
    {
        boolean result = false;
        if ( classifier != null && classifier.trim().length() > 0 )
        {
            result = true;
        }

        return result;
    }

    protected void deleteFolderOfPreviousRunIfExist( File folderOfPreviousRun )
        throws MojoExecutionException
    {

        if ( folderOfPreviousRun.exists() )
        {
            try
            {
                FileUtils.deleteDirectory( folderOfPreviousRun );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Failure while deleting " + folderOfPreviousRun.getAbsolutePath(),
                                                  e );
            }
        }
    }

    /**
     * Create the unpack folder for later unpacking of the main artifact.
     * 
     * @return The folder which has been created.
     * @throws MojoExecutionException in case of failures.
     */
    protected File createUnpackFolder()
        throws MojoFailureException, MojoExecutionException
    {
        // TODO: Should we use a different name or temp file? File.createTempFile( prefix, suffix );
        File unpackFolder = new File( getOutputDirectory(), "configuration-maven-plugin-unpack" );

        deleteFolderOfPreviousRunIfExist( unpackFolder );

        if ( !unpackFolder.mkdirs() )
        {
            throw new MojoExecutionException( "The unpack folder " + unpackFolder.getAbsolutePath()
                + " couldn't generated!" );
        }
        return unpackFolder;
    }

    protected String getArchiveExtensionOfTheProjectMainArtifact()
        throws MojoExecutionException
    {
        if ( getMavenProject().getArtifact() == null )
        {
            throw new MojoExecutionException( "No main artifact has been set yet." );
        }

        if ( getMavenProject().getArtifact().getFile() == null )
        {
            throw new MojoExecutionException( "No main artifact file has been set yet." );
        }

        return FileUtils.getExtension( getMavenProject().getArtifact().getFile().getAbsolutePath() ).toLowerCase();

    }

    protected File createPluginResourceOutput()
        throws MojoExecutionException
    {
        // TODO: Should we use a different name? Or temp File?
        File resourceResult = new File( getOutputDirectory(), "configuration-maven-plugin-resource-output" );

        deleteFolderOfPreviousRunIfExist( resourceResult );

        if ( !resourceResult.mkdirs() )
        {
            throw new MojoExecutionException( "Failure while trying to create " + resourceResult.getAbsolutePath() );
        }

        return resourceResult;
    }

    protected void filterResources( File outputDirectory )
        throws MojoExecutionException
    {

        Resource res = new Resource();
        // TODO: Check how to prevent hard coding here?
        res.setDirectory( getSourceDirectory().getAbsolutePath() );
        res.setFiltering( true );
        // TODO: Check if it makes sense to make this list configurable?
        res.setIncludes( Collections.singletonList( "**/*" ) );

        List<String> filtersFile = new ArrayList<String>();
        MavenResourcesExecution execution =
            new MavenResourcesExecution( Collections.singletonList( res ), outputDirectory, getMavenProject(),
                                         getEncoding(), filtersFile, getNonFilteredFileExtensions(),
                                         getMavenSession() );

        execution.setEscapeString( getEscapeString() );
        execution.setSupportMultiLineFiltering( isSupportMultiLineFiltering() );
        // TODO: Check if we need a parameter?
        execution.setIncludeEmptyDirs( true );
        execution.setEscapeWindowsPaths( isEscapeWindowsPaths() );
        execution.setFilterFilenames( isFileNameFiltering() );
        //// execution.setFilters( filters );
        //
        // // TODO: Check if we need a parameter?
        execution.setOverwrite( true );
        execution.setDelimiters( getDelimiters(), isUseDefaultDelimiters() );
        execution.setEncoding( getEncoding() );
        //
        // execution.setUseDefaultFilterWrappers( true );

        if ( getNonFilteredFileExtensions() != null )
        {
            execution.setNonFilteredFileExtensions( getNonFilteredFileExtensions() );
        }

        try
        {
            mavenResourcesFiltering.filterResources( execution );
        }
        catch ( MavenFilteringException e )
        {
            getLog().error( "Failure during filtering.", e );
            throw new MojoExecutionException( "Failure during filtering", e );
        }

    }

    protected void createLoggingOutput( String[] identifiedEnvironments )
    {
        getLog().info( "" );
        getLog().info( "We have found " + identifiedEnvironments.length + " environments." );

        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < identifiedEnvironments.length; i++ )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ',' );
            }
            sb.append( identifiedEnvironments[i] );
        }

        getLog().info( "We have the following environments: " + sb.toString() );
        getLog().info( "" );
    }

}
