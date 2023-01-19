package org.mastodon.mamut.tomancak.tree_matching;

import java.util.ArrayList;
import java.util.List;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.SimilarityModel3D;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.mastodon.collection.RefRefMap;
import org.mastodon.mamut.model.Spot;

public class EstimateTransformation
{
	static AffineTransform3D estimateScaleRotationTranslation( List< RealPoint > a, List< RealPoint > b )
	{
		AbstractAffineModel3D model = new SimilarityModel3D();
		assert a.size() == b.size();
		List< PointMatch > matches = new ArrayList<>(a.size());
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

	static AffineTransform3D estimateTransform( RefRefMap< Spot, Spot > roots )
	{
		List< RealPoint > pointsA = new ArrayList<>();
		List< RealPoint > pointsB = new ArrayList<>();
		for( Spot rootA : roots.keySet() ) {
			Spot rootB = roots.get( rootA );
			pointsA.add( new RealPoint( rootA ) );
			pointsB.add( new RealPoint( rootB ) );
		}
		return estimateScaleRotationTranslation( pointsA, pointsB );
	}
}
