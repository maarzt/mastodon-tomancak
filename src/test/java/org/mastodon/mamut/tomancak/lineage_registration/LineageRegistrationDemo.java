package org.mastodon.mamut.tomancak.lineage_registration;

import java.io.IOException;
import java.util.Collections;

import mpicbg.spim.data.SpimDataException;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.LinAlgHelpers;

import org.mastodon.blender.BlenderController;
import org.mastodon.collection.RefCollection;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefRefMap;
import org.mastodon.collection.ref.RefIntHashMap;
import org.mastodon.mamut.MainWindow;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.project.MamutProject;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.model.tag.ObjTags;
import org.mastodon.model.tag.TagSetStructure;
import org.scijava.Context;

public class LineageRegistrationDemo
{

	private final Context context;

	public final MamutAppModel embryoA;

	public final MamutAppModel embryoB;

	public static void main(String... args) {
		new LineageRegistrationDemo().run();
	}

	private LineageRegistrationDemo()
	{
		this.context = new Context();
		this.embryoA = openAppModel( context, "/home/arzt/Datasets/Mette/E1.mastodon" );
		this.embryoB = openAppModel( context, "/home/arzt/Datasets/Mette/E2.mastodon" );
	}

	public void run()
	{
		LineageColoring.tagLineages( embryoA.getModel(), embryoB.getModel() );
		LineageRegistrationAlgorithm.run( embryoA.getModel(), embryoB.getModel() );
		addNotMappedTag();
		RefIntMap< Spot > colors = colorPositionCorrectnessEmbryoA();
		showEmbryoA( colors );
	}

	private RefIntMap< Spot > colorPositionCorrectnessEmbryoA()
	{
		ModelGraph graphA = embryoA.getModel().getGraph();
		ModelGraph graphB = embryoB.getModel().getGraph();
		RefRefMap< Spot, Spot > roots = RootsPairing.pairRoots( graphA, graphB );
		AffineTransform3D transformAB = EstimateTransformation.estimateScaleRotationAndTranslation( roots );
		RefRefMap< Spot, Spot > mapping = new LineageRegistrationAlgorithm(
				graphA, graphB,
				roots, transformAB ).getMapping();
		double sizeA = sizeEstimate( graphA );
		double[] positionB = new double[3];
		double[] positionA = new double[3];
		Spot ref = graphB.vertexRef();
		RefIntMap<Spot> colors = new RefIntHashMap<>( graphA.vertices().getRefPool(), 0xff550000 );
		for(Spot spotA : mapping.keySet() ) {
			Spot spotB = mapping.get( spotA, ref );
			spotA.localize( positionA );
			spotB.localize( positionB );
			transformAB.applyInverse( positionB, positionB );
			double distance = LinAlgHelpers.distance( positionA, positionB );
			colors.put( spotA, gray( 1 - distance / sizeA * 5 ) );
		}
		return colors;
	}

	private int gray( double b )
	{
		if ( b > 1 )
			b = 1;
		if ( b < 0 )
			b = 0;
		int gray = (int) (b * 255);
		return 0xff000000 + gray + (gray << 8) + (gray << 16);
	}

	private double sizeEstimate( ModelGraph graphB )
	{
		RealInterval boundingBox = boundingBox( graphB.vertices());
		return LinAlgHelpers.distance( boundingBox.minAsDoubleArray(), boundingBox.maxAsDoubleArray() );
	}

	private void addNotMappedTag()
	{
		RefRefMap< Spot, Spot > mapping = getMapping();
		TagSetStructure.TagSet tagSet = LineageColoring.createTagSet( embryoA.getModel(), "registration", Collections.singletonMap( "not mapped", 0xffff2222 ) );
		TagSetStructure.Tag tag = tagSet.getTags().get( 0 );
		ModelGraph graphA = embryoA.getModel().getGraph();
		for( Spot spotA : LineageTreeUtils.getBranchStarts( graphA ) ) {
			Spot spotB = mapping.get( spotA );
			if( spotB == null )
				tagBranch( tag, embryoA.getModel(), spotA );
		}
	}

	private void tagBranch( TagSetStructure.Tag tag, Model model, Spot spotA )
	{
		ModelGraph graphA = model.getGraph();
		Spot spot = graphA.vertexRef();
		try
		{
			ObjTags< Spot > vertexTags = model.getTagSetModel().getVertexTags();
			ObjTags< Link > edgeTags = model.getTagSetModel().getEdgeTags();
			spot.refTo( spotA );
			vertexTags.set( spot, tag );
			while ( spot.outgoingEdges().size() == 1 )
			{
				Link link = spot.outgoingEdges().get( 0 );
				edgeTags.set( link, tag );
				spot = link.getTarget( spot );
				vertexTags.set( spot, tag );
			}
		}
		finally
		{
			graphA.releaseRef( spot );
		}
	}

	private void showEmbryoA( RefIntMap< Spot > colors )
	{
		BlenderController blender = new BlenderController( context, embryoA );
		blender.sendColors( colors::get );
	}

	private RefIntHashMap< Spot > colorCorrespondingPositionEmbryoA()
	{
		RefRefMap< Spot, Spot > mapping = getMapping();
		ModelGraph graphA = embryoA.getModel().getGraph();
		ModelGraph graphB = embryoB.getModel().getGraph();
		AffineTransform3D transform = coordinateNormalizationTransform( graphB );
		RefIntHashMap< Spot > colors = new RefIntHashMap<>( graphA.vertices().getRefPool(), 0xff000000 );
		Spot ref = graphB.vertexRef();
		double[] coords = new double[3];
		for( Spot spotA : mapping.keySet() ) {
			Spot spotB = mapping.get( spotA, ref );
			spotB.localize( coords );
			transform.apply( coords, coords );
			colors.put( spotA, ARGBType.rgba( coords[0] * 255, coords[1] * 255, coords[2] * 255, 1 ) );
		}
		return colors;
	}

	private AffineTransform3D coordinateNormalizationTransform( ModelGraph graphB )
	{
		RealInterval boundingBox = boundingBox( graphB.vertices() );
		AffineTransform3D transform = new AffineTransform3D();
		transform.setTranslation(
				- boundingBox.realMin(0),
				- boundingBox.realMin( 1 ),
				- boundingBox.realMin( 2 ) );
		transform.scale(
				1 / (boundingBox.realMax( 0 ) - boundingBox.realMin( 0 )),
				1 / (boundingBox.realMax( 1 ) - boundingBox.realMin( 1 )),
				1 / (boundingBox.realMax( 2 ) - boundingBox.realMin( 2 )));
		return transform;
	}

	private RealInterval boundingBox( RefCollection< Spot> vertices )
	{
		double[] coords = { 0, 0, 0 };
		double[] min = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
		double[] max = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
		for( Spot spot : vertices ) {
			spot.localize( coords );
			for ( int i = 0; i < 3; i++ )
			{
				min[i] = Math.min( min[i], coords[i] );
				max[i] = Math.max( max[i], coords[i] );
			}
		}
		return new FinalRealInterval( min, max );
	}

	private RefRefMap< Spot, Spot > getMapping()
	{
		ModelGraph graphA = embryoA.getModel().getGraph();
		ModelGraph graphB = embryoB.getModel().getGraph();
		RefRefMap< Spot, Spot > roots = RootsPairing.pairRoots( graphA, graphB );
		AffineTransform3D transformAB = EstimateTransformation.estimateScaleRotationAndTranslation( roots );
		return new LineageRegistrationAlgorithm(
				graphA, graphB,
				roots, transformAB ).getMapping();
	}

	private static MamutAppModel openAppModel( Context context, String projectPath )
	{
		try
		{
			MamutProject project = new MamutProjectIO().load( projectPath );
			WindowManager wm = new WindowManager( context );
			wm.getProjectManager().open( project );
			new MainWindow( wm ).setVisible( true );
			return wm.getAppModel();
		}
		catch ( SpimDataException | IOException e )
		{
			throw new RuntimeException(e);
		}
	}

}
