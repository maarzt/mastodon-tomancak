package org.mastodon.mamut.tomancak.tree_matching;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.mastodon.collection.ObjectRefMap;
import org.mastodon.graph.algorithm.traversal.DepthFirstSearch;
import org.mastodon.graph.algorithm.traversal.GraphSearch;
import org.mastodon.graph.algorithm.traversal.SearchListener;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.model.tag.ObjTagMap;
import org.mastodon.model.tag.TagSetModel;
import org.mastodon.model.tag.TagSetStructure;

public class LineageColoring
{
	static void tagLineages( GraphPair m )
	{
		Map<String, Integer> colorMap = createColorMap( m.commonRootLabels );
		tagLineages( colorMap, m.rootsA, m.embryoA.getModel() );
		tagLineages( colorMap, m.rootsB, m.embryoB.getModel() );
	}

	private static void tagLineages( Map<String, Integer> colorMap, ObjectRefMap<String, Spot > roots, Model model )
	{
		TagSetStructure.TagSet tagSet = createTagSet( model, colorMap );
		for(TagSetStructure.Tag tag : tagSet.getTags()) {
			tagLineage( model, roots.get(tag.label()), tagSet, tag );
		}
	}

	private static void tagLineage( Model model, Spot root, TagSetStructure.TagSet tagSet, TagSetStructure.Tag tag )
	{
		ObjTagMap<Spot, TagSetStructure.Tag> spotTags = model.getTagSetModel().getVertexTags().tags( tagSet );
		ObjTagMap< Link, TagSetStructure.Tag> edgeTags = model.getTagSetModel().getEdgeTags().tags( tagSet );

		SearchListener<Spot, Link, DepthFirstSearch<Spot, Link> > searchListener = new SearchListener<Spot, Link, DepthFirstSearch<Spot, Link>>()
		{
			@Override
			public void processVertexLate( Spot spot, DepthFirstSearch<Spot, Link> search )
			{
				// do nothing
			}

			@Override
			public void processVertexEarly( Spot spot, DepthFirstSearch<Spot, Link> spotLinkDepthFirstSearch )
			{
				spotTags.set( spot, tag );
			}

			@Override
			public void processEdge( Link link, Spot spot, Spot v1, DepthFirstSearch<Spot, Link> spotLinkDepthFirstSearch )
			{
				edgeTags.set( link, tag );
			}

			@Override
			public void crossComponent( Spot spot, Spot v1, DepthFirstSearch<Spot, Link> spotLinkDepthFirstSearch )
			{
				// do nothing
			}
		};

		DepthFirstSearch<Spot, Link> search = new DepthFirstSearch<>( model.getGraph(), GraphSearch.SearchDirection.DIRECTED );
		search.setTraversalListener( searchListener );
		search.start( root );
	}

	private static TagSetStructure.TagSet createTagSet( Model model, Map<String, Integer> tagsAndColors )
	{
		TagSetModel<Spot, Link> tagSetModel = model.getTagSetModel();
		TagSetStructure tss = copy( tagSetModel.getTagSetStructure() );
		TagSetStructure.TagSet tagSet = tss.createTagSet("lineages");
		tagsAndColors.forEach( tagSet::createTag );
		tagSetModel.setTagSetStructure( tss );
		return tagSet;
	}

	private static TagSetStructure copy( TagSetStructure original )
	{
		TagSetStructure copy = new TagSetStructure();
		copy.set( original );
		return copy;
	}

	@NotNull
	private static Map<String, Integer> createColorMap( Set<String> labels )
	{
		Map<String, Integer> colors = new HashMap<>();
		int count = 4;
		for(String label : labels )
			colors.put( label, Glasbey.GLASBEY[ count++ ] );
		return colors;
	}
}
