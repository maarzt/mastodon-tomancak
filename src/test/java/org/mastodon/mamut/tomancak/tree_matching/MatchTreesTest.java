package org.mastodon.mamut.tomancak.tree_matching;

import mpicbg.spim.data.SpimDataException;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mastodon.collection.ObjectRefMap;
import org.mastodon.collection.RefCollections;
import org.mastodon.collection.RefList;
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
	public static void main(String... args) {
		new MatchTreesTest().test();
	}

	@Test
	public void test()
	{
		try(Context context = new Context())
		{
			GraphPair m = initializeTreeMatching( context );
			m.transformAB = estimateTransform( m );
			LineageColoring.tagLineages( m );
			rotateGraphB( m );
			flipGraphRandomly( m.embryoB.getModel() );
			matchGraphs( m );
		}
	}

	private static void rotateGraphB( GraphPair m )
	{
		transformGraph( m.transformAB.inverse(), m.graphB );
		m.transformAB = new AffineTransform3D();
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

	private static void matchGraphs( GraphPair m )
	{
		AffineTransform3D noOffsetTransform = noOffsetTransform( m.transformAB );
		RefList<Spot> toBeFlipped = new RefArrayList<>( m.graphB.vertices().getRefPool());
		for(String label : m.commonRootLabels )
			TreeMatchingAlgorithm.matchTree( noOffsetTransform, m.graphA, m.graphB, m.rootsA.get( label ), m.rootsB.get( label ), toBeFlipped );
		FlipDescendants.flipDescendants( m.embryoB.getModel(), toBeFlipped );
	}

	private static AffineTransform3D noOffsetTransform( AffineTransform3D transformAB )
	{
		AffineTransform3D noOffsetTransform = new AffineTransform3D();
		noOffsetTransform.set( transformAB );
		noOffsetTransform.setTranslation( 0, 0, 0 );
		return noOffsetTransform;
	}

	private static AffineTransform3D estimateTransform( GraphPair m )
	{
		List<RealPoint> pointsA = new ArrayList<>();
		List<RealPoint> pointsB = new ArrayList<>();
		for(String label : m.commonRootLabels ) {
			pointsA.add(new RealPoint( m.rootsA.get( label ) ));
			pointsB.add(new RealPoint( m.rootsB.get( label ) ));
		}
		return EstimateTransformation.estimateScaleRotationTranslation( pointsA, pointsB );
	}

	@NotNull
	private static GraphPair initializeTreeMatching( Context context )
	{
		GraphPair m = new GraphPair();
		m.embryoA = openAppModel( context, "/home/arzt/Datasets/Mette/E1.mastodon" );
		m.embryoB = openAppModel( context, "/home/arzt/Datasets/Mette/E2.mastodon" );
		m.graphA = m.embryoA.getModel().getGraph();
		m.graphB = m.embryoB.getModel().getGraph();
		m.rootsA = LineageTreeUtils.getRootsMap( m.graphA );
		m.rootsB = LineageTreeUtils.getRootsMap( m.graphB );
		m.commonRootLabels = commonRootLabels( m.rootsA, m.rootsB );
		return m;
	}

	private static Set<String> commonRootLabels( ObjectRefMap<String, Spot> rootsA, ObjectRefMap<String, Spot> rootsB )
	{
		List<String> blackList = Arrays.asList( "ventral", "dorsal", "left", "right", "vegetal_posterior", "apical_anterior" );
		Set<String> intersection = new HashSet<>( rootsA.keySet());
		intersection.retainAll( rootsB.keySet() );
		intersection.removeAll( blackList );
		return intersection;
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
