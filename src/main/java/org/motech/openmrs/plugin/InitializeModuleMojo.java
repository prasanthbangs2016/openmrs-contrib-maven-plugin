package org.motech.openmrs.plugin;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.codehaus.plexus.util.FileUtils;

/**
 * Initializes an OpenMRS module project based on an existing project layout and the project meta data. This mojo is
 * only provided to simplify default usage by working without configuration. All of the functionality can be achieved by
 * using the normal maven resource configuration mechanisms. Once you start configuring, forget the existence of this
 * mojo.
 * 
 * @goal initialize-module
 * @phase initialize
 */
public class InitializeModuleMojo
    extends AbstractMojo
{

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    MavenProject project;

    /**
     * The directory assumed to contain the OpenMRS module config file. It is only used to auto-configure resource
     * filtering.
     * 
     * @parameter default-value="src/main/resources"
     * @required
     * @readonly
     */
    private String configSource;

    /**
     * @parameter default-value="src/main/webapp"
     * @required
     * @readonly
     */
    private String webappSource;

    /**
     * @parameter default-value="web/module"
     * @required
     * @readonly
     */
    private String webappTarget;

    /**
     * The path in the final built module to the OpenMRS config file. Used to configure include when auto-configuring
     * config file filtering.
     * 
     * @parameter default-value="config.xml"
     * @required
     * @readonly
     */
    String configFilePath;

    /**
     * @parameter
     */
    private List hbmFiles;

    /**
     * @parameter default-value="${project.basedir}/src/main/resources"
     */
    File hbmDirectory;

    /**
     * @parameter default-value="hbmConfig"
     */
    private String hbmProperty;

    /**
     * @parameter default-value="<mapping resource=\"{0}\" />"
     */
    private String hbmFormat;

    /**
     * @parameter default-value="omodHbmConfig"
     */
    private String omodHbmProperty;

    /**
     * @parameter default-value="{0}"
     */
    private String omodHbmFormat;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        getLog().debug( "initializing OpenMRS module" );

        getLog().debug( "ensuring config is filtered" );
        File configDir = new File( project.getBasedir(), configSource );
        if ( configDir.exists() )
        {
            List defaultReses = getResourcesByPath( configSource );
            Iterator defResIter = defaultReses.iterator();
            boolean configPotentiallyFiltered = false;
            while ( defResIter.hasNext() )
            {
                Resource defRes = (Resource) defResIter.next();
                if ( defRes.isFiltering() )
                {
                    configPotentiallyFiltered = true;
                    break;
                }
            }

            if ( defaultReses.isEmpty() || configPotentiallyFiltered )
            {
                getLog().debug( "config dir manually configured, skipping" );
            }
            else
            {
                getLog().debug( "adding resource to filter config file" );
                Resource configResource = new Resource();
                configResource.setDirectory( configSource );
                configResource.setFiltering( true );
                configResource.addInclude( FileUtils.removePath( configFilePath ) );
                project.addResource( configResource );
            }
        }

        getLog().debug( "auto-detecting web sources" );
        File webappDir = new File( project.getBasedir(), webappSource );
        if ( webappDir.exists() )
        {
            if ( getResourceByPath( webappSource ) == null )
            {
                getLog().debug( "adding resource: " + webappSource );
                Resource webappResource = new Resource();
                webappResource.setDirectory( webappSource );
                webappResource.setFiltering( false );
                webappResource.setTargetPath( webappTarget );
                project.addResource( webappResource );
            }
            else
            {
                getLog().debug( "web resources already configured, skipping" );
            }
        }

        getLog().debug( "building hbm config properties" );

        FileSetManager fileSetManager = new FileSetManager( getLog(), false );

        Set hbmFilenames = new HashSet();

        if ( hbmFiles == null )
        {
            getLog().debug( "configuring hbm filset to default" );
            hbmFiles = new ArrayList();
            FileSet hbmFileSet = new FileSet();
            hbmFileSet.setDirectory( hbmDirectory.getPath() );
            hbmFileSet.addInclude( "**/*.hbm.xml" );
            hbmFiles.add( hbmFileSet );
        }

        getLog().debug( "populating hbm files using filesets" );

        while ( !hbmFiles.isEmpty() )
        {
            FileSet hbmFileSet = (FileSet) hbmFiles.remove( 0 );
            String[] files = fileSetManager.getIncludedFiles( hbmFileSet );
            hbmFilenames.addAll( Arrays.asList( files ) );
        }

        Iterator fileIter = hbmFilenames.iterator();

        getLog().debug( "constructing hbm properties" );
        StringBuffer hbmValue = new StringBuffer();
        StringBuffer omodHbmValue = new StringBuffer();
        while ( fileIter.hasNext() )
        {
            String filename = (String) fileIter.next();
            Object[] msgParams = new Object[] { filename };
            hbmValue.append( MessageFormat.format( hbmFormat, msgParams ) ).append( '\n' );
            omodHbmValue.append( MessageFormat.format( omodHbmFormat, msgParams ) ).append( '\n' );
        }

        getLog().debug( "setting project hbm properies" );
        project.getProperties().setProperty( hbmProperty, hbmValue.toString() );
        project.getProperties().setProperty( omodHbmProperty, omodHbmValue.toString() );

        hbmValue = omodHbmValue = null;
    }

    /**
     * Returns the first resource with the directory exactly matching the specified path.
     * 
     * @param path
     * @return first matching resource, or null
     * @throws IOException
     */
    private Resource getResourceByPath( String path )
        throws MojoExecutionException
    {
        Resource result = null;
        try
        {
            Iterator resIter = project.getResources().iterator();
            while ( resIter.hasNext() )
            {
                Resource res = (Resource) resIter.next();
                String canon1 = new File( path ).getCanonicalPath();
                String canon2 = new File( res.getDirectory() ).getCanonicalPath();
                getLog().debug( "matching " + canon1 + " ~ " + canon2 );
                if ( canon1.equals( canon2 ) )
                {
                    result = res;
                    break;
                }
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "failed to construct paths" );
        }
        return result;
    }

    /**
     * Returns the entire list of resources with the directory exactly matching the specified path.
     * 
     * @param path
     * @return list of matching resources, or null
     * @throws IOException
     */
    private List getResourcesByPath( String path )
        throws MojoExecutionException
    {
        List result = new ArrayList();
        try
        {
            Iterator resIter = project.getResources().iterator();
            while ( resIter.hasNext() )
            {
                Resource res = (Resource) resIter.next();
                String canon1 = new File( path ).getCanonicalPath();
                String canon2 = new File( res.getDirectory() ).getCanonicalPath();
                getLog().debug( "matching " + canon1 + " ~ " + canon2 );
                if ( canon1.equals( canon2 ) )
                {
                    result.add( res );
                }
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "failed to construct paths" );
        }
        return result;
    }
}
