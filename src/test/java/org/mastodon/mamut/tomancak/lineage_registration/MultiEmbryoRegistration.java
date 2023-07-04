package org.mastodon.mamut.tomancak.lineage_registration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefIntHashMap;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.tomancak.lineage_registration.spatial_registration.SpatialRegistrationMethod;
import org.mastodon.model.tag.ObjTagMap;
import org.mastodon.model.tag.TagSetStructure;
import org.scijava.Context;

/**
 * Do pairwise {@link LineageRegistrationAlgorithm lineage registration} of multiple embryos.
 * Tag the cells that are uniquely identified by the registration, without contradiction.
 */
public class MultiEmbryoRegistration
{
	public static void main( String... args )
	{
		List< String > projectPaths = Arrays.asList(
//				LineageRegistrationDemo.project1,
//				LineageRegistrationDemo.project2,
//				LineageRegistrationDemo.project3
				LineageRegistrationDemo.Ml_2020_07_23_MIRRORED,
				LineageRegistrationDemo.Ml_2022_01_27,
				LineageRegistrationDemo.Ml_2022_05_03,
				//LineageRegistrationDemo.Ml_2020_08_03 // Cell are unusually positioned in 4 cell stage -> bad for registration.
				LineageRegistrationDemo.Ml_2022_07_27
				//LineageRegistrationDemo.Ml_2022_09_08 // Was treated specially for EM image acquisition and shows differences in development.
		);
		try (final Context context = new Context())
		{
			List< WindowManager > windowManagers = projectPaths.stream().map( path -> LineageRegistrationDemo.openAppModel( context, path ) ).collect( Collectors.toList() );
			List< Model > models = windowManagers.stream().map( wm -> wm.getAppModel().getModel() ).collect( Collectors.toList() );
			models.forEach( model -> ImproveAnglesDemo.removeBackEdges( model.getGraph() ) );
			createTags( models.get( 0 ), computeAgreement( models ) );
			windowManagers.get( 0 ).createBranchTrackScheme();
		}
	}

	private static RefIntMap< Spot > computeAgreement( List< Model > models )
	{
		Map< Pair< Model, Model >, RefRefMap< Spot, Spot > > registrations = new HashMap<>();
		for ( Pair< Model, Model > pair : makePairs( models ) )
			registrations.put( pair, register( pair.getLeft(), pair.getRight() ) );

		Model firstModel = models.get( 0 );
		RefIntMap< Spot > agreement = new RefIntHashMap<>( firstModel.getGraph().vertices().getRefPool(), 0 );

		List< Model > otherModels = models.subList( 1, models.size() );
		for ( Pair< Model, Model > pair : makePairs( otherModels ) )
		{
			Model otherModelA = pair.getLeft();
			Model otherModelB = pair.getRight();
			RefRefMap< Spot, Spot > registrationA = registrations.get( Pair.of( firstModel, otherModelA ) );
			RefRefMap< Spot, Spot > registrationB = registrations.get( Pair.of( firstModel, otherModelB ) );
			RefRefMap< Spot, Spot > registrationAB = registrations.get( pair );
			incrementAgreement( agreement, registrationA, registrationB, registrationAB );
		}

		return agreement;
	}

	private static void incrementAgreement( RefIntMap< Spot > agreement, RefRefMap< Spot, Spot > registrationA, RefRefMap< Spot, Spot > registrationB, RefRefMap< Spot, Spot > registrationAB )
	{
		Spot refA = registrationA.createValueRef();
		Spot refB = registrationB.createValueRef();
		Spot refB2 = registrationAB.createValueRef();
		for ( Spot key : registrationA.keySet() )
			if ( registrationB.containsKey( key ) )
			{
				Spot spotA = registrationA.get( key, refA );
				Spot spotB = registrationB.get( key, refB );
				Spot crossSpotB = registrationAB.get( spotA, refB2 );
				boolean correct = spotB.equals( crossSpotB );
				if ( correct )
					agreement.put( key, agreement.get( key ) + 1 );
			}
	}

	private static RefRefMap< Spot, Spot > register( Model firstModel, Model otherModel )
	{
		return LineageRegistrationAlgorithm.run( firstModel, 0, otherModel, 0, SpatialRegistrationMethod.DYNAMIC_ROOTS ).mapAB;
	}

	private static List< Pair< Model, Model > > makePairs( List< Model > otherModels )
	{
		List< Pair< Model, Model > > spokes = new ArrayList<>();
		for ( int i = 0; i < otherModels.size(); i++ )
			for ( int j = i + 1; j < otherModels.size(); j++ )
				spokes.add( Pair.of( otherModels.get( i ), otherModels.get( j ) ) );
		return spokes;
	}

	private static void createTags( Model firstModel, RefIntMap< Spot > agreement )
	{
		int max = 0;
		for ( Spot spot : agreement.keySet() )
			max = Math.max( max, agreement.get( spot ) );
		List< Pair< String, Integer > > correct =
				IntStream.rangeClosed( 0, max ).mapToObj( i -> Pair.of( String.valueOf( i ), Glasbey.GLASBEY[ i + 1 ] ) ).collect( Collectors.toList() );

		TagSetStructure.TagSet tagSet = TagSetUtils.addNewTagSetToModel( firstModel, "UnifiedEmbryo", correct );
		List< TagSetStructure.Tag > tags = tagSet.getTags();
		ObjTagMap< Link, TagSetStructure.Tag > edgeTags = firstModel.getTagSetModel().getEdgeTags().tags( tagSet );
		for ( Spot spot : agreement.keySet() )
		{
			int value = agreement.get( spot );
			TagSetStructure.Tag tag = tags.get( max - value );
			TagSetUtils.tagBranch( firstModel, tagSet, tag, spot );
			if ( spot.incomingEdges().size() == 1 )
			{
				Link link = spot.incomingEdges().iterator().next();
				edgeTags.set( link, tag );
			}
		}
	}
}
