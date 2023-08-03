package org.mastodon.mamut.tomancak.collaborative;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.BiConsumer;

import org.mastodon.collection.RefCollection;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.ref.RefIntHashMap;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.tomancak.lineage_registration.RefCollectionUtils;

public class GitFormatSerializer
{
	public static void serialize( ModelGraph graph, File folder )
	{
		try
		{
			Path path = folder.toPath();
			RefIntMap< Spot > idmap = writeSpots( graph, path );
			writeLinks( graph, path, idmap );
		}
		catch ( IOException e )
		{
			throw new RuntimeException( e );
		}
	}

	private static RefIntMap< Spot > writeSpots( ModelGraph graph, Path path ) throws IOException
	{
		double[][] cov = new double[ 3 ][ 3 ];
		return writeChunked( path, "spots_", graph.vertices(), ( os, spot ) -> {
			try
			{
				os.writeInt( spot.getTimepoint() );
				os.writeUTF( spot.getLabel() );
				os.writeDouble( spot.getDoublePosition( 0 ) );
				os.writeDouble( spot.getDoublePosition( 1 ) );
				os.writeDouble( spot.getDoublePosition( 2 ) );
				writeCovariance( os, spot, cov );
			}
			catch ( IOException e )
			{
				throw new RuntimeException( e );
			}
		} );
	}

	private static void writeLinks( ModelGraph graph, Path path, RefIntMap< Spot > idmap ) throws IOException
	{
		Spot ref = graph.vertexRef();
		writeChunked( path, "links_", graph.edges(), ( os, link ) -> {
			try
			{
				os.writeInt( idmap.get( link.getSource( ref ) ) );
				os.writeInt( idmap.get( link.getTarget( ref ) ) );
			}
			catch ( IOException e )
			{
				throw new RuntimeException( e );
			}
		} );
	}

	private static < T > RefIntHashMap< T > writeChunked( Path path, String prefix, RefCollection< T > objects, BiConsumer< DataOutputStream, T > writeEntry ) throws IOException
	{
		RefIntHashMap< T > objectIdMap = new RefIntHashMap<>( RefCollectionUtils.getRefPool( objects ), -1, objects.size() );
		Iterator< T > iterator = objects.iterator();
		int i = 0;
		while ( iterator.hasNext() )
		{
			try (DataOutputStream os = new DataOutputStream( Files.newOutputStream( path.resolve( prefix + i + ".raw" ) ) ))
			{
				for ( int j = 0; j < 10_000; j++ )
				{
					if ( !iterator.hasNext() )
						break;
					T t = iterator.next();
					writeEntry.accept( os, t );
					objectIdMap.put( t, j );
					i++;
				}
			}
		}
		return objectIdMap;
	}

	private static void writeCovariance( DataOutputStream os, Spot spot, double[][] cov ) throws IOException
	{
		spot.getCovariance( cov );
		for ( int i = 0; i < 3; i++ )
			for ( int j = 0; j < 3; j++ )
				os.writeDouble( cov[ i ][ j ] );
	}
}
