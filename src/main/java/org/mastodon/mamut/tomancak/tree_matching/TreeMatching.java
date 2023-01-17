package org.mastodon.mamut.tomancak.tree_matching;

import java.io.File;

import javax.swing.JOptionPane;

import org.mastodon.mamut.MamutAppModel;

public class TreeMatching
{
	public static void showDialog( MamutAppModel appModel )
	{
		File otherProject = TreeMatchingDialog.showDialog();
	}
}
