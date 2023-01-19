package org.mastodon.mamut.tomancak.tree_matching;

import java.io.File;
import java.io.IOException;

import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;

public class TreeMatching
{
	public static void showDialog( MamutAppModel appModel )
	{
		File otherProject = TreeMatchingDialog.showDialog();
		if( otherProject == null)
			return;
		Model model = openModel( otherProject );
		TreeMatchingAlgorithm.run( model, appModel.getModel() );
	}
	
	private static Model openModel( File file ) {
		try
		{
			MamutProject project = new MamutProjectIO().load( file.getAbsolutePath() );
			final Model model = new Model( project.getSpaceUnits(), project.getTimeUnits() );
			try (final MamutProject.ProjectReader reader = project.openForReading())
			{
				model.loadRaw( reader );
			}
			return model;
		}
		catch ( IOException e )
		{
			throw new RuntimeException( e );
		}
	}
}
