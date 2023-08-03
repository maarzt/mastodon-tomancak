package org.mastodon.mamut.tomancak.versioning;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import net.imglib2.util.StopWatch;

import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.mamut.tomancak.collaborative.GitFormatSerializer;
import org.scijava.Context;

import mpicbg.spim.data.SpimDataException;

public class TestGit
{

	private static final String original = "/home/arzt/Datasets/Mette/E2.mastodon";

	private static final String empty = "/home/arzt/Datasets/Mette/empty.mastodon";

	public static void main( String... args ) throws SpimDataException, IOException
	{
		run();
	}

	private static void run() throws IOException, SpimDataException
	{
		try (Context context = new Context())
		{
			WindowManager originalProject = openMastodonProject( context, Paths.get( original ) );
			removeWrongEdges( originalProject.getAppModel().getModel().getGraph() );
			WindowManager newProject = openMastodonProject( context, Paths.get( empty ) );
			ModelGraph newGraph = newProject.getAppModel().getModel().getGraph();
			ModelGraph originalGraph = originalProject.getAppModel().getModel().getGraph();
			exec( "git", "init" );
			try (GraphCopier copier = new GraphCopier( originalGraph, newGraph ))
			{
				while ( copier.hasNextSpot() )
				{
					for ( int i = 0; i < 10_000 && copier.hasNextSpot(); i++ )
						copier.copyNextSpot();
					saveAndCommit( newProject );
				}
			}
			System.out.println( "done" );
		}
	}

	private static void saveAndCommit( WindowManager newProject ) throws IOException
	{
		StopWatch saveTime = StopWatch.createAndStart();
		saveMastodonProject( Paths.get( "/home/arzt/tmp/test-git/text.mastodon" ), newProject );
		System.out.println( "time to save: " + saveTime );
		StopWatch gitTime = StopWatch.createAndStart();
		exec( "git", "add", "text.mastodon" );
		exec( "git", "commit", "-m", "text" + newProject.getAppModel().getModel().getGraph().vertices().size() );
		System.out.println( "time to run git: " + gitTime );
	}

	private static void removeWrongEdges( ModelGraph graph )
	{
		Spot ref1 = graph.vertexRef();
		Spot ref2 = graph.vertexRef();
		try
		{
			for ( Link link : graph.edges() )
			{
				Spot source = link.getSource( ref1 );
				Spot target = link.getTarget( ref2 );
				if ( source.getTimepoint() >= target.getTimepoint() )
					graph.remove( link );
			}
		}
		finally
		{
			graph.releaseRef( ref1 );
			graph.releaseRef( ref2 );
		}
	}

	private static void clearGraph( ModelGraph graph )
	{
		graph.vertices().forEach( graph::remove );
	}

	private static void saveMastodonProject( Path path, WindowManager windowManager ) throws IOException
	{
		Model model = windowManager.getAppModel().getModel();
		deleteNonEmptyDirectory( path );
		Files.createDirectory( path );
		GitFormatSerializer.serialize( model.getGraph(), path.toFile() );
		//windowManager.getProjectManager().saveProject( save.toFile() );
	}

	private static void deleteNonEmptyDirectory( Path path ) throws IOException
	{
		if ( !Files.exists( path ) )
			return;
		try (Stream< Path > walk = Files.walk( path ))
		{
			walk.sorted( Comparator.reverseOrder() ).map( Path::toFile ).forEach( File::delete );
		}
	}

	private static WindowManager openMastodonProject( Context context, Path open ) throws IOException, SpimDataException
	{
		WindowManager windowManager = new WindowManager( context );
		MamutProject project = new MamutProjectIO().load( open.toFile().getAbsolutePath() );
		windowManager.getProjectManager().open( project, false, true );
		return windowManager;
	}

	private static void exec( String... command )
	{
		try
		{
			ProcessBuilder pb = new ProcessBuilder( command );
			pb.directory( new File( "/home/arzt/tmp/test-git" ) );
			pb.redirectOutput( ProcessBuilder.Redirect.INHERIT );
			pb.redirectError( ProcessBuilder.Redirect.INHERIT );
			pb.start().waitFor();
		}
		catch ( InterruptedException | IOException e )
		{
			throw new RuntimeException( e );
		}
	}
}
