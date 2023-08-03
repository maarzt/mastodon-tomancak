package org.mastodon.mamut.tomancak.collaborative;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import org.junit.Test;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;

public class ModelGraphVerticesTest
{
	@Test
	public void testOrder()
	{
		ModelGraph graph = new ModelGraph();
		extracted( graph );
	}

	private static void extracted( ModelGraph graph )
	{
		graph.addVertex().init( 0, new double[ 3 ], 1 ).setLabel( "a" );
		graph.addVertex().init( 0, new double[ 3 ], 1 ).setLabel( "b" );
		graph.addVertex().init( 0, new double[ 3 ], 1 ).setLabel( "c" );
		Iterator< Spot > iterator = graph.vertices().iterator();
		assertEquals( "a", iterator.next().getLabel() );
		assertEquals( "b", iterator.next().getLabel() );
		assertEquals( "c", iterator.next().getLabel() );
	}

	@Test
	public void testOrderAfterClear()
	{
		ModelGraph graph = new ModelGraph();
		extracted( graph );
		graph.vertices().forEach( graph::remove );
		extracted( graph );
	}
}
