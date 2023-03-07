package org.mastodon.mamut.tomancak;

import java.io.File;
import java.io.IOException;

import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.scijava.Context;

import mpicbg.spim.data.SpimDataException;

public class MirrorEmbryo
{
	private static String input = "/home/arzt/Datasets/DeepLineage/Johannes-2023-03-06/2020-07-23_Ml_NL20-H2B-oGFP_TrackingData_from4-cells/2020-07-23_Ml_NL20-H2B-oGFP_subbg_2022-10-30_sorted_1a2-flipped.mastodon";

	private static String output = "/home/arzt/Datasets/DeepLineage/Johannes-2023-03-06/2020-07-23_Ml_NL20-H2B-oGFP_TrackingData_from4-cells/2020-07-23_Ml_NL20-H2B-oGFP_subbg_2023-03-07_mirrored.mastodon";

	public static void main(String... args) throws IOException, SpimDataException
	{
		try( Context context = new Context() ) {
			WindowManager windowManager = new WindowManager( context );
			MamutProject project = new MamutProjectIO().load( input );
			windowManager.getProjectManager().open( project, false, true );
			ModelGraph graph = windowManager.getAppModel().getModel().getGraph();
			mirrorX( graph );
			windowManager.getProjectManager().saveProject( new File( output ) );
		}
	}

	private static void mirrorX( ModelGraph graph )
	{
		double meanX = getMeanX( graph );
		for ( Spot spot : graph.vertices() )
			spot.setPosition( 2 * meanX - spot.getDoublePosition( 0 ), 0 );
	}

	private static double getMeanX( ModelGraph graph )
	{
		double sumX = 0;
		long count = 0;
		for ( Spot spot : graph.vertices() ) {
			sumX += spot.getDoublePosition( 0 );
			count++;
		}
		return sumX / count;
	}
}
