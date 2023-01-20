package org.mastodon.mamut.tomancak.lineage_registration;

import java.util.List;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

public class Unused
{

	private static void sanityCheck( AffineTransform3D transfrom )
	{
		RealLocalizable x = transfrom.d( 0 );
		RealLocalizable y = transfrom.d(1);
		RealLocalizable z = transfrom.d(2);
		System.out.printf( "length %.3f %.3f %.3f", length(x), length(y), length(z) );
		System.out.printf( "angle %.3f %.3f %.3f", angle( x, y ), angle( y, z ), angle( x, z ));
	}

	private static void show( List< RealPoint > pointsA, List<RealPoint> pointsB )
	{
		double distance = 0;
		for ( int i = 0; i < pointsA.size(); i++ )
			distance += distance( pointsA.get( i ), pointsB.get( i ) );
		System.out.println(distance);
	}

	private static double distance( RealLocalizable a, RealLocalizable b )
	{
		double sum = 0;
		for ( int i = 0; i < a.numDimensions(); i++ )
			sum += sqr( a.getDoublePosition( i ) - b.getDoublePosition( i ) );
		return Math.sqrt( sum );
	}

	private static double length( RealLocalizable vector) {
		double sum = 0;
		for ( int i = 0; i < vector.numDimensions(); i++ )
			sum += sqr( vector.getDoublePosition( i ));
		return Math.sqrt( sum );
	}

	private static double scalarProduct( RealLocalizable a, RealLocalizable b )
	{
		double sum = 0;
		for ( int i = 0; i < a.numDimensions(); i++ )
			sum += a.getDoublePosition( i ) * b.getDoublePosition( i );
		return sum;
	}

	private static double cosAngle( RealLocalizable a, RealLocalizable b ) {
		return scalarProduct( a, b ) / length( a ) / length( b );
	}

	private static double angle( RealLocalizable a, RealLocalizable b ) {
		return Math.acos( cosAngle( a, b ) ) / Math.PI * 180;
	}

	private static double sqr( double v )
	{
		return v * v;
	}
}
