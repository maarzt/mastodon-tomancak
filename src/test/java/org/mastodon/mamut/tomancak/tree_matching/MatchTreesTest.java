package org.mastodon.mamut.tomancak.tree_matching;

import mpicbg.models.*;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.jetbrains.annotations.NotNull;
import org.jfree.data.xy.XYSeries;
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
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;

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

	@NotNull
	private AffineTransform3D estimateTransform( GraphPair m )
	{
		List<RealPoint> pointsA = new ArrayList<>();
		List<RealPoint> pointsB = new ArrayList<>();
		for(String label : m.commonRootLabels ) {
			pointsA.add(new RealPoint( m.rootsA.get( label ) ));
			pointsB.add(new RealPoint( m.rootsB.get( label ) ));
		}
		return estimateScaleRotationTranslation( pointsA, pointsB );
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

	private void sanityCheck( AffineTransform3D transfrom )
	{
		RealLocalizable x = transfrom.d( 0 );
		RealLocalizable y = transfrom.d(1);
		RealLocalizable z = transfrom.d(2);
		System.out.printf( "length %.3f %.3f %.3f", length(x), length(y), length(z) );
		System.out.printf( "angle %.3f %.3f %.3f", angle( x, y ), angle( y, z ), angle( x, z ));
	}

	private void show( List<RealPoint> pointsA, List<RealPoint> pointsB )
	{
		double distance = 0;
		for ( int i = 0; i < pointsA.size(); i++ )
			distance += distance( pointsA.get( i ), pointsB.get( i ) );
		System.out.println(distance);
	}

	private double distance( RealLocalizable a, RealLocalizable b )
	{
		double sum = 0;
		for ( int i = 0; i < a.numDimensions(); i++ )
			sum += sqr( a.getDoublePosition( i ) - b.getDoublePosition( i ) );
		return Math.sqrt( sum );
	}

	private double length( RealLocalizable vector) {
		double sum = 0;
		for ( int i = 0; i < vector.numDimensions(); i++ )
			sum += sqr( vector.getDoublePosition( i ));
		return Math.sqrt( sum );
	}

	private double scalarProduct( RealLocalizable a, RealLocalizable b )
	{
		double sum = 0;
		for ( int i = 0; i < a.numDimensions(); i++ )
			sum += a.getDoublePosition( i ) * b.getDoublePosition( i );
		return sum;
	}

	private double cosAngle( RealLocalizable a, RealLocalizable b ) {
		return scalarProduct( a, b ) / length( a ) / length( b );
	}

	private double angle( RealLocalizable a, RealLocalizable b ) {
		return Math.acos( cosAngle( a, b ) ) / Math.PI * 180;
	}

	private double sqr( double v )
	{
		return v * v;
	}

	private XYSeries getXySeries( List<RealPoint> pointsA, String a1 )
	{
		XYSeries as = new XYSeries( a1 );
		for(RealPoint a: pointsA )
			as.add( a.getDoublePosition( 0 ), a.getDoublePosition( 1 ) );
		return as;
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

	@Test
	public void testLinearRegression() {
		final List<RealPoint> a = Arrays.asList(point(1,0,0), point(0,2,0), point(0,0,3), point(0, 0, 0));
		final AffineTransform3D expected = new AffineTransform3D();
		expected.set( 1, -2, 3, 4, -5, 6, 7, 8, -9, 10, 11, 12 );
		final List<RealPoint> b = transformPoints( expected, a );
		final AffineTransform3D actual = estimateAffineTransform( a, b );
		assertTransformEquals( expected, actual, 0.01);
	}

	@Test
	public void testEstimateAffineTransform() {
		final AffineTransform3D m = new AffineTransform3D();
		m.set( 1, 4, 6, 2, 6, 3, 5, 8, 1, 2, 3, 4 );
		final Random r = new Random(42);
		final List<RealPoint> a = randomPoints(r, 3, 1000);
		final List<RealPoint> noise = randomPoints(r, 3, a.size());
		final List<RealPoint> b = map(transformPoints( m, a ), noise, this::add);
		final AffineTransform3D actual = estimateAffineTransform( a, b );
		assertTransformEquals( m, actual, 0.1 );
	}

	private <A,B,R> List<R> map( List<A> a, List<B> b, BiFunction<A, B, R> operation )
	{
		int size = a.size();
		assert size == b.size();
		List<R> result = new ArrayList<>(size);
		for ( int i = 0; i < size; i++ )
		{
			result.add(operation.apply( a.get(i), b.get( i ) ));
		}
		return result;
	}

	private RealPoint add( RealPoint a, RealPoint b ) {
		int n = a.numDimensions();
		assert n == b.numDimensions();
		RealPoint r = new RealPoint( n );
		for ( int i = 0; i < n; i++ )
		{
			r.setPosition( a.getDoublePosition( i ) +  b.getDoublePosition( i ), i );
		}
		return r;
	}

	private List<RealPoint> randomPoints( Random r, int numDimensions, int numPoints )
	{
		final List<RealPoint> result = new ArrayList<>();
		for ( int i = 0; i < numPoints; i++ )
		{
			RealPoint point = new RealPoint( numDimensions );
			for ( int j = 0; j < numDimensions; j++ )
			{
				point.setPosition( r.nextGaussian(), j );
			}
			result.add( point );
		}
		return result;
	}

	private void assertTransformEquals( AffineTransform3D expected, AffineTransform3D actual, double tolerance )
	{
		for ( int row = 0; row < 3; row++ )
			for ( int col = 0; col < 4; col++ )
				assertEquals( expected.get( row, col ), actual.get( row, col ), tolerance );
	}

	private List<RealPoint> transformPoints( AffineTransform3D transform, List<RealPoint> points )
	{
		List<RealPoint> b = new ArrayList<>();
		for(RealPoint p : points )
		{
			RealPoint r = new RealPoint( 3 );
			transform.apply( p, r );
			b.add( r );
		}
		return b;
	}

	private AffineTransform3D estimateAffineTransform( List<RealPoint> a, List<RealPoint> b )
	{
		int n = b.get( 0 ).numDimensions();
		double[] y = new double[ b.size() * n];
		for ( int i = 0; i < b.size(); i++ )
		{
			RealPoint point = b.get( i );
			for ( int j = 0; j < n; j++ )
				y[ i * n + j ] = point.getDoublePosition( j );
		}
		double [][] X = new double[ a.size() * n][];
		for ( int i = 0; i < a.size(); i++ )
		{
			RealPoint point = a.get( i );
			for ( int j = 0; j < n; j++ ) {
				double [] x = new double[n * n + n - 1];
				for ( int d = 0; d < n; d++ )
				{
					x[2 + j * n + d] = point.getDoublePosition( d );
				}
				x[0] = j == 1 ? 1 : 0;
				x[1] = j == 2 ? 1 : 0;
				X[i * n + j] = x;
			}
		}
		OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
		regression.newSampleData( y, X );
		double[] p = regression.estimateRegressionParameters();
		AffineTransform3D m = new AffineTransform3D();
		m.set(
				p[3], p[4], p[5], p[0],
				p[6], p[7], p[8], p[0] + p[1],
				p[9], p[10], p[11], p[0] + p[2]
		);
		return m;
	}

	private RealPoint point( double... values )
	{
		return new RealPoint(values);
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

	@Test
	public void testEstimateScaleRotationTransform() {
		AffineTransform3D expected = new AffineTransform3D();
		expected.rotate( 0, Math.PI / 7 );
		expected.rotate( 1, Math.PI / 7 );
		expected.rotate( 2, Math.PI / 7 );
		expected.scale( 2 );
		expected.translate( 7,6,8 );
		List<RealPoint> a = Arrays.asList( point(1, 0, 0), point( 0, 1, 0 ), point( 0, 0, 1), point(0, 0,0) );
		List<RealPoint> b = transformPoints( expected, a );
		AffineTransform3D m = estimateScaleRotationTranslation(a, b);
		assertTransformEquals( expected, m, 0.001 );
	}

	private static void print(AffineTransform3D m) {
		for ( int row = 0; row < 3; row++ )
		{
			StringJoiner s = new StringJoiner( ", " );
			for ( int column = 0; column < 4; column++ )
				s.add(String.format( "%.3f", m.get( row, column ) ));
			System.out.println(s);
		}
	}

	private AffineTransform3D estimateScaleRotationTranslation( List<RealPoint> a, List<RealPoint> b )
	{
		AbstractAffineModel3D model = new SimilarityModel3D();
		assert a.size() == b.size();
		List<PointMatch> matches = new ArrayList<>(a.size());
		for ( int i = 0; i < a.size(); i++ )
		{
			matches.add(new PointMatch( new Point( a.get( i ).positionAsDoubleArray() ), new Point( b.get( i ).positionAsDoubleArray() ), 1 ));
		}
		try
		{
			model.fit( matches );
		}
		catch ( NotEnoughDataPointsException | IllDefinedDataPointsException e )
		{
			throw new RuntimeException(e);
		}
		double[] m = model.getMatrix( null );
		AffineTransform3D affineTransform3D = new AffineTransform3D();
		affineTransform3D.set( m );
		return affineTransform3D;
	}

}
