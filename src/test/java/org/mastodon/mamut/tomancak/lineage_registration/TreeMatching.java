package org.mastodon.mamut.tomancak.lineage_registration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.mastodon.collection.ObjectRefMap;
import org.mastodon.collection.RefList;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.ObjectRefHashMap;
import org.mastodon.collection.ref.RefArrayList;
import org.mastodon.collection.ref.RefRefHashMap;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.model.branch.BranchLink;
import org.mastodon.mamut.model.branch.BranchSpot;
import org.mastodon.mamut.model.branch.ModelBranchGraph;
import org.mastodon.mamut.treesimilarity.ZhangUnorderedTreeEditDistance;
import org.mastodon.mamut.treesimilarity.tree.SimpleTree;
import org.mastodon.mamut.treesimilarity.tree.Tree;

import gnu.trove.map.TByteByteMap;

public class TreeMatching
{
	static RefRefMap< Spot, Spot > register( Model firstModel, Model otherModel )
	{
		RefRefHashMap< Spot, Spot > result = new RefRefHashMap<>( firstModel.getGraph().vertices().getRefPool(), otherModel.getGraph().vertices().getRefPool() );
		int numberOfSpots1 = maximumNumberOfSpots( firstModel );
		int numberOfSpots2 = maximumNumberOfSpots( otherModel );
		int cutoffNumber = ( int ) ( Math.min( numberOfSpots1, numberOfSpots2 ) * 0.9 );
		int cutoffTime1 = firstTimepointWithAtLeast( firstModel, cutoffNumber );
		int cutoffTime2 = firstTimepointWithAtLeast( otherModel, cutoffNumber );
		ModelBranchGraph branchGraph1 = new ModelBranchGraph( firstModel.getGraph() );
		ModelBranchGraph branchGraph2 = new ModelBranchGraph( otherModel.getGraph() );
		Set< String > commonLabels = intersect( dividingBranchLabels( branchGraph1 ), dividingBranchLabels( branchGraph2 ) );
		int startTime1 = startTime( branchGraph1, commonLabels );
		int startTime2 = startTime( branchGraph2, commonLabels );
		ObjectRefMap< Tree< Double >, Spot > treeToSpot1 = new ObjectRefHashMap<>( firstModel.getGraph().vertices().getRefPool() );
		ObjectRefMap< Tree< Double >, Spot > treeToSpot2 = new ObjectRefHashMap<>( otherModel.getGraph().vertices().getRefPool() );
		Map< String, Tree< Double > > trees1 = convertToSimpleTrees( branchGraph1, startTime1, cutoffTime1, commonLabels, treeToSpot1 );
		Map< String, Tree< Double > > trees2 = convertToSimpleTrees( branchGraph2, startTime2, cutoffTime2, commonLabels, treeToSpot2 );
		for ( String label : intersect( trees1.keySet(), trees2.keySet() ) )
		{
			Tree< Double > tree1 = trees1.get( label );
			Tree< Double > tree2 = trees2.get( label );
			Map< Tree< Double >, Tree< Double > > mapping = ZhangUnorderedTreeEditDistance.nodeMapping( tree1, tree2, ( a, b ) -> {
				if ( b == null )
					return Math.abs( a );
				else
					return Math.abs( a - b );
			} );
			mapping.forEach( ( t1, t2 ) -> {
				Spot s1 = treeToSpot1.get( t1 );
				Spot s2 = treeToSpot2.get( t2 );
				result.put( s1, s2 );
			} );
		}
		return result;
	}

	private static Map< String, Tree< Double > > convertToSimpleTrees( ModelBranchGraph branchGraph, int startTime, int endTime, Set< String > commonLabels,
			ObjectRefMap< Tree< Double >, Spot > treeToSpot )
	{

		HashMap< String, Tree< Double > > stringTreeHashMap = new HashMap<>();
		RefList< BranchSpot > roots = findRootNodes( branchGraph, startTime, commonLabels );
		for ( BranchSpot root : roots )
			stringTreeHashMap.put( root.getFirstLabel(), convertToSimpleTree( branchGraph, root, startTime, endTime, treeToSpot ) );
		return stringTreeHashMap;
	}

	private static SimpleTree< Double > convertToSimpleTree( ModelBranchGraph branchGraph, BranchSpot spot, int startTime, int endTime, ObjectRefMap< Tree< Double >, Spot > treeToSpot )
	{
		double value = ( Math.min( endTime, spot.getTimepoint() ) - Math.max( startTime, spot.getFirstTimePoint() ) + 1 ) / ( double ) ( endTime - startTime + 1 );
		SimpleTree< Double > tree = new SimpleTree<>( value );
		Spot valueRef = treeToSpot.createValueRef();
		treeToSpot.put( tree, branchGraph.getFirstLinkedVertex( spot, valueRef ) );
		treeToSpot.releaseValueRef( valueRef );
		BranchSpot ref = branchGraph.vertexRef();
		for ( BranchLink link : spot.outgoingEdges() )
			tree.addChild( convertToSimpleTree( branchGraph, link.getTarget( ref ), startTime, endTime, treeToSpot ) );
		branchGraph.releaseRef( ref );
		return tree;
	}

	private static RefList< BranchSpot > findRootNodes( ModelBranchGraph branchGraph, int time, Set< String > commonLabels )
	{
		RefList< BranchSpot > roots = new RefArrayList<>( branchGraph.vertices().getRefPool() );
		for ( BranchSpot bs : branchGraph.vertices() )
		{
			boolean isValid = bs.getTimepoint() >= time && bs.getFirstTimePoint() <= time && commonLabels.contains( bs.getFirstLabel() ) && bs.outgoingEdges().size() > 1;
			if ( isValid )
				roots.add( bs );
		}
		return roots;
	}

	private static int startTime( ModelBranchGraph bg, Set< String > commonLabels )
	{
		int time = Integer.MAX_VALUE;
		for ( BranchSpot bs : bg.vertices() )
			if ( commonLabels.contains( bs.getFirstLabel() ) )
				time = Math.min( time, bs.getTimepoint() );
		return time;
	}

	private static Set< String > dividingBranchLabels( ModelBranchGraph bg )
	{
		Set< String > labels = new HashSet<>();
		for ( BranchSpot bs : bg.vertices() )
			if ( bs.outgoingEdges().size() > 1 )
				labels.add( bs.getFirstLabel() );
		return labels;
	}

	private static < T > Set< T > intersect( Set< T > set1, Set< T > set2 )
	{
		Set< T > intersection = new HashSet<>( set1 );
		intersection.retainAll( set2 );
		return intersection;
	}

	private static int firstTimepointWithAtLeast( Model firstModel, int cutoffNumber )
	{
		int[] count = countSpotsPerTimepoint( firstModel );
		for ( int t = 0; t < count.length; t++ )
			if ( cutoffNumber <= count[ t ] )
				return t;
		throw new NoSuchElementException( "No timepoint with at least " + cutoffNumber + " spots." );
	}

	private static int maximumNumberOfSpots( Model firstModel )
	{
		int[] spotCount = countSpotsPerTimepoint( firstModel );
		return Arrays.stream( spotCount ).max().orElse( 0 );
	}

	private static int[] countSpotsPerTimepoint( Model firstModel )
	{
		int numberOfTimepoints = numberOfTimepoints( firstModel );
		int[] spotCount = new int[ numberOfTimepoints ];
		for ( Spot spot : firstModel.getGraph().vertices() )
			spotCount[ spot.getTimepoint() ]++;
		return spotCount;
	}

	private static int numberOfTimepoints( Model firstModel )
	{
		int latestTimepoint = 0;
		for ( Spot spot : firstModel.getGraph().vertices() )
			latestTimepoint = Math.max( latestTimepoint, spot.getTimepoint() );
		return latestTimepoint + 1;
	}
}
