package org.mastodon.mamut.tomancak.lineage_registration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.mastodon.collection.RefObjectMap;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefObjectHashMap;
import org.mastodon.collection.ref.RefRefHashMap;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.model.branch.BranchLink;
import org.mastodon.mamut.model.branch.BranchSpot;
import org.mastodon.mamut.model.branch.ModelBranchGraph;
import org.mastodon.mamut.tomancak.lineage_registration.spatial_registration.EstimateTransformation;
import org.mastodon.mamut.tomancak.lineage_registration.spatial_registration.SpatialRegistrationMethod;
import org.mastodon.mamut.tomancak.sort_tree.SortTreeUtils;
import org.scijava.Context;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;

// TODOs: get s graph from the new approach
public class ImproveAnglesDemo
{
	public static void main( String... args )
	{
		try (Context context = new Context())
		{
			WindowManager windowManager1 = LineageRegistrationDemo.openAppModel( context, LineageRegistrationDemo.project1 );
			WindowManager windowManager2 = LineageRegistrationDemo.openAppModel( context, LineageRegistrationDemo.project2 );
			RegisteredGraphs rg = LineageRegistrationAlgorithm.run(
					windowManager1.getAppModel().getModel(), 0,
					windowManager2.getAppModel().getModel(), 0,
					SpatialRegistrationMethod.DYNAMIC_ROOTS );
			LineageRegistrationUtils.plotAngleAgainstTimepoint( rg.anglesA );
			ModelGraph graphA = windowManager1.getAppModel().getModel().getGraph();
			ModelBranchGraph branchGraphA = new ModelBranchGraph( graphA );
			List< RefObjectMap< BranchSpot, double[] > > da = getRefObjectMaps( graphA, branchGraphA );
			ModelGraph graphB = windowManager2.getAppModel().getModel().getGraph();
			ModelBranchGraph branchGraphB = new ModelBranchGraph( graphB );
			List< RefObjectMap< BranchSpot, double[] > > db = getRefObjectMaps( graphB, branchGraphB );
			RefRefMap< BranchSpot, BranchSpot > map = toBranchSpotMap( rg.mapAB, branchGraphA, branchGraphB );
			List< Pair< Double, Double > > angles = new ArrayList<>();
			BranchSpot ref = branchGraphA.vertexRef();
			Spot ref2 = graphA.vertexRef();
			Average meanOldAngle = new Average();
			Average meanNewAngle = new Average();
			RefMapUtils.forEach( map, ( a, b ) -> {
				try
				{
					if ( !isFirstChild( branchGraphA, a ) )
						return;
					double v = computeAngle( a, da, b, db );
					BranchSpot parent = a.incomingEdges().iterator().next().getSource( ref );
					Spot parentStart = branchGraphA.getFirstLinkedVertex( parent, ref2 );
					if ( parentStart.getTimepoint() < 200 )
						return;
					double w = rg.anglesA.get( parentStart );
					if ( w > 90. )
						w = 180. - w;
					meanNewAngle.add( v );
					meanOldAngle.add( w );
					angles.add( new ValuePair<>( Math.min( v, w ), w ) );
				}
				catch ( NullPointerException e )
				{
					// ignore (happens when a or b is not in the map)
				}
			} );
			System.out.println( "old: " + meanOldAngle.get() + " new: " + meanNewAngle.get() );
			plotAngles( angles );
		}
	}

	private static List< RefObjectMap< BranchSpot, double[] > > getRefObjectMaps( ModelGraph graphA, ModelBranchGraph branchGraphA )
	{
		RefObjectMap< BranchSpot, double[] > directionsA = computeDirections( graphA, branchGraphA );
		RefObjectMap< BranchSpot, double[] > parentDirectionsA = fromParentToChild( directionsA, branchGraphA );
		RefObjectMap< BranchSpot, double[] > grantParentDirectionsA = fromParentToChild( parentDirectionsA, branchGraphA );
		RefObjectMap< BranchSpot, double[] > values = computeMovement( graphA, branchGraphA );
		RefObjectMap< BranchSpot, double[] > x = fromParentToChild( values, branchGraphA );
		RefObjectMap< BranchSpot, double[] > y = fromParentToChild( x, branchGraphA );
		return Arrays.asList( directionsA, parentDirectionsA, grantParentDirectionsA, x );
	}

	private static RefObjectMap< BranchSpot, double[] > computeMovement( ModelGraph graphA, ModelBranchGraph branchGraphA )
	{
		RefObjectMap< BranchSpot, double[] > map = new RefObjectHashMap<>( branchGraphA.vertices().getRefPool() );
		Spot ref1 = graphA.vertexRef();
		Spot ref2 = graphA.vertexRef();
		for ( BranchSpot branchSpot : branchGraphA.vertices() )
		{
			double[] start = branchGraphA.getFirstLinkedVertex( branchSpot, ref1 ).positionAsDoubleArray();
			double[] end = branchGraphA.getLastLinkedVertex( branchSpot, ref2 ).positionAsDoubleArray();
			LinAlgHelpers.subtract( end, start, end );
			map.put( branchSpot, end );
		}
		return map;
	}

	private static double computeAngle( BranchSpot a, List< RefObjectMap< BranchSpot, double[] > > da, BranchSpot b, List< RefObjectMap< BranchSpot, double[] > > db )
	{
		double[][] dA = new double[ da.size() ][ 3 ];
		double[][] dB = new double[ db.size() ][ 3 ];
		for ( int i = 0; i < da.size(); i++ )
		{
			dA[ i ] = da.get( i ).get( a );
			dB[ i ] = db.get( i ).get( b );
		}
		return computeAngle( dA, dB );
	}

	static class Average
	{
		double sum = 0;

		int counter = 0;

		public void add( double value )
		{
			sum += value;
			counter++;
		}

		public double get()
		{
			return sum / counter;
		}
	}

	private static double computeAngle( double[][] a, double[][] b )
	{
		List< PointMatch > matches = new ArrayList<>();
		for ( int i = 1; i < a.length; i++ )
		{
			matches.add( new PointMatch( new Point( a[ i ] ), new Point( b[ i ] ) ) );
			matches.add( new PointMatch( new Point( negate( a[ i ] ) ), new Point( negate( b[ i ] ) ) ) );
		}
		AffineTransform3D tranformAB = EstimateTransformation.fitTransform( matches );
		double[] target = new double[ 3 ];
		tranformAB.applyInverse( target, b[ 0 ] );
		return SortTreeUtils.angleInDegree( a[ 0 ], target );
	}

	private static double[] negate( double[] values )
	{
		double[] result = new double[ values.length ];
		for ( int i = 0; i < values.length; i++ )
			result[ i ] = -values[ i ];
		return result;
	}

	private static < T > RefObjectMap< BranchSpot, T > fromParentToChild( RefObjectMap< BranchSpot, T > values, ModelBranchGraph branchGraph )
	{
		BranchSpot ref = branchGraph.vertexRef();
		try
		{
			RefObjectMap< BranchSpot, T > map = new RefObjectHashMap<>( branchGraph.vertices().getRefPool(), values.size() );
			for ( BranchSpot spot : values.keySet() )
				map.put( spot, values.get( getParent( spot, ref ) ) );
			return map;
		}
		finally
		{
			branchGraph.releaseRef( ref );
		}
	}

	private static boolean isFirstChild( ModelBranchGraph graph, BranchSpot spot )
	{
		BranchSpot ref = graph.vertexRef();
		BranchSpot ref2 = graph.vertexRef();
		try
		{
			if ( spot.incomingEdges().size() != 1 )
				return false;
			BranchSpot parent = spot.incomingEdges().iterator().next().getSource( ref );
			BranchSpot firstChild = parent.outgoingEdges().iterator().next().getTarget( ref2 );
			return spot.equals( firstChild );
		}
		finally
		{
			graph.releaseRef( ref );
			graph.releaseRef( ref2 );
		}
	}

	private static void plotAngles( List< Pair< Double, Double > > angles )
	{
		// Use JFreeChart to plot the angles.
		XYSeriesCollection dataset = new XYSeriesCollection();
		XYSeries series = new XYSeries( "Angles" );
		for ( Pair< Double, Double > angle : angles )
			series.add( angle.getA(), angle.getB() );
		dataset.addSeries( series );
		JFreeChart chart = ChartFactory.createScatterPlot( "Angles", "Angle A", "Angle B", dataset );
		ChartFrame frame = new ChartFrame( "Angles", chart );
		frame.pack();
		frame.setVisible( true );
	}

	private static BranchSpot getParent( BranchSpot a, BranchSpot refA )
	{
		return a.incomingEdges().get( 0 ).getSource( refA );
	}

	private static RefRefMap< BranchSpot, BranchSpot > toBranchSpotMap( RefRefMap< Spot, Spot > mapAB, ModelBranchGraph branchGraphA, ModelBranchGraph branchGraphB )
	{
		BranchSpot refA = branchGraphA.vertexRef();
		BranchSpot refB = branchGraphB.vertexRef();
		try
		{
			RefRefMap< BranchSpot, BranchSpot > map = new RefRefHashMap<>( branchGraphA.vertices().getRefPool(), branchGraphB.vertices().getRefPool() );
			RefMapUtils.forEach( mapAB, ( a, b ) -> {
				BranchSpot branchA = branchGraphA.getBranchVertex( a, refA );
				BranchSpot branchB = branchGraphB.getBranchVertex( b, refB );
				map.put( branchA, branchB );
			} );
			return map;
		}
		finally
		{
			branchGraphA.releaseRef( refA );
			branchGraphB.releaseRef( refB );
		}
	}

	private static RefObjectMap< BranchSpot, double[] > computeDirections( ModelGraph graph, ModelBranchGraph branchGraph )
	{
		RefObjectMap< BranchSpot, double[] > directions = new RefObjectHashMap<>( branchGraph.vertices().getRefPool(), branchGraph.vertices().size() );
		Spot ref = graph.vertexRef();
		BranchSpot ref2 = branchGraph.vertexRef();
		for ( BranchSpot branch : branchGraph.vertices() )
		{
			if ( branch.outgoingEdges().size() != 2 )
				continue;
			double[] direction = SortTreeUtils.directionOfCellDevision( graph, branchGraph.getLastLinkedVertex( branch, ref ) );
			Iterator< BranchLink > iterator = branch.outgoingEdges().iterator();
			BranchSpot child = iterator.next().getTarget( ref2 );
			directions.put( child, direction );
			child = iterator.next().getTarget( ref2 );
			double[] oppositeDirection = new double[ 3 ];
			LinAlgHelpers.scale( direction, -1, oppositeDirection );
			directions.put( child, oppositeDirection );
		}
		return directions;
	}
}
