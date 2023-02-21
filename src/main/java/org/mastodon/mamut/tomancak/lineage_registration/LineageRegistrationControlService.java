package org.mastodon.mamut.tomancak.lineage_registration;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import net.imagej.ImageJService;

import org.mastodon.app.ui.ViewFrame;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.MamutViewBdv;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.tomancak.lineage_registration.copuling.ModelCoupling;
import org.mastodon.model.tag.TagSetStructure;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;

@Plugin( type = ImageJService.class )
public class LineageRegistrationControlService extends AbstractService implements ImageJService
{
	private final LineageRegistrationDialog dialog = new LineageRegistrationDialog( new Listener() );

	private final List< WindowManager > windowManagers = new ArrayList<>();

	public void registerMastodonInstance( WindowManager windowManager )
	{
		windowManagers.add( windowManager );
	}

	public void showDialog()
	{
		if ( dialog.isVisible() )
			return;
		dialog.setMastodonInstances( windowManagers );
		dialog.pack();
		dialog.setVisible( true );
	}

	private class Listener implements LineageRegistrationDialog.Listener
	{

		private ModelCoupling coupling = null;

		@Override
		public void onUpdateClicked()
		{
			dialog.setMastodonInstances( windowManagers );
		}

		@Override
		public void onImproveTitlesClicked()
		{
			for ( WindowManager windowManager : windowManagers )
				improveTitles( windowManager );
		}

		private void improveTitles( WindowManager windowManager )
		{
			// NB: kind of a hack and not as good as it should be.
			String projectName = LineageRegistrationDialog.getProjectName( windowManager );
			for ( MamutViewBdv window : windowManager.getBdvWindows() )
			{
				ViewFrame frame = window.getFrame();
				String title = frame.getTitle();
				frame.setTitle( title.split( " - ", 2 )[ 0 ] + " - " + projectName );
			}
		}

		@Override
		public void onSortTrackSchemeAClicked()
		{
			Model modelA = dialog.getProjectA().getAppModel().getModel();
			Model modelB = dialog.getProjectB().getAppModel().getModel();
			LineageRegistrationUtils.sortSecondTrackSchemeToMatch( modelB, modelA );
		}

		@Override
		public void onSortTrackSchemeBClicked()
		{
			Model modelA = dialog.getProjectA().getAppModel().getModel();
			Model modelB = dialog.getProjectB().getAppModel().getModel();
			LineageRegistrationUtils.sortSecondTrackSchemeToMatch( modelA, modelB );
		}

		@Override
		public void onColorLineagesClicked()
		{
			Model modelA = dialog.getProjectA().getAppModel().getModel();
			Model modelB = dialog.getProjectB().getAppModel().getModel();
			LineageColoring.tagLineages( modelA, modelB );
		}

		@Override
		public void onCopyTagSetAtoB()
		{
			copyTagSetFromTo( dialog.getProjectA(), dialog.getProjectB() );
		}

		@Override
		public void onCopyTagSetBtoA()
		{
			copyTagSetFromTo( dialog.getProjectB(), dialog.getProjectA() );
		}

		private void copyTagSetFromTo( WindowManager fromProject, WindowManager toProject )
		{
			Model fromModel = fromProject.getAppModel().getModel();
			Model toModel = toProject.getAppModel().getModel();

			List< TagSetStructure.TagSet > tagSets = fromModel.getTagSetModel().getTagSetStructure().getTagSets();
			if ( tagSets.isEmpty() )
			{
				JOptionPane.showMessageDialog( dialog,
						"No tag sets in project \"" + LineageRegistrationDialog.getProjectName( fromProject ) + "\"." );
				return;
			}

			TagSetStructure.TagSet tagSet = ComboBoxDialog.showComboBoxDialog( dialog,
					"Copy tag set to registered lineage",
					"Select tag set to copy:",
					tagSets,
					TagSetStructure.TagSet::getName );

			if ( tagSet == null )
				return;

			LineageRegistrationUtils.copyTagSetToSecond( fromModel, toModel, tagSet );
		}

		@Override
		public void onTagBothClicked()
		{
			putTags( true, true );
		}

		@Override
		public void onTagProjectAClicked()
		{
			putTags( true, false );
		}

		@Override
		public void onTagProjectBClicked()
		{
			putTags( false, true );
		}

		private void putTags( boolean modifyA, boolean modifyB )
		{
			Model modelA = dialog.getProjectA().getAppModel().getModel();
			Model modelB = dialog.getProjectB().getAppModel().getModel();
			LineageRegistrationUtils.tagCells( modelA, modelB, modifyA, modifyB );
		}

		@Override
		public void onSyncGroupClicked( int i )
		{
			if ( coupling != null )
				coupling.close();
			coupling = null;
			if ( i < 0 )
				return;
			MamutAppModel appModelA = dialog.getProjectA().getAppModel();
			MamutAppModel appModelB = dialog.getProjectB().getAppModel();
			RegisteredGraphs r = LineageRegistrationAlgorithm.run(
					appModelA.getModel().getGraph(),
					appModelB.getModel().getGraph() );
			coupling = new ModelCoupling( appModelA, appModelB, r, i );
		}
	}
}
