package org.mastodon.mamut.tomancak.tree_matching;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.junit.Test;

public class EstimateTransformationTest
{
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
		AffineTransform3D m = EstimateTransformation.estimateScaleRotationTranslation(a, b);
		EstimateTransformationTest.assertTransformEquals( expected, m, 0.001 );
	}

	private static RealPoint point( double... values )
	{
		return new RealPoint(values);
	}

	private static void assertTransformEquals( AffineTransform3D expected, AffineTransform3D actual, double tolerance )
	{
		for ( int row = 0; row < 3; row++ )
			for ( int col = 0; col < 4; col++ )
				assertEquals( expected.get( row, col ), actual.get( row, col ), tolerance );
	}
	
	// The rest of the class tests an algorithm that estimate an affine transform between to point clouds.
	// This algorithm is currently not used.
	
	@Test
	public void testLinearRegression() {
		final List< RealPoint > a = Arrays.asList(point(1,0,0), point(0,2,0), point(0,0,3), point(0, 0, 0));
		final AffineTransform3D expected = new AffineTransform3D();
		expected.set( 1, -2, 3, 4, -5, 6, 7, 8, -9, 10, 11, 12 );
		final List<RealPoint> b = transformPoints( expected, a );
		final AffineTransform3D actual = estimateAffineTransform( a, b );
		EstimateTransformationTest.assertTransformEquals( expected, actual, 0.01);
	}

	@Test
	public void testEstimateAffineTransform() {
		final AffineTransform3D m = new AffineTransform3D();
		m.set( 1, 4, 6, 2, 6, 3, 5, 8, 1, 2, 3, 4 );
		final Random r = new Random(42);
		final List<RealPoint> a = randomPoints(r, 3, 1000);
		final List<RealPoint> noise = randomPoints(r, 3, a.size());
		final List<RealPoint> b = map(transformPoints( m, a ), noise, EstimateTransformationTest::add);
		final AffineTransform3D actual = estimateAffineTransform( a, b );
		EstimateTransformationTest.assertTransformEquals( m, actual, 0.1 );
	}

	private static <A,B,R> List<R> map( List<A> a, List<B> b, BiFunction<A, B, R> operation )
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

	private static RealPoint add( RealPoint a, RealPoint b ) {
		int n = a.numDimensions();
		assert n == b.numDimensions();
		RealPoint r = new RealPoint( n );
		for ( int i = 0; i < n; i++ )
		{
			r.setPosition( a.getDoublePosition( i ) +  b.getDoublePosition( i ), i );
		}
		return r;
	}

	private static List<RealPoint> randomPoints( Random r, int numDimensions, int numPoints )
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

	private static AffineTransform3D estimateAffineTransform( List<RealPoint> a, List<RealPoint> b )
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
}
