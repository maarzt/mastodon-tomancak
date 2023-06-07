package org.mastodon.mamut.tomancak.lineage_registration;

import org.mockito.Mockito;

public class LineageRegistrationFrameDemo
{
	public static void main( String... args )
	{
		// NOTE: Small demo function that only shows the LineageRegistrationDialog. For easy debugging.
		LineageRegistrationFrame.Listener dummyListener = Mockito.mock( LineageRegistrationFrame.Listener.class );
		LineageRegistrationFrame dialog = new LineageRegistrationFrame( dummyListener );
		dialog.pack();
		dialog.setVisible( true );
	}

}
