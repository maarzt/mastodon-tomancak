/*-
 * #%L
 * mastodon-tomancak
 * %%
 * Copyright (C) 2018 - 2022 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.mamut.tomancak.sort_tree;

import net.imglib2.RealLocalizable;
import org.mastodon.collection.ref.RefArrayList;
import org.mastodon.graph.ref.OutgoingEdges;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Methods for sorting the lineage tree in a {@link ModelGraph}.
 * <p>
 * The order of the outgoing edges of all nodes are changed such that
 * the child that is closer to the "left anchor" is first and the child closer
 * to the "right anchor" is second.
 *
 * @author Matthias Arzt
 */
public class SortTree
{

	public static void sort( Model model, Collection<Spot> vertices, Collection<Spot> leftAnchors, Collection<Spot> rightAnchors )
	{
		ReentrantReadWriteLock lock = model.getGraph().getLock();
		lock.writeLock().lock();
		try
		{
			int numberOfTimePoints = getNumberOfTimePoints( model.getGraph() );
			// calculate directions
			List<double[]> left = calculateAndInterpolateAveragePosition( numberOfTimePoints, leftAnchors );
			List<double[]> right = calculateAndInterpolateAveragePosition( numberOfTimePoints, rightAnchors );
			List<double[]> directions = subtract( right, left );
			// find vertices to be flipped
			Collection<Spot> toBeFlipped = findSpotsToBeFlipped( model.getGraph(), vertices, directions );
			// flip vertices
			FlipDescendants.flipDescendants( model, toBeFlipped );
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	public static List<double[]> calculateAndInterpolateAveragePosition( int numTimePoint, Collection<Spot> spots )
	{
		if(spots.isEmpty())
			throw new NoSuchElementException();
		List<double[]> averages = calculateAveragePosition( numTimePoint, spots );
		fillStartAndEnd(averages);
		interpolateGaps(averages);
		return averages;
	}

	private static List<double[]> calculateAveragePosition( int numTimePoint, Collection<Spot> taggedSpots )
	{
		List<double[]> averages = new ArrayList<>( Collections.nCopies( numTimePoint, null ) );
		int[] counts = new int[ numTimePoint ];
		Collections.fill(averages, null);
		for(Spot spot : taggedSpots )
		{
			int timepoint = spot.getTimepoint();
			counts[timepoint]++;
			double[] average = getOrCreateEntry( averages, timepoint );
			add( average, spot );
		}
		for ( int i = 0; i < numTimePoint; i++ )
		{
			double[] average = averages.get( i );
			if(average != null)
				divide( average, counts[i] );
		}
		return averages;
	}

	private static double[] getOrCreateEntry( List<double[]> averages, int timepoint )
	{
		double[] average = averages.get( timepoint );
		if(average == null) {
			average = new double[3];
			averages.set( timepoint, average );
		}
		return average;
	}

	private static void fillStartAndEnd( List<double[]> averages )
	{
		int firstIndex = findFirstNonNullIndex( averages );
		fill( averages, 0, firstIndex - 1, averages.get( firstIndex ) );
		int lastIndex = findLastNonNullIndex( averages );
		fill( averages, lastIndex + 1, averages.size() - 1,  averages.get(lastIndex) );
	}

	private static void interpolateGaps( List<double[]> averages )
	{
		int startIndex = 0;
		while(true)
		{
			int beforeGap = findNextNullEntry( averages, startIndex + 1 ) - 1;
			if(beforeGap < 0)
				return;
			int afterGap = findNextNonNullEntry( averages, beforeGap + 2 );
			double[] before = averages.get( beforeGap);
			double[] after = averages.get( afterGap );
			for ( int i = beforeGap + 1; i <= afterGap - 1; i++ )
				averages.set(i, interpolate(before, after, (double) (i - beforeGap) / (afterGap - beforeGap)));
			startIndex = afterGap;
		}
	}

	private static double[] interpolate( double[] a, double[] b, double weight )
	{
		double[] result = new double[ a.length];
		Arrays.setAll( result, i -> a[i] * (1 - weight) + b[i] * weight );
		return result;
	}

	private static int findNextNullEntry( List<double[]> averages, final int startIndex )
	{
		for ( int i = startIndex; i < averages.size(); i++ )
			if ( averages.get( i ) == null )
				return i;
		return -1;
	}

	private static int findNextNonNullEntry( List<double[]> averages, final int startIndex )
	{
		for ( int i = startIndex; i < averages.size(); i++ )
			if ( averages.get( i ) != null )
				return i;
		return -1;
	}

	private static int findFirstNonNullIndex( List<double[]> averages )
	{
		for( int i = 0; i < averages.size(); i++)
			if( averages.get(i) != null)
				return i;
		throw new NoSuchElementException();
	}

	private static int findLastNonNullIndex( List<double[]> averages )
	{
		for( int i = averages.size() - 1; i >= 0; i--)
			if( averages.get(i) != null)
				return i;
		throw new NoSuchElementException();
	}

	private static void fill( List<double[]> averages, int fromIndex, int toIndex, double[] value )
	{
		for ( int i = fromIndex; i <= toIndex; i++ )
			averages.set( i, value );
	}

	private static void add( double[] average, RealLocalizable spot )
	{
		for ( int i = 0; i < average.length; i++ )
			average[ i ] += spot.getDoublePosition( i );
	}

	private static void divide( double[] average, int size )
	{
		for ( int i = 0; i < average.length; i++ )
			average[ i ] /= size;
	}

	public static Collection<Spot> findSpotsToBeFlipped( ModelGraph graph, Collection<Spot> selection, List<double[]> sortingDirections )
	{
		RefArrayList<Spot> result = new RefArrayList<>( graph.vertices().getRefPool() );
		for(Spot spot : selection)
			if( isSortingWrong(graph, spot, sortingDirections.get(spot.getTimepoint())) )
				result.add(spot);
		return result;
	}

	private static boolean isSortingWrong( ModelGraph graph, Spot spot, double[] sortingDirection )
	{
		if(spot.outgoingEdges().size() != 2)
			return false;
		double[] divisionDirection = directionOfCellDevision( graph, spot );
		return scalarProduct(sortingDirection, divisionDirection) < 0;
	}

	public static double[] directionOfCellDevision( ModelGraph graph, Spot spot )
	{
		if(spot.outgoingEdges().size() != 2)
			return new double[]{ 0, 0, 0 };
		OutgoingEdges<Link>.OutgoingEdgesIterator iterator = spot.outgoingEdges().iterator();
		Spot childA = iterator.next().getTarget();
		Spot childB = iterator.next().getTarget();
		double[] direction = subtract( childB, childA );
		graph.releaseRef( childA );
		graph.releaseRef( childB );
		return direction;
	}

	private static double[] subtract( RealLocalizable a, RealLocalizable b )
	{
		double[] direction = new double[ 3 ];
		for ( int i = 0; i < 3; i++ )
			direction[ i ] = a.getDoublePosition( i ) - b.getDoublePosition( i );
		return direction;
	}

	private static double scalarProduct( double[] a, double[] b )
	{
		assert a.length == b.length;
		double sum = 0;
		for ( int i = 0; i < a.length; i++ )
			sum += a[ i ] * b[ i ];
		return sum;
	}

	private static List<double[]> subtract( List<double[]> a, List<double[]> b )
	{
		final int n = a.size();
		assert n == b.size();
		final List<double[]> result = new ArrayList<>(n);
		for ( int i = 0; i < n; i++ )
		{
			result.add( subtract( a.get(i), b.get(i) )	);
		}
		return result;
	}

	private static double[] subtract( double[] a, double[] b )
	{
		double[] direction = new double[ 3 ];
		for ( int i = 0; i < 3; i++ )
			direction[ i ] = a[ i ] - b[ i ];
		return direction;
	}


	private static int getNumberOfTimePoints( ModelGraph graph )
	{
		int max = -1;
		for(Spot spot : graph.vertices())
			max = Math.max( max, spot.getTimepoint() );
		return max + 1;
	}
}
