package org.mastodon.mamut.tomancak.tree_matching;

import mpicbg.spim.data.SpimDataException;
import net.imglib2.realtransform.AffineTransform3D;
import org.jetbrains.annotations.NotNull;
import org.mastodon.collection.RefCollections;
import org.mastodon.collection.RefList;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefArrayList;
import org.mastodon.mamut.MainWindow;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.mamut.tomancak.sort_tree.FlipDescendants;
import org.scijava.Context;

import java.io.IOException;
import java.util.*;

public class MatchTreesTest
{

	public Model embryoA;

	public Model embryoB;

	public static void main(String... args) {
		new MatchTreesTest().run();
	}

	private MatchTreesTest()
	{
		Context context = new Context();
		embryoA = openAppModel( context, "/home/arzt/Datasets/Mette/E1.mastodon" ).getModel();
		embryoB = openAppModel( context, "/home/arzt/Datasets/Mette/E2.mastodon" ).getModel();
	}

	public void run()
	{
		LineageColoring.tagLineages( embryoA, embryoB );
		TreeMatchingAlgorithm.run( embryoA, embryoB );
	}

	private void rotateGraphB()
	{
		RefRefMap< Spot, Spot > pairedRoots = RootsPairing.pairRoots( embryoA.getGraph(), embryoB.getGraph() );
		AffineTransform3D transformAB = EstimateTransformation.estimateTransform( pairedRoots );
		transformGraph( transformAB.inverse(), embryoB.getGraph() );
	}

	private static void flipGraphRandomly( Model model )
	{
		RefList<Spot> dividingSpots = getDividingSpots( model.getGraph() );
		RefList<Spot> toBeFlipped = randomlySubSampleRefList( dividingSpots );
		FlipDescendants.flipDescendants( model, toBeFlipped );
	}

	private static RefList<Spot> randomlySubSampleRefList( RefList<Spot> list )
	{
		RefList<Spot> result = RefCollections.createRefList( list );
		Random random = new Random();
		for( Spot a : list )
			if( random.nextBoolean() )
				result.add(a);
		return result;
	}

	@NotNull
	private static RefList<Spot> getDividingSpots( ModelGraph graph )
	{
		RefList<Spot> divisions = new RefArrayList<>( graph.vertices().getRefPool() );
		for(Spot spot : graph.vertices() )
			if ( spot.outgoingEdges().size() == 2 )
				divisions.add( spot );
		return divisions;
	}

	private static void transformGraph( AffineTransform3D transformAB, ModelGraph graphB )
	{
		double[] position = new double[3];
		for( Spot spot : graphB.vertices() ) {
			spot.localize( position );
			transformAB.apply( position, position );
			spot.setPosition( position );
		}
	}

	private static MamutAppModel openAppModel( Context context, String projectPath )
	{
		try
		{
			MamutProject project = new MamutProjectIO().load( projectPath );
			WindowManager wm = new WindowManager( context );
			wm.getProjectManager().open( project );
			new MainWindow( wm ).setVisible( true );
			return wm.getAppModel();
		}
		catch ( SpimDataException | IOException e )
		{
			throw new RuntimeException(e);
		}
	}

}
