package org.mastodon.mamut.tomancak.lineage_registration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.project.MamutProject;

public class LineageRegistrationDialog extends JDialog
{
	private final Listener listener;

	private final JComboBox< MastodonInstance > comboBoxA = new JComboBox<>();

	private final JComboBox< MastodonInstance > comboBoxB = new JComboBox<>();

	private final List< JToggleButton > syncGroupButtons;

	public LineageRegistrationDialog( Listener listener )
	{
		super( ( JFrame ) null, "Lineage Registration Across Two Mastodon Projects", false );
		this.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
		this.setLocationByPlatform( true );
		this.listener = listener;
		setLayout( new MigLayout( "insets dialog, fill" ) );

		add( introductionTextPane(), "span, grow, wrap, width 0:0:" );
		add( new JLabel( "Select Mastodon projects to match:" ), "span, wrap" );
		add( new JLabel( "project A:" ) );
		add( comboBoxA, "grow, wrap" );
		add( new JLabel( "project B:" ) );
		add( comboBoxB, "grow, wrap" );
		add( newButton( "update", listener::onUpdateClicked ), "skip, wrap" );

		add( new JLabel( "Sort TrackScheme:" ), "gaptop unrelated" );
		add( newButton( "project A", listener::onSortTrackSchemeAClicked ), "split 2" );
		add( newButton( "project B", listener::onSortTrackSchemeBClicked ), "wrap" );
		add( new JLabel( "Copy tag set:" ) );
		add( newButton( "from A to B", listener::onCopyTagSetAtoB ), "split 2" );
		add( newButton( "from B to A", listener::onCopyTagSetBtoA ), "wrap" );
		add( new JLabel( "Tag unmatched & flipped cells:" ) );
		add( newButton( "in both projects", listener::onTagBothClicked ), "split 3" );
		add( newButton( "project A", listener::onTagProjectAClicked ) );
		add( newButton( "project B", listener::onTagProjectBClicked ), "wrap" );
		add( new JLabel( "Others:" ) );
		add( newButton( "color paired lineages", listener::onColorLineagesClicked ), "wrap" );
		add( new JLabel( "Couple projects:" ) );
		this.syncGroupButtons = initSyncGroupButtons();
		add( syncGroupButtons.get( 0 ), "split 3" );
		add( syncGroupButtons.get( 1 ) );
		add( syncGroupButtons.get( 2 ), "wrap" );
		add( newButton( "Close", this::onCloseClicked ), "gaptop unrelated, span, align right" );
	}

	private JButton newButton( String select, Runnable action )
	{
		JButton button = new JButton( select );
		button.addActionListener( ignored -> action.run() );
		return button;
	}

	private static JTextPane introductionTextPane()
	{
		final String introText = "<html><body>"
				+ "The \"lineage registration\" plugin allows to compare the lineages of two "
				+ "similarly developing embryos in two mastodon projects. By analyzing the "
				+ "spindle directions it recursively finds the corresponding cells in both "
				+ "embryos.<br><br>"
				+ "<details>"
				+ "The plugin allows to perform various operations baseed on the correspondence "
				+ "information.<br><br>"
				+ "The following condition need to be met for the algorithm to work:"
				+ "<ul>"
				+ "<li>Both projects should show stereotypically developing embryos.</li>"
				+ "<li>The first frames should show the both embryos at a similar stage.</li>"
				+ "<li>Root nodes must be labeled, and the labels should match between the two projects.</li>"
				+ "<li>There need to be at least three lineages with cell divisions,"
				+ "that can be paired based on their name.</li>"
				+ "</ul>"
				+ "(The plugin ignores lineages that have no cell division.)<br><br>"
				+ "</details>"
				+ "</body></html>";
		JTextPane comp = new JTextPane();
		comp.setContentType( "text/html" );
		comp.setText( introText );
		comp.setEditable( false );
		return comp;
	}

	private List< JToggleButton > initSyncGroupButtons()
	{
		ArrayList< JToggleButton > buttons = new ArrayList<>();
		for ( int i = 0; i < 3; i++ )
		{
			JToggleButton button = new JToggleButton( "Lock " + ( i + 1 ) );
			int j = i;
			button.addActionListener( ignore -> onSyncGroupButtonClicked( j ) );
			buttons.add( button );
		}
		return buttons;
	}

	private void onSyncGroupButtonClicked( int i )
	{
		for ( int j = 0; j < syncGroupButtons.size(); j++ )
			if ( i != j )
				syncGroupButtons.get( j ).setSelected( false );
		boolean isSelected = syncGroupButtons.get( i ).isSelected();
		listener.onSyncGroupClicked( isSelected ? i : -1 );
	}

	private void onCloseClicked()
	{
		dispose();
	}

	public void setMastodonInstances( List< WindowManager > instances )
	{
		WindowManager a = getProjectA();
		WindowManager b = getProjectB();
		comboBoxA.removeAllItems();
		comboBoxB.removeAllItems();
		for ( WindowManager windowManager : instances )
		{
			MastodonInstance mastodonInstance = new MastodonInstance( windowManager );
			comboBoxA.addItem( mastodonInstance );
			comboBoxB.addItem( mastodonInstance );
		}
		setSelected( comboBoxA, a, 0 );
		setSelected( comboBoxB, b, 1 );
	}

	private void setSelected( JComboBox< MastodonInstance > comboBox, WindowManager windowManager, int defaultIndex )
	{
		for ( int i = 0; i < comboBox.getItemCount(); i++ )
			if ( comboBox.getItemAt( i ).windowManager == windowManager )
			{
				comboBox.setSelectedIndex( i );
				return;
			}
		if ( defaultIndex < comboBox.getItemCount() )
			comboBox.setSelectedIndex( defaultIndex );
	}

	public WindowManager getProjectA()
	{
		return getSelected( comboBoxA );
	}

	public WindowManager getProjectB()
	{
		return getSelected( comboBoxB );
	}

	private WindowManager getSelected( JComboBox< MastodonInstance > comboBoxA )
	{
		Object selectedItem = comboBoxA.getSelectedItem();
		if ( selectedItem == null )
			return null;
		return ( ( MastodonInstance ) selectedItem ).windowManager;
	}

	private static class MastodonInstance
	{
		private final WindowManager windowManager;

		private MastodonInstance( WindowManager windowManager )
		{
			this.windowManager = windowManager;
		}

		@Override
		public String toString()
		{
			return getProjectName( windowManager );
		}
	}

	public static String getProjectName( WindowManager windowManager )
	{
		MamutProject project = windowManager.getProjectManager().getProject();
		if ( project == null )
			return windowManager.toString();
		File projectRoot = project.getProjectRoot();
		return FilenameUtils.getBaseName( projectRoot.getName() );
	}

	public interface Listener
	{

		void onUpdateClicked();

		void onSortTrackSchemeAClicked();

		void onSortTrackSchemeBClicked();

		void onColorLineagesClicked();

		void onCopyTagSetAtoB();

		void onCopyTagSetBtoA();

		void onTagBothClicked();

		void onTagProjectAClicked();

		void onTagProjectBClicked();

		void onSyncGroupClicked( int i );
	}

	private static class DummyListener implements Listener
	{

		@Override
		public void onUpdateClicked()
		{

		}

		@Override
		public void onSortTrackSchemeAClicked()
		{

		}

		@Override
		public void onSortTrackSchemeBClicked()
		{

		}

		@Override
		public void onColorLineagesClicked()
		{

		}

		@Override
		public void onCopyTagSetAtoB()
		{

		}

		@Override
		public void onCopyTagSetBtoA()
		{

		}

		@Override
		public void onTagBothClicked()
		{

		}

		@Override
		public void onTagProjectAClicked()
		{

		}

		@Override
		public void onTagProjectBClicked()
		{

		}

		@Override
		public void onSyncGroupClicked( int i )
		{

		}
	}

	public static void main( String... args )
	{
		// NOTE: Small demo function that only shows the LineageRegistrationDialog. For easy debugging.
		LineageRegistrationDialog dialog = new LineageRegistrationDialog( new DummyListener() );
		dialog.pack();
		dialog.setVisible( true );
	}

}
