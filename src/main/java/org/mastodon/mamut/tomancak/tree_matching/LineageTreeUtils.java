package org.mastodon.mamut.tomancak.tree_matching;

import org.mastodon.collection.ObjectRefMap;
import org.mastodon.collection.RefSet;
import org.mastodon.collection.ref.ObjectRefHashMap;
import org.mastodon.collection.ref.RefSetImp;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.pool.PoolCollectionWrapper;

public class LineageTreeUtils
{
	public static RefSet<Spot> getRoots( ModelGraph graph ) {
		PoolCollectionWrapper<Spot> vertices = graph.vertices();
		RefSetImp<Spot> roots = new RefSetImp<>( vertices.getRefPool() );
		for( Spot spot : vertices )
			if(spot.incomingEdges().isEmpty())
				roots.add(spot);
		return roots;
	}

	public static ObjectRefMap<String, Spot> getRootsMap( ModelGraph graph )
	{
		PoolCollectionWrapper<Spot> vertices = graph.vertices();
		ObjectRefMap<String, Spot> roots = new ObjectRefHashMap<>( vertices.getRefPool() );
		for( Spot spot : vertices )
			if(spot.incomingEdges().isEmpty())
				roots.put(spot.getLabel(), spot);
		return roots;
	}

	public static Spot getDividingSpot( ModelGraph graph, final Spot spot )
	{
		Spot s = graph.vertexRef();
		s.refTo( spot );
		while(s.outgoingEdges().size() == 1) {
			s = s.outgoingEdges().get( 0 ).getTarget(s);
		}
		return s;
	}
}
