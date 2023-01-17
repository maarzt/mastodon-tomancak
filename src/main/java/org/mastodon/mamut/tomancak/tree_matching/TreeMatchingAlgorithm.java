package org.mastodon.mamut.tomancak.tree_matching;

import net.imglib2.realtransform.AffineTransform3D;
import org.mastodon.collection.RefList;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.tomancak.sort_tree.SortTreeUtils;

public class TreeMatchingAlgorithm
{
	static void matchTree( AffineTransform3D transformAB, ModelGraph graphA, ModelGraph graphB, Spot rootA, Spot rootB, RefList< Spot > toBeFlipped )
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
			matchChildTree( transformAB, graphA, graphB, toBeFlipped, dividingA, dividingB, 0, flip ? 1 : 0 );
			matchChildTree( transformAB, graphA, graphB, toBeFlipped, dividingA, dividingB, 1, flip ? 0 : 1 );
		} finally
		{
			graphA.releaseRef( dividingA );
			graphB.releaseRef( dividingB );
		}
	}

	private static void matchChildTree( AffineTransform3D transformAB, ModelGraph graphA, ModelGraph graphB, RefList< Spot > toBeFlipped, Spot dividingA, Spot dividingB, int indexA, int indexB )
	{
		Spot childA = dividingA.outgoingEdges().get( indexA ).getTarget();
		Spot childB = dividingB.outgoingEdges().get( indexB ).getTarget();
		matchTree( transformAB, graphA, graphB, childA, childB, toBeFlipped );
		graphA.releaseRef( childA );
		graphA.releaseRef( childB );
	}
}
