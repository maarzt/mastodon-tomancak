package org.mastodon.mamut.tomancak.lineage_registration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mastodon.collection.ObjectRefMap;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefRefHashMap;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;

/**
 * Pair the root spots in two ModelGraphs based on their label.
 */
public class RootsPairing
{
	/**
	 * Pair the root nodes in two ModelGraphs based on their label.
	 * A root node in graphA is paired with a root node in graphB
	 * if it has the same label.
	 * 
	 * @param graphA graph A
	 * @param graphB graph B   
	 * @return a map, that maps root nodes in graph A to equally named
	 * root nodes of graph B.
	 * 
	 */
	static RefRefMap< Spot, Spot > pairRoots( ModelGraph graphA, ModelGraph graphB )
	{
		ObjectRefMap< String, Spot > rootsA = LineageTreeUtils.getRootsMap( graphA );
		ObjectRefMap< String, Spot > rootsB = LineageTreeUtils.getRootsMap( graphB );
		Set< String > intersection = intersection( rootsA.keySet(), rootsB.keySet() );
		intersection.removeAll( getSolistNames( graphA, rootsA ) );
		intersection.removeAll( getSolistNames( graphB, rootsB ) );
		RefRefMap< Spot, Spot > roots = new RefRefHashMap<>( graphA.vertices().getRefPool(), graphB.vertices().getRefPool() );
		for( String label : intersection )
			roots.put( rootsA.get( label ), rootsB.get( label ) );
		return roots;
	}

	static List< String > getSolistNames( ModelGraph graph, ObjectRefMap< String, Spot > roots )
	{
		return roots.values().stream()
				.filter( spot -> ! LineageTreeUtils.doesBranchDivide( graph, spot ))
				.map( Spot::getLabel )
				.collect( Collectors.toList());
	}

	private static Set< String > intersection( Set< String > a, Set< String > b )
	{
		Set<String> intersection = new HashSet<>( a );
		intersection.retainAll( b );
		return intersection;
	}
}
