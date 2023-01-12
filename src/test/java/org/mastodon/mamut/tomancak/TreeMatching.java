package org.mastodon.mamut.tomancak;

import java.util.Set;

import net.imglib2.realtransform.AffineTransform3D;
import org.mastodon.collection.ObjectRefMap;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;

public class TreeMatching
{
	public MamutAppModel embryoA;

	public MamutAppModel embryoB;

	public ModelGraph graphA;

	public ModelGraph graphB;

	public ObjectRefMap<String, Spot> rootsA;

	public ObjectRefMap<String, Spot> rootsB;

	public Set<String> commonRootLabels;

	public AffineTransform3D transformAB;
}
