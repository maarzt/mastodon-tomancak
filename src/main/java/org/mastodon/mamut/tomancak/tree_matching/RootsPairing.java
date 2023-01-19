package org.mastodon.mamut.tomancak.tree_matching;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.mastodon.collection.ObjectRefMap;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefRefHashMap;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;

public class RootsPairing
{
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
