package org.mastodon.mamut.tomancak.versioning;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import net.imglib2.util.StopWatch;

import org.apache.commons.io.FileSystemUtils;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefRefHashMap;
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
			WindowManager project = openMastodonProject( context, Paths.get( original ) );
			clearGraph( project.getAppModel().getModel().getGraph() );
			copyGraph( originalProject.getAppModel().getModel().getGraph(), project.getAppModel().getModel().getGraph(), new Saver( project ) );
			System.out.println( "done" );
		}
	}

	private static void copyGraph( ModelGraph graphA, ModelGraph graphB, Consumer< Spot > progress )
	{
		Spot refA = graphA.vertexRef();
		Spot refB = graphB.vertexRef();
		Spot refB2 = graphB.vertexRef();
		try
		{
			RefRefMap< Spot, Spot > map = new RefRefHashMap<>( graphA.vertices().getRefPool(), graphB.vertices().getRefPool() );
			double[] position = new double[ 3 ];
			double[][] cov = new double[ 3 ][ 3 ];
			for ( Spot spotA : graphA.vertices() )
			{
				int timepoint = spotA.getTimepoint();
				spotA.localize( position );
				spotA.getCovariance( cov );
				Spot spotB = graphB.addVertex( refB );
				spotB.init( timepoint, position, cov );
				spotB.setLabel( spotA.getLabel() );
				map.put( spotA, spotB );
				for ( Link sLink : spotA.incomingEdges() )
				{
					Spot sourceA = sLink.getSource( refA );
					Spot sourceB = map.get( sourceA, refB2 );
					if ( sourceB != null )
						graphB.addEdge( sourceB, spotB ).init();
				}
				for ( Link sLink : spotA.outgoingEdges() )
				{
					Spot targetA = sLink.getTarget( refA );
					Spot targetB = map.get( targetA, refB2 );
					if ( targetB != null )
						graphB.addEdge( spotB, targetB ).init();
				}
				progress.accept( spotB );
			}
		}
		finally
		{
			graphA.releaseRef( refA );
			graphB.releaseRef( refB );
			graphB.releaseRef( refB2 );
		}
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

	private static class Saver implements Consumer< Spot >
	{
		private final WindowManager project;

		int counter = 0;

		public Saver( WindowManager project )
		{
			exec( "git", "init" );
			this.project = project;
		}

		@Override
		public void accept( Spot spot )
		{
			counter++;
			if ( counter % 10_000 != 0 )
				return;

			System.out.println( counter );
			try
			{
				StopWatch saveTime = StopWatch.createAndStart();
				saveMastodonProject( Paths.get( "/home/arzt/tmp/test-git/text.mastodon" ), project );
				System.out.println( "time to save: " + saveTime );
				StopWatch gitTime = StopWatch.createAndStart();
				exec( "git", "add", "text.mastodon" );
				exec( "git", "commit", "-m", "text" + counter );
				System.out.println( "time to run git: " + gitTime );
			}
			catch ( IOException e )
			{
				throw new RuntimeException( e );
			}
		}
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
