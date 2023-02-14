package org.mastodon.mamut.tomancak.lineage_registration;

import net.imglib2.realtransform.AffineTransform3D;

import org.mastodon.collection.RefList;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefArrayList;
import org.mastodon.collection.ref.RefRefHashMap;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.tomancak.sort_tree.FlipDescendants;
import org.mastodon.mamut.tomancak.sort_tree.SortTreeUtils;

/**
 * An algorithm that by compares the "spindle directions" in two lineages.
 * By doing so it figures out which spots need to be flipped in order
 * to match the TrackSchemes of both lineages.
 */
public class LineageRegistrationAlgorithm
{
	private final AffineTransform3D transformAB;

	private final ModelGraph graphA;

	private final ModelGraph graphB;

	/**
	 * Map branch starting spots in graphA to branch starting spots in graphB.
	 */
	private final RefRefMap< Spot, Spot > map;

	public static void run( Model embryoA, Model embryoB )
	{
		ModelGraph graphA = embryoA.getGraph();
		ModelGraph graphB = embryoB.getGraph();
		RefRefMap< Spot, Spot > roots = RootsPairing.pairDividingRoots( graphA, graphB );
		AffineTransform3D transformAB = EstimateTransformation.estimateScaleRotationAndTranslation( roots );
		LineageRegistrationAlgorithm algorithm = new LineageRegistrationAlgorithm(
				graphA, graphB,
				roots, transformAB );
		RefList< Spot > flip = algorithm.getToBeFlipped();
		FlipDescendants.flipDescendants( embryoB, flip );
	}

	public LineageRegistrationAlgorithm( ModelGraph graphA, ModelGraph graphB, RefRefMap< Spot, Spot > roots,
			AffineTransform3D transformAB )
	{
		this.transformAB = noOffsetTransform( transformAB );
		this.graphA = graphA;
		this.graphB = graphB;
		this.map = new RefRefHashMap<>( graphA.vertices().getRefPool(), graphB.vertices().getRefPool() );
		Spot refB = graphB.vertexRef();
		try
		{
			for ( Spot rootA : roots.keySet() )
			{
				Spot rootB = roots.get( rootA, refB );
				matchTree( rootA, rootB );
			}
		}
		finally
		{
			graphB.releaseRef( refB );
		}
	}

	private void matchTree( Spot rootA, Spot rootB )
	{
		map.put( rootA, rootB );
		Spot refA = graphA.vertexRef();
		Spot refB = graphB.vertexRef();
		try
		{
			Spot dividingA = LineageTreeUtils.getBranchEnd( rootA, refA );
			Spot dividingB = LineageTreeUtils.getBranchEnd( rootB, refB );
			boolean bothDivide = dividingA.outgoingEdges().size() == 2 &&
					dividingB.outgoingEdges().size() == 2;
			if ( !bothDivide )
				return;
			double[] directionA = SortTreeUtils.directionOfCellDevision( graphA, dividingA );
			double[] directionB = SortTreeUtils.directionOfCellDevision( graphB, dividingB );
			transformAB.apply( directionA, directionA );
			boolean flip = SortTreeUtils.scalarProduct( directionA, directionB ) < 0;
			matchChildTree( dividingA, dividingB, 0, flip ? 1 : 0 );
			matchChildTree( dividingA, dividingB, 1, flip ? 0 : 1 );
		}
		finally
		{
			graphA.releaseRef( refA );
			graphB.releaseRef( refB );
		}
	}

	private void matchChildTree( Spot dividingA, Spot dividingB, int indexA, int indexB )
	{
		Spot childA = dividingA.outgoingEdges().get( indexA ).getTarget();
		Spot childB = dividingB.outgoingEdges().get( indexB ).getTarget();
		try
		{
			matchTree( childA, childB );
		}
		finally
		{
			graphA.releaseRef( childA );
			graphB.releaseRef( childB );
		}
	}

	public RefList< Spot > getToBeFlipped()
	{
		Spot refA = graphA.vertexRef();
		Spot refB = graphB.vertexRef();
		Spot refB0 = graphB.vertexRef();
		try
		{
			RefArrayList< Spot > list = new RefArrayList<>( graphB.vertices().getRefPool() );
			for ( Spot spotA : map.keySet() )
			{
				Spot spotB = map.get( spotA, refB0 );
				Spot dividingA = LineageTreeUtils.getBranchEnd( spotA, refA );
				Spot dividingB = LineageTreeUtils.getBranchEnd( spotB, refB );
				if ( doesRequireFlip( dividingA, dividingB ) )
					list.add( dividingB );
			}
			return list;
		}
		finally
		{
			graphA.releaseRef( refA );
			graphB.releaseRef( refB );
			graphB.releaseRef( refB0 );
		}
	}

	private boolean doesRequireFlip( Spot dividingA, Spot dividingB )
	{
		Spot refA = graphA.vertexRef();
		Spot refB = graphB.vertexRef();
		Spot refB2 = graphB.vertexRef();
		try
		{
			boolean bothDivide = dividingA.outgoingEdges().size() == 2 &&
					dividingB.outgoingEdges().size() == 2;
			if ( !bothDivide )
				return false;
			Spot firstChildA = dividingA.outgoingEdges().get( 0 ).getTarget( refA );
			Spot secondChildB = dividingB.outgoingEdges().get( 1 ).getTarget( refB );
			return map.get( firstChildA, refB2 ).equals( secondChildB );
		}
		finally
		{
			graphA.releaseRef( refA );
			graphB.releaseRef( refB );
			graphB.releaseRef( refB2 );
		}
	}

	/**
	 * @return a {@link RefRefMap} that maps (branch starting) spots in embryoA
	 * to the matched (branch starting) spots in embryoB.
	 */
	public RefRefMap< Spot, Spot > getMapping()
	{
		return map;
	}

	private static AffineTransform3D noOffsetTransform( AffineTransform3D transformAB )
	{
		AffineTransform3D noOffsetTransform = new AffineTransform3D();
		noOffsetTransform.set( transformAB );
		noOffsetTransform.setTranslation( 0, 0, 0 );
		return noOffsetTransform;
	}
}
