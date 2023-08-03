package org.mastodon.mamut.tomancak.collaborative;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.BiConsumer;

import org.mastodon.Ref;
import org.mastodon.collection.RefCollection;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.ModelSerializer;
import org.mastodon.mamut.model.Spot;
import org.mastodon.pool.PoolObjectAttributeSerializer;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

public class GitFormatSerializer
{
	public static void serialize( ModelGraph graph, File folder )
	{
		try
		{
			Path path = folder.toPath();
			TIntIntMap spotIdToFileId = writeSpots( graph, path );
			writeLinks( graph, path, spotIdToFileId );
		}
		catch ( IOException e )
		{
			throw new RuntimeException( e );
		}
	}

	private static TIntIntMap writeSpots( ModelGraph graph, Path path ) throws IOException
	{
		PoolObjectAttributeSerializer< Spot > vio = ModelSerializer.getInstance().getVertexSerializer();
		byte[] bytes = new byte[ vio.getNumBytes() ];
		return writeChunked( path, "spots_", graph.vertices(), ( os, spot ) -> {
			try
			{
				vio.getBytes( spot, bytes );
				os.write( bytes );
			}
			catch ( IOException e )
			{
				throw new RuntimeException( e );
			}
		} );
	}

	private static void writeLinks( ModelGraph graph, Path path, TIntIntMap idmap ) throws IOException
	{
		Spot ref = graph.vertexRef();
		writeChunked( path, "links_", graph.edges(), ( os, link ) -> {
			try
			{
				os.writeInt( idmap.get( link.getSource( ref ).getInternalPoolIndex() ) );
				os.writeInt( idmap.get( link.getTarget( ref ).getInternalPoolIndex() ) );
			}
			catch ( IOException e )
			{
				throw new RuntimeException( e );
			}
		} );
	}

	private static < T extends Ref< T > > TIntIntMap writeChunked( Path path, String prefix, RefCollection< T > objects, BiConsumer< ObjectOutputStream, T > writeEntry ) throws IOException
	{
		TIntIntMap objectIdMap = new TIntIntHashMap( objects.size() * 2, 0.75f, -1, -1 );
		Iterator< T > iterator = objects.iterator();
		int i = 0;
		while ( iterator.hasNext() )
		{
			try (ObjectOutputStream os = new ObjectOutputStream( Files.newOutputStream( path.resolve( prefix + i + ".raw" ) ) ))
			{
				for ( int j = 0; j < 10_000; j++ )
				{
					if ( !iterator.hasNext() )
						break;
					T t = iterator.next();
					writeEntry.accept( os, t );
					objectIdMap.put( t.getInternalPoolIndex(), j );
					i++;
				}
			}
		}
		return objectIdMap;
	}

}
