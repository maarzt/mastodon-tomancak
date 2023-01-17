package org.mastodon.mamut.tomancak.tree_matching;

import mpicbg.spim.data.SpimDataException;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mastodon.collection.ObjectRefMap;
import org.mastodon.collection.RefCollections;
import org.mastodon.collection.RefList;
import org.mastodon.collection.ref.RefArrayList;
import org.mastodon.graph.algorithm.traversal.DepthFirstSearch;
import org.mastodon.graph.algorithm.traversal.GraphSearch;
import org.mastodon.graph.algorithm.traversal.SearchListener;
import org.mastodon.mamut.MainWindow;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.mamut.tomancak.sort_tree.FlipDescendants;
import org.mastodon.mamut.tomancak.sort_tree.SortTreeUtils;
import org.mastodon.model.tag.ObjTagMap;
import org.mastodon.model.tag.TagSetModel;
import org.mastodon.model.tag.TagSetStructure;
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
			tagLineages( m );
			rotateGraphB( m );
			flipGraphRandomly( m.embryoB.getModel() );
			matchGraphs( m );
		}
	}

	private void rotateGraphB( GraphPair m )
	{
		transformGraph( m.transformAB.inverse(), m.graphB );
		m.transformAB = new AffineTransform3D();
	}

	private void flipGraphRandomly( Model model )
	{
		RefList<Spot> dividingSpots = getDividingSpots( model.getGraph() );
		RefList<Spot> toBeFlipped = randomlySubSampleRefList( dividingSpots );
		FlipDescendants.flipDescendants( model, toBeFlipped );
	}

	private RefList<Spot> randomlySubSampleRefList( RefList<Spot> list )
	{
		RefList<Spot> result = RefCollections.createRefList( list );
		Random random = new Random();
		for( Spot a : list )
			if( random.nextBoolean() )
				result.add(a);
		return result;
	}

	@NotNull
	private RefList<Spot> getDividingSpots( ModelGraph graph )
	{
		RefList<Spot> divisions = new RefArrayList<>( graph.vertices().getRefPool() );
		for(Spot spot : graph.vertices() )
			if ( spot.outgoingEdges().size() == 2 )
				divisions.add( spot );
		return divisions;
	}

	private void transformGraph( AffineTransform3D transformAB, ModelGraph graphB )
	{
		double[] position = new double[3];
		for( Spot spot : graphB.vertices() ) {
			spot.localize( position );
			transformAB.apply( position, position );
			spot.setPosition( position );
		}
	}

	private void matchGraphs( GraphPair m )
	{
		AffineTransform3D noOffsetTransform = noOffsetTransform( m.transformAB );
		RefList<Spot> toBeFlipped = new RefArrayList<>( m.graphB.vertices().getRefPool());
		for(String label : m.commonRootLabels )
			matchTree( noOffsetTransform, m.graphA, m.graphB, m.rootsA.get( label ), m.rootsB.get( label ), toBeFlipped );
		FlipDescendants.flipDescendants( m.embryoB.getModel(), toBeFlipped );
	}

	private AffineTransform3D noOffsetTransform( AffineTransform3D transformAB )
	{
		AffineTransform3D noOffsetTransform = new AffineTransform3D();
		noOffsetTransform.set( transformAB );
		noOffsetTransform.setTranslation( 0, 0, 0 );
		return noOffsetTransform;
	}

	private void tagLineages( GraphPair m )
	{
		Map<String, Integer> colorMap = createColorMap( m.commonRootLabels );
		tagLineages( colorMap, m.rootsA, m.embryoA.getModel() );
		tagLineages( colorMap, m.rootsB, m.embryoB.getModel() );
	}

	private void tagLineages( Map<String, Integer> colorMap, ObjectRefMap<String, Spot> roots, Model model )
	{
		TagSetStructure.TagSet tagSet = createTagSet( model, colorMap );
		for(TagSetStructure.Tag tag : tagSet.getTags()) {
			tagLineage( model, roots.get(tag.label()), tagSet, tag );
		}
	}

	private void tagLineage( Model model, Spot root, TagSetStructure.TagSet tagSet, TagSetStructure.Tag tag )
	{
		ObjTagMap<Spot, TagSetStructure.Tag> spotTags = model.getTagSetModel().getVertexTags().tags( tagSet );
		ObjTagMap<Link, TagSetStructure.Tag> edgeTags = model.getTagSetModel().getEdgeTags().tags( tagSet );

		SearchListener<Spot, Link, DepthFirstSearch<Spot, Link>> searchListener = new SearchListener<Spot, Link, DepthFirstSearch<Spot, Link>>()
		{
			@Override
			public void processVertexLate( Spot spot, DepthFirstSearch<Spot, Link> search )
			{
				// do nothing
			}

			@Override
			public void processVertexEarly( Spot spot, DepthFirstSearch<Spot, Link> spotLinkDepthFirstSearch )
			{
				spotTags.set( spot, tag );
			}

			@Override
			public void processEdge( Link link, Spot spot, Spot v1, DepthFirstSearch<Spot, Link> spotLinkDepthFirstSearch )
			{
				edgeTags.set( link, tag );
			}

			@Override
			public void crossComponent( Spot spot, Spot v1, DepthFirstSearch<Spot, Link> spotLinkDepthFirstSearch )
			{
				// do nothing
			}
		};

		DepthFirstSearch<Spot, Link> search = new DepthFirstSearch<>( model.getGraph(), GraphSearch.SearchDirection.DIRECTED );
		search.setTraversalListener( searchListener );
		search.start( root );
	}

	private TagSetStructure.TagSet createTagSet( Model model, Map<String, Integer> tagsAndColors )
	{
		TagSetModel<Spot, Link> tagSetModel = model.getTagSetModel();
		TagSetStructure tss = copy( tagSetModel.getTagSetStructure() );
		TagSetStructure.TagSet tagSet = tss.createTagSet("lineages");
		tagsAndColors.forEach( tagSet::createTag );
		tagSetModel.setTagSetStructure( tss );
		return tagSet;
	}

	private TagSetStructure copy( TagSetStructure original )
	{
		TagSetStructure copy = new TagSetStructure();
		copy.set( original );
		return copy;
	}

	@NotNull
	private Map<String, Integer> createColorMap( Set<String> labels )
	{
		Map<String, Integer> colors = new HashMap<>();
		int count = 4;
		for(String label : labels )
			colors.put( label, Glasbey.GLASBEY[ count++ ] );
		return colors;
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
	private GraphPair initializeTreeMatching( Context context )
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

	private void matchTree( AffineTransform3D transformAB, ModelGraph graphA, ModelGraph graphB, Spot rootA, Spot rootB, RefList<Spot> toBeFlipped )
	{
		Spot dividingA = LineageTreeUtils.getDividingSpot( graphA, rootA );
		Spot dividingB = LineageTreeUtils.getDividingSpot( graphB, rootB );
		try
		{
			if(dividingA.outgoingEdges().size() != 2 ||
			  dividingB.outgoingEdges().size() != 2)
				return;
			double[] directionA = SortTreeUtils.directionOfCellDevision( graphA, dividingA );
			double[] directionB = SortTreeUtils.directionOfCellDevision( graphB, dividingB );
			transformAB.apply( directionA, directionA );
			boolean flip = SortTreeUtils.scalarProduct( directionA, directionB ) < 0;
			if(flip)
				toBeFlipped.add( dividingB );
			{
				Spot childA = dividingA.outgoingEdges().get( 0 ).getTarget();
				Spot childB = dividingB.outgoingEdges().get( flip ? 1 : 0 ).getTarget();
				matchTree( transformAB, graphA, graphB, childA, childB, toBeFlipped );
				graphA.releaseRef( childA );
				graphA.releaseRef( childB );
			}
			{
				Spot childA = dividingA.outgoingEdges().get( 1 ).getTarget();
				Spot childB = dividingB.outgoingEdges().get( flip ? 0 : 1 ).getTarget();
				matchTree( transformAB, graphA, graphB, childA, childB, toBeFlipped );
				graphA.releaseRef( childA );
				graphA.releaseRef( childB );
			}
		} finally
		{
			graphA.releaseRef( dividingA );
			graphB.releaseRef( dividingB );
		}
	}

	private Set<String> commonRootLabels( ObjectRefMap<String, Spot> rootsA, ObjectRefMap<String, Spot> rootsB )
	{
		List<String> blackList = Arrays.asList( "ventral", "dorsal", "left", "right", "vegetal_posterior", "apical_anterior" );
		Set<String> intersection = new HashSet<>( rootsA.keySet());
		intersection.retainAll( rootsB.keySet() );
		intersection.removeAll( blackList );
		return intersection;
	}

	private MamutAppModel openAppModel( Context context, String projectPath )
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
