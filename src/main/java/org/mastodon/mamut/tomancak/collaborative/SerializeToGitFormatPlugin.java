package org.mastodon.mamut.tomancak.collaborative;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.plugin.MamutPlugin;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.mamut.tomancak.lineage_registration.LineageRegistrationControlService;
import org.mastodon.mamut.tomancak.lineage_registration.LineageRegistrationFrame;
import org.mastodon.ui.keymap.CommandDescriptionProvider;
import org.mastodon.ui.keymap.CommandDescriptions;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.RunnableAction;

/**
 * A plugin that registers the cell lineages of two stereotypically developing embryos.
 * <p>
 * The plugin interacts with the {@link LineageRegistrationControlService} to
 * register and unregister the {@link MamutAppModel}
 * and to show the {@link LineageRegistrationFrame}.
 */
@Plugin( type = MamutPlugin.class )
public class SerializeToGitFormatPlugin implements MamutPlugin
{

	private static final String SERIALIZE_ID = "[tomancak] serialize";

	private static final String[] SERIALIZE_KEYS = { "not mapped" };

	private static final Map< String, String > menuTexts =
			Collections.singletonMap( SERIALIZE_ID, "Export ModelGraph to Git Repo" );

	private MamutPluginAppModel pluginAppModel;

	@Plugin( type = CommandDescriptionProvider.class )
	public static class Descriptions extends CommandDescriptionProvider
	{
		public Descriptions()
		{
			super( KeyConfigContexts.TRACKSCHEME, KeyConfigContexts.BIGDATAVIEWER );
		}

		@Override
		public void getCommandDescriptions( CommandDescriptions descriptions )
		{
			descriptions.add( SERIALIZE_ID, SERIALIZE_KEYS, "Save the model graph to disc in a GIT friendly manner." );
		}
	}

	private final AbstractNamedAction serializeAction;

	public SerializeToGitFormatPlugin()
	{
		serializeAction = new RunnableAction( SERIALIZE_ID, this::run );
	}

	@Override
	public void setAppPluginModel( MamutPluginAppModel model )
	{
		this.pluginAppModel = model;
	}

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		return Collections.singletonList( menu( "Plugins", item( SERIALIZE_ID ) ) );
	}

	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}

	@Override
	public void installGlobalActions( Actions actions )
	{
		actions.namedAction( serializeAction, SERIALIZE_KEYS );
	}

	private void run()
	{

	}
}
