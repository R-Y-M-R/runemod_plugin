/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.runemod;

import com.google.inject.Provides;
import lombok.SneakyThrows;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.model.Triangle;
import net.runelite.api.model.Vertex;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.util.HotkeyListener;
import org.pf4j.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static net.runelite.api.Constants.TILE_FLAG_BRIDGE;

//import net.runelite.api.VarClientInt;
//import net.runelite.api.Varbits;
//import net.runelite.api.MenuOpcode;
//import net.runelite.api.widgets.WidgetInfo;
//import net.runelite.client.plugins.specbar.SpecBarConfig;
//import net.runelite.client.plugins.chatnotifications.ChatNotificationsConfig;

@Extension
@PluginDescriptor(
		name = "RuneMod",
		enabledByDefault = false,
		description = "Tool for downloading and creationg mods",
		tags = {"mod", "hd", "unreal", "ue4", "graphics"}
)

@Singleton
public class RuneMod extends Plugin
{
	public static volatile boolean clientJustConnected;
	private int ClientIntID;
	private int ClientIntValue;
	private int VarBitID;
	private int VarBitValue;
	private int VarPlayerID;
	private int VarPlayerValue;
	private int VarStrID;
	private String VarStrValue;
	private String terrainTileData = "";
	private int lastFrameWorldX;
	private int lastFrameWorldY;
	private int lastFrameWorldZ;
	private WorldPoint playerWorldPos = new WorldPoint(0,0,0);
	private List<Byte> meshDataByteList = new ArrayList<Byte>();
	private int counter;
	private int counter1;
	private List<WallObjectSpawned> spawnedWallObjects = new ArrayList<WallObjectSpawned>();
	private List<Long> spawnedObjectHashes = new ArrayList<Long>();
	private List<AnimationChanged> queuedAnimationChangePackets = new ArrayList<AnimationChanged>();

	private boolean keyEvent = false;
	public SharedMemoryManager rsclient_ui_pixels_shared_memory = new SharedMemoryManager();
	public SharedMemoryManager rsclient_terrain_shared_memory = new SharedMemoryManager();
	public boolean canvasSizeChanged = false;
	public boolean newRegionLoaded = false;

	private int clientPlane = -1;
	private Set<String> libMeshIDs = new HashSet<>();
	private Set<String> sentInstanceIds = new HashSet<>();
	private Set<Integer> sentSequenceDefIds = new HashSet<>();
	private Set<Integer> sentSkeletonIds = new HashSet<>();
	private Set<Integer> sentFrameIds = new HashSet<>();

	private String instanceID = "0";

	private String stringToSend = "nothing yet";
	AtomicInteger stringsToSendQuePosition = new AtomicInteger(0);
	MyRunnableSender myRunnableSender = new MyRunnableSender();
	ExecutorService executorService = Executors.newFixedThreadPool(1);
	ExecutorService executorService1 = Executors.newFixedThreadPool(1);
	MyRunnableReciever myRunnableReciever = new MyRunnableReciever();

	ObjectComposition sourceObjDef;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RuneModConfig config;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientUI clientUI;

	@Override
	protected void startUp() throws IOException {

		client.getCanvas().addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				System.out.println("id: "+mouseEvent.getID());
				System.out.println("modifiers: "+mouseEvent.getModifiersEx());
				System.out.println("clickCount: "+mouseEvent.getClickCount());
				System.out.println("button: "+mouseEvent.getButton());
				System.out.println("isPopupTrigger: "+mouseEvent.isPopupTrigger());
			}
		});

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		rsclient_ui_pixels_shared_memory.createSharedMemory("rsclient_ui_pixels", ((screenSize.height*screenSize.width*4)+4));
		rsclient_terrain_shared_memory.createSharedMemory("rsclient_terrain_data", 865280);

		executorService.execute(myRunnableSender);

		executorService1.execute(myRunnableReciever);
		myRunnableReciever.clientCanvas = client.getCanvas();
		client.setPrintMenuActions(true);

		keyManager.registerKeyListener(hotkeyListenerq);
		keyManager.registerKeyListener(hotkeyListenerw);
		keyManager.registerKeyListener(hotkeyListenerr);
		//sourceObjDef =  client.createObjectDefinition();

//		Thread thread = new Thread(this::threadTest);
//		thread.start();

		// The clicked x & y coordinates (the last arguments) are not processed in the game or sent to Jagex, so they don't have to be real.
	}


	private byte[] runGetSkeletonBytes (int id) {
		IndexDataBase var0 = client.getSequenceDefinition_animationsArchive();
		System.out.println("Animation frame Count: "+ var0.getFileIds().length);

		IndexDataBase var1 = client.getSequenceDefinition_skeletonsArchive();
		System.out.println("skeleton Count: "+ var1.getGroupCount());

		IndexDataBase var2 = client.getSequenceDefinition_archive();
		System.out.println("Sequence Count: "+ var2.getGroupCount());

		int count = 0;
		for (int i = 0; i < var2.getFileCounts().length; i++) {
			count = count+ var2.getFileCounts()[i];
		}
		System.out.println("Sequence file file Count: "+ count);
		//return (getSkeletonBytes(var0, var1, id, false));
		return null;
	}

	private byte[] insertIDToByteArray(byte[] bytes, int id) {
		//byte[] concatenated = new byte[bytes.length+4];
		byte[] id_temp = new byte[4];
		id_temp[0] = (byte)(id >> 24); // L: 84
		id_temp[1] = (byte)(id >> 16); // L: 85
		id_temp[2] = (byte)(id >> 8); // L: 86
		id_temp[3] = (byte)id; // L: 87

		byte[] concatenated = Arrays.copyOf(id_temp, id_temp.length + bytes.length);
		System.arraycopy(bytes, 0, concatenated, id_temp.length, bytes.length);

		return concatenated;
	}

	public byte[] addToArray(final byte[] source, final Byte element) {
		final byte[] destination = new byte[source.length + 1];
		System.arraycopy(source, 0, destination, 0, source.length);
		destination[source.length] = element;
		return destination;
	}

	private byte[] getFrameBytes(int frameId)
	{
		int framesGroupID = frameId >> 16;

		int frameFileId = frameId;
		frameFileId &= 65535;

		int var3 = framesGroupID; //framesGroupID
		Frames framesObjects = client.getFrames(var3);
		if (framesObjects == null) {
			return null;
		}

		IndexDataBase animArchive = client.getSequenceDefinition_animationsArchive(); //var1

		int[] var7 = animArchive.getFileIds(var3); //array of files in group

		for (int var8 = 0; var8 < var7.length; ++var8)
		{ //for each of the files in the group
			if (var7[var8] == frameFileId)
			{
				System.out.println("frameIdx_OldMethod: "+ (frameId & 0xFFFF) + " frameIdx_NewMethod: " + var8);
				byte[] var9 = animArchive.getConfigData(var3, var7[var8]);//animationFrameBytes
				return var9;
			}
		}
		return null;
	}

/*	private byte[] getFrameBytes(int frameId) //old bad methof of gettoing frame bytes
	{
		IndexDataBase animArchive = client.getSequenceDefinition_animationsArchive(); //var1

		int frameIdx = frameId & 0xFFFF;
		int framesGroupID = frameId >> 16;

		int[] frameFileIds = animArchive.getFileIds(framesGroupID); // L: 38
		byte[] bytes_AnimationFrame = animArchive.getConfigData(framesGroupID, frameFileIds[frameIdx]); // L: 40

		return bytes_AnimationFrame;
	}*/

	private byte[] getSkeletonBytesForAnimFrame(byte[] animFrameBytes) {
		int skeletonId = (animFrameBytes[0] & 255) << 8 | animFrameBytes[1] & 255;
		IndexDataBase skeletonArchive = client.getSequenceDefinition_skeletonsArchive(); //var2
		byte[] bytes_Skeleton = skeletonArchive.loadData(skeletonId, 0); // L: 54
		return bytes_Skeleton;
	}

	private void sendAnimation (int sequenceDefID) {
		boolean var3 = false;

		byte[] bytes_sequenceDefinition = client.getSequenceDefinition_archive().getConfigData(12, sequenceDefID); // L: 37

		if (bytes_sequenceDefinition!= null) {
			System.out.println("sending animSequence: " + sequenceDefID);
			if (!sentSequenceDefIds.contains(sequenceDefID)) {
				bytes_sequenceDefinition = insertIDToByteArray(bytes_sequenceDefinition, sequenceDefID); // inserts bytes to define id

				myRunnableSender.sendBytes(bytes_sequenceDefinition,"SequenceDefinition");

				//sentSequenceDefIds.add(sequenceDefID);
/*			System.out.println("sent sequence def id: " + sequenceDefID);
			for (int i1 = 0; i1 < bytes_sequenceDefinition.length; i1++) {
				System.out.println(bytes_sequenceDefinition[i1]);
			}*/
			}


			IndexDataBase animArchive = client.getSequenceDefinition_animationsArchive();
			IndexDataBase skeletonArchive = client.getSequenceDefinition_skeletonsArchive();

			//byte[] bytes_SeqDef = sequenceDefArchive.getConfigData(12, sequenceDefID); // send seqDefBytes
			SequenceDefinition sequenceDef = client.getSequenceDefinition(sequenceDefID);
			int[] sequenceFrameIds = sequenceDef.getFrameIDs();

			//System.out.println("bitshifted frameid is: "+ framesGroupID);

			//System.out.println("animSequence frameCount: " + sequenceDef.getFrameIDs().length);

			//NodeDeque var5 = client.createNodeDeque(); // L: 35

			//System.out.println("frameFIleIDs len: "+ frameFileIds.length);

			for (int i = 0; i < sequenceDef.getFrameIDs().length; ++i)
			{ // L: 39

				int frame = i;
				int packed = frame ^ Integer.MIN_VALUE;
				int interval = packed >> 16;
				frame = packed & 0xFFFF;
				int frameId = sequenceFrameIds[frame];
/*				int frameIdx = frameId & 0xFFFF;
				int framesGroupID = frameId >> 16;

				int[] frameFileIds = animArchive.getFileIds(framesGroupID); // L: 38
				if (frameIdx >= frameFileIds.length) { continue; }
				byte[] bytes_AnimationFrame = animArchive.getConfigData(framesGroupID, frameFileIds[frameIdx]); // L: 40
				Skeleton skeleton = null; // L: 41
				int skeletonId = (bytes_AnimationFrame[0] & 255) << 8 | bytes_AnimationFrame[1] & 255; // L: 42

				for (Skeleton skeletonLast = (Skeleton) var5.last(); skeletonLast != null; skeletonLast = (Skeleton) var5.previous()) //if skeleton != null do the loop
				{ // L: 43 44 49
					if (skeletonId == skeletonLast.id())
					{ // L: 45
						skeleton = skeletonLast; // L: 46
						break;
					}
				}

				if (!sentSkeletonIds.contains(skeletonId))
				{
					byte[] bytes_Skeleton;
					if (var3) { // L: 53
						bytes_Skeleton = skeletonArchive.getFile(0, skeletonId);
					}
					else {
						bytes_Skeleton = skeletonArchive.getFile(skeletonId, 0); // L: 54
					}

					//send skeleton bytes
					//skeleton = client.createSkeleton(skeletonId, bytes_Skeleton); // L: 55
					bytes_Skeleton = insertIDToByteArray(bytes_Skeleton, skeletonId); // inserts bytes to define id
					myRunnableSender.sendBytes(bytes_Skeleton,"Skeleton");
					sentSkeletonIds.add(skeletonId);
				}*/

				if (!sentFrameIds.contains(frameId))
				{
					byte[] frameBytes = getFrameBytes(frameId);
					if (frameBytes!= null) {
						int skeletonId = (frameBytes[0] & 255) << 8 | frameBytes[1] & 255; // L: 42

						if (!sentSkeletonIds.contains(skeletonId))
						{
							byte[] bytes_Skeleton;
							bytes_Skeleton = skeletonArchive.loadData(skeletonId, 0);
							if (bytes_Skeleton!=null)
								bytes_Skeleton = insertIDToByteArray(bytes_Skeleton, skeletonId); // inserts bytes to define id
							myRunnableSender.sendBytes(bytes_Skeleton,"Skeleton");
							sentSkeletonIds.add(skeletonId);
						}
						frameBytes = insertIDToByteArray(frameBytes, frameId); // inserts bytes to define id
						myRunnableSender.sendBytes(frameBytes,"AnimationFrame");
						sentFrameIds.add(frameId);
					}
					//send animationFrame bytes
					//System.out.println("sent frame id: " + frameId);
				}
				//frames[frameFileIds[i]] = client.createAnimationFrame(bytes_AnimationFrame, skeleton); // L: 58
			}
		}
	}


	private static final Keybind myKeybindQ = new Keybind(KeyEvent.VK_Q, InputEvent.ALT_DOWN_MASK);
	private final HotkeyListener hotkeyListenerq = new HotkeyListener(() -> myKeybindQ)
	{
		@Override
		public void hotkeyPressed() //print animation
		{
//			client.invokeMenuAction(-1, 36569105, 57, 1, "Look up name", "", 206, 261);
//			KeyEvent kvPressed = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER);
//			KeyEvent kvReleased = new KeyEvent(client.getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER);

			int sequenceDefCount = client.getSequenceDefinition_archive().getGroupFileCount(12);
			System.out.println("sequenceDefCount: " + sequenceDefCount);
			for (int i = 0; i < sequenceDefCount; i++) {
				sendAnimation(i);
			}

			int objDefModelDataCount = client.getObjectDefinition_modelsArchive().getGroupCount();
			System.out.println("objDefModelDataCount: " + objDefModelDataCount);
			for (int i = 0; i < objDefModelDataCount; i++) {
				byte[] modelDataBytes = client.getObjectDefinition_modelsArchive().getConfigData(i & 65535, 0);
				if (modelDataBytes != null) {
					modelDataBytes = insertIDToByteArray(modelDataBytes, i);
					myRunnableSender.sendBytes(modelDataBytes,"ModelData");
				}
			}

			int npcDefCount = client.getNpcDefinition_archive().getGroupFileCount(9);
			System.out.println("npcDefCount: " + npcDefCount);
			for (int i = 0; i < npcDefCount; i++) {
				byte[] npcDefinitionBytes = client.getNpcDefinition_archive().getConfigData(9, i);
				if (npcDefinitionBytes != null) {
					npcDefinitionBytes = insertIDToByteArray(npcDefinitionBytes, i);
					myRunnableSender.sendBytes(npcDefinitionBytes,"NpcDefinition");
				}
			}

			int objectDefinitionCount = client.getObjectDefinition_archive().getGroupFileCount(6);
			System.out.println("objectDefCount: " + objectDefinitionCount);
			for (int i = 0; i < objectDefinitionCount; i++) {
				byte[] objectDefinitionBytes = client.getObjectDefinition_archive().getConfigData(6, i);
				if (objectDefinitionBytes != null) {
					objectDefinitionBytes = insertIDToByteArray(objectDefinitionBytes, i);
					myRunnableSender.sendBytes(objectDefinitionBytes,"ObjectDefinition");
				}
			}

			int itemDefinitionCount = client.getItemDefinition_archive().getGroupFileCount(10);
			System.out.println("itemDefCount: " + itemDefinitionCount);
			for (int i = 0; i < itemDefinitionCount; i++) {
				byte[] itemDefinitionBytes = client.getItemDefinition_archive().getConfigData(10, i);
				if (itemDefinitionBytes != null) {
					itemDefinitionBytes = insertIDToByteArray(itemDefinitionBytes, i);
					myRunnableSender.sendBytes(itemDefinitionBytes,"ItemDefinition");
				}
			}

			int kitDefinitionCount = client.getKitDefinition_archive().getGroupFileCount(3);
			System.out.println("kitDefCount: " + kitDefinitionCount);
			for (int i = 0; i < kitDefinitionCount; i++) {
				byte[] kitDefinitionBytes = client.getKitDefinition_archive().getConfigData(3, i);
				if (kitDefinitionBytes != null) {
					kitDefinitionBytes = insertIDToByteArray(kitDefinitionBytes, i);
					myRunnableSender.sendBytes(kitDefinitionBytes,"KitDefinition");
				}
			}

			System.out.println("pressed");
/*			for (int i = 0; i < 60000; i++) {
				//int modelID = 9637+counter; // capes
				//int modelID = 640 + counter; //spears wall
				//int modelID = 14200 + counter; //soft edged man man with hard edged shackles
		}*/

		}
	};

	private void sendTerrainData() {
		//SendLowLatencyData();
		Scene scene = client.getScene();

		int tileHeights [][][] = client.getTileHeights();
		byte tileSettings [][][] = client.getTileSettings();

		int tileHeightsBufferSize = 43264;
		int tileSettingsBufferSize = 43264;
		int tileColorsBufferSize = 43264*4;
		int tileTextureIdsBufferSize = 43264;
		int tileRotationsBufferSize = 43264;
		int tileOverlayColorsBufferSize = 43264*4;
		int tileOverlayShapesBufferSize = 43264;
		int tileOverlayTextureIdsBufferSize = 43264;
		int flatTileBufferSize = 43264;
		int tileShouldDrawBufferSize = 43264;
		int tileOriginalPlaneBufferSize = 43264;

		int terrainDataBufferSize = 4+tileHeightsBufferSize+
			tileSettingsBufferSize+
			tileColorsBufferSize+
			tileTextureIdsBufferSize+
			tileRotationsBufferSize+
			tileOverlayColorsBufferSize+
			tileOverlayShapesBufferSize+
			tileOverlayTextureIdsBufferSize+
			flatTileBufferSize+
			tileShouldDrawBufferSize+
			tileOriginalPlaneBufferSize;

		int noTerrainDataTypes = 9;

		Buffer terrainDataBuffer = client.createBuffer(new byte[terrainDataBufferSize]);

		for (int z = 0; z < 4; z++)
		{
			for (int y = 0; y < 104; y++)
			{
				for (int x = 0; x < 104; x++)
				{
					boolean isBridgeTile = false;
					boolean isLinkedTile = false;
					int tileOriginalPlane = 0;
					int isAboveBridge = 0;
					if (z == 2 &&  z < Constants.MAX_Z - 1 && (client.getTileSettings()[z-1][x][y] & TILE_FLAG_BRIDGE) == TILE_FLAG_BRIDGE) { //if we are above a bridge tile
						isAboveBridge = 1;
					}

					if (z < Constants.MAX_Z - 1 && (tileSettings[z][x][y] & TILE_FLAG_BRIDGE) == TILE_FLAG_BRIDGE && z == 1)
					{
						isBridgeTile = true;
					}

					Tile tile = null;

					if (client.getScene().getTiles()[z][x][y]!= null)
					{
						tile = scene.getTiles()[z][x][y];
						tileOriginalPlane = tile.getRenderLevel();
					}

					if (client.getScene().getTiles()[z][x][y]!= null || isBridgeTile)
					{
						if (isBridgeTile)
						{ // if is bridge tile, we get the tiles colors from the tile bellow. We also eed to check Ne color of the bellow tile, to know if the bridge tile is supposed to be rendered/visible.
							tile = scene.getTiles()[z][x][y];
							if (scene.getTiles()[z - 1][x][y] != null)
							{
								if (scene.getTiles()[z - 1][x][y].getSceneTilePaint() != null)
								{
									if (scene.getTiles()[z - 1][x][y].getSceneTilePaint().getNeColor() != 12345678)
									{
										tile = scene.getTiles()[z - 1][x][y];
									}
								}
							}
						}
						else
						{
							if (scene.getTiles()[z][x][y].getBridge() != null)
							{ //if this tile is bellow a bridge, we get colors from Linked_Bellow tile (getBridge is getLinkedBellow)
								tile = scene.getTiles()[z][x][y].getBridge();
								isLinkedTile = true;
							}
							else
							{
								tile = scene.getTiles()[z][x][y]; //normal Tile
							}
						}
					}

					int tileHeight =  (tileHeights[z+isAboveBridge][x][y]/8)*-1;
					int tileSetting = tileSettings[z][x][y];
					int tileCol = 0; //12345678
					int tileTextureId = -1;
					int tileRotation = -1;
					int tileOverlayColor = 0; //12345678
					int tileOverlayTextureId = -1;
					int tileOverlayShape = 0;
					int tileIsFlat = 0;
					int tileIsdDraw = -1;

					if (tile!= null)
					{
						if (tile.getSceneTilePaint() != null)
						{
							if (tile.getSceneTilePaint().getNeColor() == 12345678 || tile.getSceneTilePaint().getSwColor() == 12345678) {
								tileIsdDraw = 0;
							} else {
								tileIsdDraw = 1;
							}

							tileCol = tile.getSceneTilePaint().getRBG();
							tileTextureId = tile.getSceneTilePaint().getTexture();
						}

						SceneTileModel tileModel = tile.getSceneTileModel();
						if (tileModel != null)
						{
							if ((tileModel.getModelUnderlay()== 12345678 || tileModel.getModelOverlay()==12345678) || tileIsdDraw == 0) {
								tileIsdDraw = 0;
							} else {
								tileIsdDraw = 1;
							}
							tileRotation = tileModel.getRotation();
							tileOverlayColor = tileModel.getModelOverlay();
							tileCol = tileModel.getModelUnderlay();

/*								if (z < 3) {
									if (tileSettings[tile.getPlane()+1] [tile.getSceneLocation().getX()] [tile.getSceneLocation().getY()] == 8) { //if tile above has flag 8, tilemodel is used on plane bellow? maybe
										Tile tileAbove = scene.getTiles()[tile.getPlane()+1] [tile.getSceneLocation().getX()] [tile.getSceneLocation().getY()];
										if (tileAbove!= null) {
											SceneTileModel tileModelAbove = tileAbove.getSceneTileModel();
											if (tileAbove != null)
											{
												if (tileModelAbove!= null)
												{
													tileCol = tileModelAbove.getModelOverlay();
													int[] tileModelTextureIds = tileModel.getTriangleTextureId();
													if (tileModelTextureIds != null)
													{ //find textureIDForOverlay
														for (int i = 0; i < tileModelTextureIds.length; i++)
														{
															if (tileModelTextureIds[i] > 0)
															{
																tileOverlayTextureId = tileModelTextureIds[i];
																break;
															}
														}
													}
												}
											}
										}
									}
								}*/

							int[] tileModelTextureIds = tileModel.getTriangleTextureId();
							if (tileModelTextureIds != null)
							{ //find textureIDForOverlay
								for (int i = 0; i < tileModelTextureIds.length; i++)
								{
									if (tileModelTextureIds[i] > 0)
									{
										tileOverlayTextureId = tileModelTextureIds[i];
										break;
									}
								}
							}

							tileOverlayShape = tileModel.getShape();
							if (tileModel.getIsFlat()) tileIsFlat = 1;
						}


					}

					if (tileIsdDraw == -1){tileIsdDraw = 0;}

					terrainDataBuffer.writeByte(tileHeight); // tileHeightsBuffer
					terrainDataBuffer.writeByte(tileSetting); // tileSettingsBuffer
					terrainDataBuffer.writeInt(tileCol); // tileColorsBuffer
					terrainDataBuffer.writeByte(tileTextureId); // tileTextureIdsBuffer
					terrainDataBuffer.writeByte(tileRotation); // tileRotationsBuffer
					terrainDataBuffer.writeInt(tileOverlayColor); //tileOverlayColorsBuffer
					terrainDataBuffer.writeByte(tileOverlayTextureId); //tileOverlayTextureIdsBuffer
					terrainDataBuffer.writeByte(tileOverlayShape); //tileOverlayShapesBuffer
					terrainDataBuffer.writeByte(tileIsFlat); //flatTileBuffer
					terrainDataBuffer.writeByte(tileIsdDraw); //flatTileBuffer
					terrainDataBuffer.writeByte(tileOriginalPlane); //flatTileBuffer
				}
			}
		}
		rsclient_terrain_shared_memory.setDataLength(terrainDataBuffer.getPayload().length); //write data length
		rsclient_terrain_shared_memory.SharedMemoryData.write(4,terrainDataBuffer.getPayload(),0,terrainDataBuffer.getPayload().length);
		myRunnableSender.sendBytes(new byte[3], "TerrainLoad");
		//rsclient_terrain_shared_memory.SharedMemoryData.read();
	}

	private void rgbaIntToColors(int col) {
		int a = (col >> 24) & 0xFF;
		int r = (col >> 16) & 0xFF;
		int g = (col >> 8) & 0xFF;
		int b = col & 0xFF;
	}

	@SneakyThrows
	private void createSharedMemory () {
/*		ByteBuffer buf = ByteBuffer.allocateDirect(10);
		directMemoryBuffer = new DirectMemoryBuffer(buf,0,10);
		for (int i = 0; i < 10; i++) {
			buf.put((byte)i);
		}
		System.out.println("made new DirectByteBuffer at address: "+directMemoryBuffer.getAddress());*/
	}


/*	private void sendUiPixels() {
		int bufferLen = client.getGraphicsPixelsWidth()*client.getGraphicsPixelsHeight() + 4;
		Buffer uiByteBuffer = client.createBuffer(new byte[bufferLen]);
		uiByteBuffer.writeInt(bufferLen);

		for (int i = 0; i < client.getGraphicsPixels().length; i++) {
			int col = client.getGraphicsPixels()[i];
			byte a = (byte)((col >> 24) & 0xFF);
			byte r = (byte)((col >> 16) & 0xFF);
			byte g = (byte)((col >> 8) & 0xFF);
			byte b = (byte)(col & 0xFF);
			uiByteBuffer.writeByte(a);
			uiByteBuffer.writeByte(r);
			uiByteBuffer.writeByte(g);
			uiByteBuffer.writeByte(b);
		}
	}*/

	private void printUIPixelAtMousePos() {
/*		System.out.println(client.getGraphicsPixelsWidth());
		int arrayPos = client.getMouseCanvasPosition().getX() + (client.getMouseCanvasPosition().getY()*client.getGraphicsPixelsWidth());
		int col = client.getGraphicsPixels()[arrayPos];
		int a = (col >> 24) & 0xFF;
		int r = (col >> 16) & 0xFF;
		int g = (col >> 8) & 0xFF;
		int b = col & 0xFF;
		System.out.print("a");
		System.out.print(" "+a+" ");
		System.out.print("r");
		System.out.print(" "+r+" ");
		System.out.print("g");
		System.out.print(" "+g+" ");
		System.out.print("b");
		System.out.print(" "+b+" ");*/
	}

public Component component = new Component() {
	@Override
	public void addNotify() {
		super.addNotify();
	}
};

	private static final Keybind myKeybindW = new Keybind(KeyEvent.VK_W, InputEvent.SHIFT_DOWN_MASK);
	private final HotkeyListener hotkeyListenerw = new HotkeyListener(() -> myKeybindW)
	{
		@Override
		public void hotkeyPressed() //send terrain
		{
			int x = MouseInfo.getPointerInfo().getLocation().x - client.getCanvas().getLocationOnScreen().x;
			int y = MouseInfo.getPointerInfo().getLocation().y - client.getCanvas().getLocationOnScreen().y;
			var eventQ = Toolkit.getDefaultToolkit().getSystemEventQueue();
			var mouseEventClick = new MouseEvent(client.getCanvas(),MouseEvent.MOUSE_PRESSED, System.currentTimeMillis() + 10, MouseEvent.BUTTON1, x,y, 0, false);
			var mouseEventPosition = new MouseEvent(client.getCanvas(),MouseEvent.MOUSE_MOVED, System.currentTimeMillis() + 10, MouseEvent.NOBUTTON, x,y, 0, false);
			var keyEventA = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_A);
			var keyEventB = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_B, (char) 66);
			eventQ.postEvent(keyEventB);
			//eventQ.postEvent(mouseEvent);



			//client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(),MouseEvent.MOUSE_PRESSED, System.currentTimeMillis() + 10, MouseEvent.BUTTON1, 200,200, 0, false));


			//var event = new MouseEvent(component, 0, 0, 0, 200, 200, 1, false);
			//eventQ.dispatchEvent(event);

/*			client.setPlane(config.VarStrID());

			Tile tile = client.getSelectedSceneTile();
			GameObject[] gameobjects = tile.getGameObjects();
			if (gameobjects.length > 0)
			{
					client.getScene().removeGameObject(gameobjects[0]);

			}*/
			//sendAnimation(config.animSequenceDefID());
			System.out.println("shift+w");
			//byte[] bytes = new byte[3];
			//myRunnableSender.sendBytesTest(bytes, "LibMesh");
/*			int[] playerEquipmentIds = client.getLocalPlayer().getPlayerComposition().getEquipmentIds();
			int[] bodyColors = client.getLocalPlayer().getPlayerComposition().getBodyPartColours();

			for (int i = 0; i < playerEquipmentIds.length; i++)
			{
				System.out.println("equipIndex "+ i +" = " + playerEquipmentIds[i]);
			}

			for (int i = 0; i < bodyColors.length; i++)
			{
				System.out.println("bodyColorIndex "+ i +" = " + bodyColors[i]);
			}*/
			//client.setCameraY(client.getCameraY()+100);
		}
	};

	@Subscribe
	private void onTerrainLoaded(TerrainLoaded event)
	{
		SendPerFramePacket();

			//clientThread.wait(1,1);

		sendTerrainData();
	}


	private static final Keybind myKeybindR = new Keybind(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK);
	private final HotkeyListener hotkeyListenerr = new HotkeyListener(() -> myKeybindR)
	{
		@Override
		public void hotkeyPressed() //send gameobjects
		{
			//client.setCameraY2(client.getCameraY()+100);
		}
	};




	@Provides
	RuneModConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneModConfig.class);
	}


	@Subscribe
	private void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"drawSpecbarAnyway".equals(event.getEventName()))
		{
			return;
		}

		int[] iStack = client.getIntStack();
		int iStackSize = client.getIntStackSize();
		iStack[iStackSize - 1] = 1;
	}

	@Subscribe
	private void onGameTick(GameTick event) {
		playerWorldPos = client.getLocalPlayer().getWorldLocation();

		//System.out.println("clientPixelsX = "+client.getGraphicsPixelsWidth());

//		System.out.println("tick ahem");
		if (clientJustConnected) {
			libMeshIDs.clear();
			sentInstanceIds.clear();
			clientJustConnected = false;
			//myRunnableSender.clientConnected = false;
		}

		LocalPoint localPos = client.getLocalPlayer().getLocalLocation();

		int localX = localPos.getX();
		int localY = localPos.getY();
		int lastLocalX = localX;
		int lastLocalY = localY;

		boolean isDivisibleByX = playerWorldPos.getX() % 10 == 0;
		boolean isDivisibleByY = playerWorldPos.getY() % 10 == 0;
	}

	private int OrientationToAngles(int orient) {
		int angle = 1;
		switch (orient) {
			case 1:
				angle=0;
				break;
			case 2:
				angle=90;
				break;
			case 4:
				angle=180;
				break;
			case 8:
				angle=270;
				break;
			case 16:
				angle=45;
				break;
			case 32:
				angle=135;
				break;
			case 64:
				angle=225;
				break;
			case 128:
				angle = 315;
				break;
		}
		if (angle == 1) {
			System.out.println("orientToAngles Failed" + orient);
		}
		return angle;
	}

	private int CalculateSubID(int ObjectType, int orient) {
		int subType = 0;
		switch (orient) {
			case 1:
				subType=0;
				break;
			case 2:
				subType=0;
				break;
			case 4:
				subType=0;
				break;
			case 8:
				subType=0;
				break;
			case 16:
				subType=1;
				break;
			case 32:
				subType=2;
				break;
			case 64:
				subType=3;
				break;
			case 128:
				subType=4;
				break;
		}
		subType = subType+(ObjectType*10);
		return subType;
	}



	private String GetModelVertData (Model model){
		List<Triangle> triangles = model.getTriangles();
		int [] faceCols1 = model.getFaceColors1();
		int [] faceCols2 = model.getFaceColors2();
		int [] faceCols3 = model.getFaceColors3();

		String xVertsString = "";
		String yVertsString = "";
		String zVertsString = "";
		String trianglesString = "";
		String vertColsString = "";
		int curVertIndex = -1;
//			System.out.println(triangles.size());
		for (int i = 0; i < triangles.size(); ++i) {

			Triangle triangle = triangles.get(i);
			Vertex vertA = triangle.getA();
			Vertex vertB = triangle.getB();
			Vertex vertC = triangle.getC();
			xVertsString = xVertsString+vertA.getX()+","+vertB.getX()+","+vertC.getX()+",";
			curVertIndex = curVertIndex+1;
			trianglesString = trianglesString+curVertIndex+",";

			yVertsString = yVertsString+vertA.getY()+","+vertB.getY()+","+vertC.getY()+",";
			curVertIndex = curVertIndex+1;
			trianglesString = trianglesString+curVertIndex+",";

			zVertsString = zVertsString+vertA.getZ()+","+vertB.getZ()+","+vertC.getZ()+",";
			curVertIndex = curVertIndex+1;
			trianglesString = trianglesString+curVertIndex+",";

			vertColsString = vertColsString+ faceCols1[i]+","+faceCols2[i]+","+faceCols3[i]+",";
		}
		return(xVertsString+"_"+yVertsString+"_"+zVertsString+"_"+trianglesString+"_"+vertColsString);
	}

	private void sendLibMeshBytes(Model model, int ID, int subID, int Orientation){
		List intList = new ArrayList();
		boolean modelHasUVCoords = false;
		boolean faceHasUVCoords = false;
		boolean modelHasTextures = false;
		boolean faceHasUTexture = false;
		float[][] uCoords =  new float[0][0];
		float[][] vCoords =  new float[0][0];
		short[] faceTextures =  new short[0];


/*		if (model.getFaceTextureUCoordinates()!=null) {
			uCoords = model.getFaceTextureUCoordinates();
			vCoords = model.getFaceTextureVCoordinates();
			modelHasUVCoords = true;
		}*/

		if (model.getFaceTextures()!=null) {
			faceTextures = model.getFaceTextures();
			modelHasTextures = true;
		}

		List<Triangle> triangles = model.getTriangles();
		int [] faceCols1 = model.getFaceColors1();
		int [] faceCols2 = model.getFaceColors2();
		int [] faceCols3 = model.getFaceColors3();
		byte [] faceAlphas = model.getFaceTransparencies();

		for (int i = 0; i < triangles.size(); ++i) {
			if (modelHasUVCoords==true) {
				if (uCoords[i] != null) {
					if (vCoords[i] != null){
						faceHasUVCoords = true;
					} else faceHasUVCoords = false;
				} else faceHasUVCoords = false;
			} else faceHasUVCoords = false;

			Triangle triangle = triangles.get(i);
			Vertex vertA = triangle.getA();
			Vertex vertB = triangle.getB();
			Vertex vertC = triangle.getC();
			intList.add(vertA.getX());
			intList.add(vertA.getY());
			intList.add(vertA.getZ());

			intList.add(vertB.getX());
			intList.add(vertB.getY());
			intList.add(vertB.getZ());

			intList.add(vertC.getX());
			intList.add(vertC.getY());
			intList.add(vertC.getZ());

			intList.add(faceCols1[i]);
			intList.add(faceCols2[i]);
			intList.add(faceCols3[i]);

			if (modelHasTextures == true) {
				if (faceTextures[i] > -1) {
					intList.add((int)faceTextures[i]);
//					System.out.println((int)faceTextures[i]);
				} else {
					intList.add(256);
				}
			} else {
				intList.add(256);
			}

			if (faceHasUVCoords == true){
				intList.add(UVCoordinateEncode(uCoords[i][0]));
				intList.add(UVCoordinateEncode(vCoords[i][0]));
				intList.add(UVCoordinateEncode(uCoords[i][1]));
				intList.add(UVCoordinateEncode(vCoords[i][1]));
				intList.add(UVCoordinateEncode(uCoords[i][2]));
				intList.add(UVCoordinateEncode(vCoords[i][2]));
			} else {
				intList.add(UVCoordinateEncode(1.0f));
				intList.add(UVCoordinateEncode(1.0f));
				intList.add(UVCoordinateEncode(0.5f));
				intList.add(UVCoordinateEncode(0.0f));
				intList.add(UVCoordinateEncode(0.0f));
				intList.add(UVCoordinateEncode(0.0f));
			}
		}

		intList.add(0);
		intList.add(0);
		intList.add(0);
		intList.add(0);
		intList.add(0);
		intList.add(0);
		intList.add(0);
		intList.add(Orientation);
		intList.add(subID);
		intList.add(ID);

		myRunnableSender.sendLibMeshBytes(intListToByteArray(intList));
	}

	private int UVCoordinateEncode(float coord) { //encodes float into an int
		coord = (coord+20)*10000;
		return Math.round(coord); //to decode, we do (coord - 200000) * 0.0001
	}
	private void sendGameObjectBytes(int objectDefID, int libMeshID, int libMeshSubID, int posX, int posY, int posZ, int orientation, int scaleX, int scaleY, int scaleHeight, int isFlipped, int contourGround, int setting11, int setting12){
		//ObjectInstance data comprises of 10 integers
		List intList = new ArrayList();

		intList.add(objectDefID);
		intList.add(libMeshID);
		intList.add(libMeshSubID);
		intList.add(posX);
		intList.add(posY);
		intList.add(posZ);
		intList.add(orientation);
		intList.add(scaleX);
		intList.add(scaleY);
		intList.add(scaleHeight);
		intList.add(isFlipped);
		intList.add(contourGround);
		intList.add(setting11);
		intList.add(setting12);

		myRunnableSender.sendGameObjectBytes(intListToByteArray(intList));
		//System.out.println("sent gameObject Instance");
	}

	private	int getObjModelTypeFromFlags(int flags){
		return flags & 63;
	}


	@Subscribe
	private void onGameStateChanged(GameStateChanged event) {
		GameState gamestate = event.getGameState();

		byte newEventTypeByte = 0;
		if (gamestate == GameState.LOGIN_SCREEN)
		{
			System.out.println("Login SCREEN...");
			newEventTypeByte = 1;
		}else
		if (gamestate == GameState.LOGGING_IN)
		{
			System.out.println("logging in...");
			newEventTypeByte = 2;
		}else
		if (gamestate == GameState.LOGGED_IN)
		{
			System.out.println("logged in...");
			newEventTypeByte = 3;
		}else
		if (gamestate == GameState.HOPPING)
		{
			System.out.println("hopping...");
			newEventTypeByte = 4;
		}else
		if (gamestate == GameState.LOADING)
		{
			SendPerFramePacket();
			System.out.println("loading...");
			newEventTypeByte = 5;
			newRegionLoaded = true;
		}

		myRunnableSender.sendBytes(new byte[] {newEventTypeByte,0,0},"GameStateChanged");
	}

	@Subscribe
	private void onWallObjectChanged(WallObjectChanged wallObjectChanged)
	{
		System.out.println("wallobject changed: "+wallObjectChanged.getWallObject().getId());
	}

	@Subscribe
	private void onWallObjectDespawned(WallObjectDespawned event)
	{
		clientThread.invokeLater(() ->
		{
			Tile tile;
			if (event.getTile().getBridge()!= null) {
				tile = event.getTile().getBridge();
			} else {
				tile = event.getTile();
			}

			Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

			int tilePlane = tile.getPlane();
			int tileX = tile.getX();
			int tileY = tile.getY();
			long tag = event.getWallObject().getHash();
			actorSpawnPacket.writeByte(5); //write tileObject data type
			actorSpawnPacket.writeByte(tilePlane);
			actorSpawnPacket.writeByte(tileX);
			actorSpawnPacket.writeByte(tileY);
			actorSpawnPacket.writeLong(tag);

			myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorDeSpawn");
			System.out.println("wallobject despawned: "+event.getWallObject().getId());
		});
	}

	@Subscribe
	private void onWallObjectSpawned(WallObjectSpawned wallObjectspawned) {
		clientThread.invokeLater(() ->
		{
			Tile tile;
			tile = wallObjectspawned.getTile();


			Buffer actorSpawnPacket = client.createBuffer(new byte[100]);
			int tileObjectModelType = getObjModelTypeFromFlags(wallObjectspawned.getWallObject().getConfig());
			int var4 = (wallObjectspawned.getWallObject().getConfig() >> 6) & 3;

			int rotation = var4;
			int anint = (rotation * 512);

			int objectOrientationA = anint;
			int objectOrientationB = 1234;
			if (tileObjectModelType == 2) { //if wall is objectType 2. walltype 2 has a model B;
				rotation = (wallObjectspawned.getWallObject().getConfig() >> 6) & 3;
				rotation = rotation + 1 & 3;
				anint = (rotation * 512);
				objectOrientationB = anint;
			}
			int objectDefinitionId = wallObjectspawned.getWallObject().getId();
			int plane = tile.getPlane();
			int tileX = tile.getX();
			int tileY = tile.getY();

			byte[][][] tileSettings = client.getTileSettings();

			if (plane < Constants.MAX_Z - 1 && (tileSettings[1][tile.getX()][tile.getY()] & TILE_FLAG_BRIDGE) == TILE_FLAG_BRIDGE)
			{
				plane = plane-1;
				plane = plane > 3 ? 3 : plane < 0 ? 0 : plane;
			}

			int height = Perspective.getTileHeight(client, tile.getLocalLocation(), tile.getPlane()) * -1;
			actorSpawnPacket.writeByte(4); //write tileObject data type
			actorSpawnPacket.writeByte(tileObjectModelType);
			actorSpawnPacket.writeByte(var4);
			actorSpawnPacket.writeShort(objectOrientationA);
			actorSpawnPacket.writeShort(objectOrientationB);
			actorSpawnPacket.writeShort(objectDefinitionId);
			actorSpawnPacket.writeByte(plane);
			actorSpawnPacket.writeByte(tileX);
			actorSpawnPacket.writeByte(tileY);
			int tileMinPlane = tile.getPhysicalLevel();
			actorSpawnPacket.writeByte(tileMinPlane);
			actorSpawnPacket.writeShort(height);
			long tag = wallObjectspawned.getWallObject().getHash();
			actorSpawnPacket.writeLong(tag);
			int cycleStart = 0;
			int frame = 0;
			actorSpawnPacket.writeShort(cycleStart);
			actorSpawnPacket.writeShort(frame);
			int offsetX = 0;
			int offsetY = 0;
			actorSpawnPacket.writeShort(offsetX);
			actorSpawnPacket.writeShort(offsetY);

			myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
		});








/*		int objDefID1 = wallObjectspawned.getWallObject().getId();
		loadedObjDefinitions[objDefID1] = client.getObjectDefinition(objDefID1);

		WallObject gameObject = wallObjectspawned.getWallObject();
		Tile tile = wallObjectspawned.getTile();
		WorldPoint tileLocation = tile.getWorldLocation();
		if (gameObject != null) {
			int objDefID = gameObject.getId();
			int orientation = OrientationToAngles(gameObject.getOrientationA());

			LocalPoint location = gameObject.getLocalLocation();
			int tilePosZ = ((Perspective.getHeight(client, location.getX(), location.getY(), tile.getPlane())) * -1);

			Long GameObjectHash = Long.parseLong("" + gameObject.getId() + tile.getPlane()+ tile.getSceneLocation().getX() + tile.getSceneLocation().getY());

			spawnedObjectHashes.add(GameObjectHash);

			ObjectDefinition objDef = loadedObjDefinitions[objDefID];
			int objDefModelIds[] = objDef.getModelIds();

			if (objDefModelIds!= null) {
				UnrealGameObject uGameObject = client.getUGameObjectsMap().get(GameObjectHash);
				if (uGameObject != null) {
					System.out.println("uGameObjectNotNull");
					uGameObject.setWorldPoint(tileLocation);
					uGameObject.setOrientation(orientation);
					uGameObject.setLocalPosX(location.getX());
					uGameObject.setLocalPosY(location.getY());
					uGameObject.setLocalPosZ(tilePosZ);
					if (gameObject.getRenderable2()!= null) {
						uGameObject.setOrientationB(OrientationToAngles(gameObject.getOrientationB()));
					}

					client.getUGameObjectsMap().put(GameObjectHash, uGameObject);

				}
			}
		}*/
	}

	@Subscribe
	private void onWallDecorationSpawned(DecorativeObjectSpawned decorativeObjectSpawned) {
		clientThread.invokeLater(() ->
		{
			Tile tile;
			tile = decorativeObjectSpawned.getTile();


			Buffer actorSpawnPacket = client.createBuffer(new byte[100]);
			int tileObjectModelType = getObjModelTypeFromFlags(decorativeObjectSpawned.getDecorativeObject().getConfig());
			int var4 = (decorativeObjectSpawned.getDecorativeObject().getConfig() >> 6) & 3;

			int rotation = var4;
			int anint = (rotation * 512);

			int objectOrientationA = anint;
			int objectOrientationB = 1234;
			if (tileObjectModelType == 8) { //if wall is objectType 2. walltype 2 has a model B;
				rotation = (decorativeObjectSpawned.getDecorativeObject().getConfig() >> 6) & 3;
				rotation = (rotation + 2 & 3);
				objectOrientationB = (rotation+4) * 512;
				objectOrientationA = ((var4+4)*512);
			}
			int objectDefinitionId = decorativeObjectSpawned.getDecorativeObject().getId();
			int plane = tile.getPlane();
			int tileX = tile.getX();
			int tileY = tile.getY();
			tile.getRenderLevel();
			byte[][][] tileSettings = client.getTileSettings();

			if (plane < Constants.MAX_Z - 1 && (tileSettings[1][tile.getX()][tile.getY()] & TILE_FLAG_BRIDGE) == TILE_FLAG_BRIDGE)
			{
				plane = plane-1;
				plane = plane > 3 ? 3 : plane < 0 ? 0 : plane;
			}

			int height = Perspective.getTileHeight(client, tile.getLocalLocation(), tile.getPlane()) * -1;
			actorSpawnPacket.writeByte(4); //write tileObject data type
			actorSpawnPacket.writeByte(tileObjectModelType);
			actorSpawnPacket.writeByte(var4);
			actorSpawnPacket.writeShort(objectOrientationA);
			actorSpawnPacket.writeShort(objectOrientationB);
			actorSpawnPacket.writeShort(objectDefinitionId);
			actorSpawnPacket.writeByte(plane);
			actorSpawnPacket.writeByte(tileX);
			actorSpawnPacket.writeByte(tileY);
			int tileMinPlane = tile.getPhysicalLevel();
			actorSpawnPacket.writeByte(tileMinPlane);
			actorSpawnPacket.writeShort(height);
			long tag = decorativeObjectSpawned.getDecorativeObject().getHash();
			actorSpawnPacket.writeLong(tag);
			int cycleStart = 0;
			int frame = 0;
			actorSpawnPacket.writeShort(cycleStart);
			actorSpawnPacket.writeShort(frame);
			int offsetX = decorativeObjectSpawned.getDecorativeObject().getXOffset();
			int offsetY = decorativeObjectSpawned.getDecorativeObject().getYOffset();
			actorSpawnPacket.writeShort(offsetX);
			actorSpawnPacket.writeShort(offsetY);

			myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
		});
	}

	public int getTileHeightV2(Client client, LocalPoint point, int plane)
	{
		int LOCAL_COORD_BITS = 7;
		int LOCAL_TILE_SIZE = 1 << LOCAL_COORD_BITS;

		int sceneX = point.getSceneX();
		int sceneY = point.getSceneY();
		if (sceneX >= 0 && sceneY >= 0 && sceneX < Constants.SCENE_SIZE && sceneY < Constants.SCENE_SIZE)
		{
			byte[][][] tileSettings = client.getTileSettings();
			int[][][] tileHeights = client.getTileHeights();

			int z1 = plane;
/*			if (plane < Constants.MAX_Z - 1 && (tileSettings[plane][sceneX][sceneY] & TILE_FLAG_BRIDGE) == TILE_FLAG_BRIDGE)
			{
				z1 = plane + 1;
			}*/

			int x = point.getX() & (LOCAL_TILE_SIZE - 1);
			int y = point.getY() & (LOCAL_TILE_SIZE - 1);
			int var8 = x * tileHeights[z1][sceneX + 1][sceneY] + (LOCAL_TILE_SIZE - x) * tileHeights[z1][sceneX][sceneY] >> LOCAL_COORD_BITS;
			int var9 = tileHeights[z1][sceneX][sceneY + 1] * (LOCAL_TILE_SIZE - x) + x * tileHeights[z1][sceneX + 1][sceneY + 1] >> LOCAL_COORD_BITS;
			return (LOCAL_TILE_SIZE - y) * var8 + y * var9 >> LOCAL_COORD_BITS;
		}

		return 0;
	}

	public static boolean wasInScene(int baseX, int baseY, int x, int y)
	{
		int maxX = baseX + Perspective.SCENE_SIZE;
		int maxY = baseY + Perspective.SCENE_SIZE;

		return x >= baseX && x < maxX && y >= baseY && y < maxY;
	}

	@Subscribe
	private void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned)
	{
		int myId = 2731;
		if (gameObjectSpawned.getGameObject().getId() == myId) {

		}
		//System.out.println("gameObjectType =" + gameObjectSpawned.getGameObject().getType());
		clientThread.invokeLater(() ->
		{
			int isBridgeTile = 0;
			Tile tile;
			tile = gameObjectSpawned.getTile();

			Buffer actorSpawnPacket = client.createBuffer(new byte[100]);
			int tileObjectModelType = getObjModelTypeFromFlags(gameObjectSpawned.getGameObject().getConfig());
			int var4 = (gameObjectSpawned.getGameObject().getConfig() >> 6) & 3;
			int objectOrientationA = gameObjectSpawned.getGameObject().getOrientation().getAngle();
			int objectOrientationB = 65535;
			int objectDefinitionId = gameObjectSpawned.getGameObject().getId();
			if (client.getObjectDefinition(objectDefinitionId) != null) {
				if (client.getObjectDefinition(objectDefinitionId).getImpostorIds() != null) {
					ObjectComposition transformedObjDef = client.getObjectDefinition(objectDefinitionId).getImpostor();
					if (transformedObjDef != null) {
						objectDefinitionId = transformedObjDef.getId();
					}
				}
			}
			int plane = tile.getPlane();
			int tileX = tile.getX();
			int tileY = tile.getY();
			int height = getTileHeightV2(client, tile.getLocalLocation(), gameObjectSpawned.getTile().getRenderLevel()) * -1;
			long tag = gameObjectSpawned.getGameObject().getHash();
			int cycleStart = 0;
			int frame = 0;
			if (gameObjectSpawned.getGameObject().getRenderable() instanceof DynamicObject) {
				DynamicObject dynamicObject  = (DynamicObject) gameObjectSpawned.getGameObject().getRenderable();
				cycleStart = dynamicObject.getAnimCycleCount();
				frame = dynamicObject.getAnimFrame();
			}
			actorSpawnPacket.writeByte(4); //write tileObject data type
			actorSpawnPacket.writeByte(tileObjectModelType);
			actorSpawnPacket.writeByte(var4);
			actorSpawnPacket.writeShort(objectOrientationA);
			actorSpawnPacket.writeShort(objectOrientationB);
			actorSpawnPacket.writeShort(objectDefinitionId);
			actorSpawnPacket.writeByte(plane);
			actorSpawnPacket.writeByte(tileX);
			actorSpawnPacket.writeByte(tileY);
			int tileMinPlane = tile.getPhysicalLevel();
			actorSpawnPacket.writeByte(tileMinPlane);
			actorSpawnPacket.writeShort(height);
			actorSpawnPacket.writeLong(tag);
			actorSpawnPacket.writeShort(cycleStart);
			actorSpawnPacket.writeShort(frame);
			int offsetX = 0;
			int offsetY = 0;
			actorSpawnPacket.writeShort(offsetX);
			actorSpawnPacket.writeShort(offsetY);

			myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
		});

/*		Tile tile = gameObjectSpawned.getTile();
		WorldPoint tilePos = tile.getWorldLocation();
		int tilePosZ = ((Perspective.getTileHeight(client, tile.getLocalLocation(), tile.getPlane())) * -1);

		int tileLocalHeight = ((Perspective.getTileHeight(client, tile.getLocalLocation(), tile.getPlane())) * -1);

		int dist = playerWorldPos.distanceTo(tilePos);

//		if (dist < 15) {
			GameObject gameObject = gameObjectSpawned.getGameObject();
				Model gameObjectModel = gameObject.getModel();
				if (gameObjectModel!=null) {
					if (gameObjectModel.getTrianglesCount() > 0) {
						if (gameObject.getWorldLocation().getY() == tilePos.getY() && gameObject.getWorldLocation().getX() == tilePos.getX()) { //this location check to to prevent getting the same object twice when it is scaled up over multiple squares
						}
						int orientation = (gameObject.getOrientation().getAngle() / 512) * 90;
						int scale = gameObjectModel.getXYZMag();
						int libMeshID = gameObject.getId();
						int libMeshSubID = orientation;
						LocalPoint location = gameObject.getLocalLocation();
						int  locationX = location.getX();
						int  locationY = location.getY();
						String IDAndSubID = libMeshID+"_"+libMeshSubID;
						instanceID = ("" + libMeshID + libMeshSubID + locationX + locationY + tilePosZ);


						if (libMeshIDs.contains(libMeshID) == false) {
							//myRunnable.sendMessage("2_" + ID + "_" + orientation + "_" + GetModelVertData(gameObjectModel));
							sendLibMeshBytes(gameObjectModel, libMeshID, libMeshSubID, orientation);
							libMeshIDs.add(IDAndSubID);
						}

						if (instanceIDs.contains(instanceID) == false) {
							//myRunnable.sendMessage("3_" + ID + "_" + tileCoords + "_" + orientation);
							sendGameObjectBytes(libMeshID,libMeshSubID,locationX,locationY,tilePosZ,orientation,scale,0,0,0);
							instanceIDs.add(instanceID);
						}
					}
				}

//		}*/
	}

	@Subscribe
	private void onGameObjectDeSpawned(GameObjectDespawned event)
	{
		Tile tile;
		if (event.getTile().getBridge()!= null) {
			tile = event.getTile().getBridge();
		} else {
			tile = event.getTile();
		}

		Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

		int tilePlane = tile.getPlane();
		int tileX = tile.getX();
		int tileY = tile.getY();
		long tag = event.getGameObject().getHash();
		actorSpawnPacket.writeByte(4); //write tileObject data type
		actorSpawnPacket.writeByte(tilePlane);
		actorSpawnPacket.writeByte(tileX);
		actorSpawnPacket.writeByte(tileY);
		actorSpawnPacket.writeLong(tag);

		myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorDeSpawn");
	}


	@Subscribe
	private void onGroundObjectSpawned(GroundObjectSpawned groundObjectSpawned) { //GroundObject is aka FloorDecoration
		clientThread.invokeLater(() ->
				{
					int isBridgeTile = 0;
					Tile tile;
					tile = groundObjectSpawned.getTile();

					Buffer actorSpawnPacket = client.createBuffer(new byte[100]);
					int tileObjectModelType = getObjModelTypeFromFlags(groundObjectSpawned.getGroundObject().getConfig());
					int var4 = (groundObjectSpawned.getGroundObject().getConfig() >> 6) & 3;
					int objectOrientationA = var4 * 512;
					int objectOrientationB = -1;
					int objectDefinitionId = groundObjectSpawned.getGroundObject().getId();
					int plane = tile.getPlane();
					int tileX = tile.getX();
					int tileY = tile.getY();
					int height = getTileHeightV2(client, tile.getLocalLocation(), groundObjectSpawned.getTile().getRenderLevel()) * -1;
					long tag = groundObjectSpawned.getGroundObject().getHash();
					int cycleStart = 0;
					int frame = 0;
					actorSpawnPacket.writeByte(4); //write tileObject data type
					actorSpawnPacket.writeByte(tileObjectModelType);
					actorSpawnPacket.writeByte(var4);
					actorSpawnPacket.writeShort(objectOrientationA);
					actorSpawnPacket.writeShort(objectOrientationB);
					actorSpawnPacket.writeShort(objectDefinitionId);
					actorSpawnPacket.writeByte(plane);
					actorSpawnPacket.writeByte(tileX);
					actorSpawnPacket.writeByte(tileY);
					int tileMinPlane = tile.getPhysicalLevel();
					actorSpawnPacket.writeByte(tileMinPlane);
					actorSpawnPacket.writeShort(height);
					actorSpawnPacket.writeLong(tag);
					actorSpawnPacket.writeShort(cycleStart);
					actorSpawnPacket.writeShort(frame);
					int offsetX = 0;
					int offsetY = 0;
					actorSpawnPacket.writeShort(offsetX);
					actorSpawnPacket.writeShort(offsetY);

					myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
				});

/*		counter1 = counter1+1;
		SendLowLatencyData();
		int objDefId = groundObjectSpawned.getGroundObject().getId();
		loadedObjDefinitions[objDefId] = client.getObjectDefinition(objDefId);

		GroundObject gameObject = groundObjectSpawned.getGroundObject();
		Tile tile = groundObjectSpawned.getTile();
		WorldPoint tileLocation = tile.getWorldLocation();
		if (gameObject != null) {

			short[] recolorFrom = client.getObjectDefinition(objDefId).recolorFrom();
			short[] recolorTo= client.getObjectDefinition(objDefId).recolorTo();


			if (recolorFrom != null) {
				if (recolorFrom.length > 0 && objDefId == 1394 ) {
					System.out.println("recolorFrom : [0] "+recolorFrom[0]);

					System.out.println("recolorTo : [0] "+recolorTo[0]);
				}
			}

			int objDefID = gameObject.getId();
			LocalPoint location = gameObject.getLocalLocation();
			int tilePosZ = ((Perspective.getHeight(client, location.getX(), location.getY(), tile.getPlane())) * -1);

			Long GameObjectHash = Long.parseLong("" + gameObject.getId() + tile.getPlane()+ tile.getSceneLocation().getX() + tile.getSceneLocation().getY());

			spawnedObjectHashes.add(GameObjectHash);

			ObjectDefinition objDef = loadedObjDefinitions[objDefID];
			int objDefModelIds[] = objDef.getModelIds();

			if (objDefModelIds!= null) {
				UnrealGameObject uGameObject = client.getUGameObjectsMap().get(GameObjectHash);
				if (uGameObject != null) {
					if (objDefId == 3635) {
						System.out.println("de scale: "+uGameObject.getScaleX() + " "+  uGameObject.getScaleY() + " "+ uGameObject.getScaleHeight());
					}

					System.out.println("uGameObjectNotNull");
					uGameObject.setWorldPoint(tileLocation);
					//uGameObject.setOrientation(orientation);
					uGameObject.setLocalPosX(location.getX());
					uGameObject.setLocalPosY(location.getY());
					uGameObject.setLocalPosZ(tilePosZ);
					client.getUGameObjectsMap().put(GameObjectHash, uGameObject);

				} else {
					System.out.println( "null UgameObjectWith Defid: " + objDefID);
				}
			}
		}*/
	}



	public static final byte[] intToByteArray(int value) {
		return new byte[] {(byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value};
	}

	public static byte[] intListToByteArray(List<Integer> intList) {
		byte[] bytes = new	byte[intList.size()*4]; //since byte encoding is 24 bit, there are 4 bytes for each value //+1 is to acount for trailing byte
		int byteCounter = 0;

		for (int i = 0; i < intList.size(); i++) { //-1 is to account for trailing byte
			int value = intList.get(i);
			bytes[byteCounter+0] = ((byte)(value >>> 24));
			bytes[byteCounter+1] = ((byte)(value >>> 16));
			bytes[byteCounter+2] = ((byte)(value >>> 8));
			bytes[byteCounter+3] = ((byte)value);
			byteCounter = byteCounter + 4;
		}
		return bytes;
	}

	private static byte[] trimmedBufferBytes(Buffer buffer) {
		return Arrays.copyOfRange(buffer.getPayload(), 0, buffer.getOffset());
	}



	@Subscribe
	private void onAnimationChanged(AnimationChanged event) {
		//int actorId = event.getActor().getRSInteracting();
		clientThread.invokeLater(() ->
		{
		clientThread.invokeLater(() ->
		{
			Buffer actorAnimChangePacket = client.createBuffer(new byte[20]);

			int newAnimationID = -1;
			int actorID = -1;
			int actorType = -1;

			int animation = event.getActor().getAnimation();

			if ((event.getActor() instanceof NPC))
			{
				final NPC npc = (NPC) event.getActor();

				//set spot, pose or normal sequence depending on which is not -1;
				if (npc.getAnimation()!= -1) {
					newAnimationID = npc.getAnimation();
				}else {
					if (npc.getPoseAnimation() != -1) {
						if(npc.getPoseAnimation() !=  npc.getLastFrameMovementSequence()) {
							newAnimationID = npc.getPoseAnimation();
							npc.setLastFrameMovementSequence(npc.getPoseAnimation());
						} else {
							return;
						}
					}else
					if (npc.getGraphic() != -1) {
						newAnimationID = npc.getGraphic();
					} else {
						return;
					}
				}
				npc.setLastFrameMovementSequence(newAnimationID);

				//System.out.println("animationchange to: " + newAnimationID);

				actorID = npc.getIndex();
				actorType = 1;

				actorAnimChangePacket.writeShort(newAnimationID);//write sequenceDef id;
				actorAnimChangePacket.writeShort(actorID);//write actor id;
				actorAnimChangePacket.writeByte(actorType); //write actor type. 1 = npc;
				//Util.sleep(1);
				myRunnableSender.sendBytes(trimmedBufferBytes(actorAnimChangePacket), "ActorAnimationChange");
			} else {
				if ((event.getActor() instanceof Player))
				{
					final Player player = (Player) event.getActor();

					//set spot, pose or normal sequence depending on which is not -1;
					if (player.getAnimation()!= -1) {
						newAnimationID = player.getAnimation();
					}else {
						if (player.getPoseAnimation() != -1) {
							if(player.getPoseAnimation() !=  player.getLastFrameMovementSequence()) {
								newAnimationID = player.getPoseAnimation();
								player.setLastFrameMovementSequence(player.getPoseAnimation());
							} else {
								return;
							}
						}else
						if (player.getGraphic() != -1) {
							newAnimationID = player.getGraphic();
						} else {
							return;
						}
					}
					player.setLastFrameMovementSequence(newAnimationID);

					//System.out.println("animationchange to: " + newAnimationID);

					actorID = player.getPlayerId();
					actorType = 2;

					actorAnimChangePacket.writeShort(newAnimationID);//write sequenceDef id;
					actorAnimChangePacket.writeShort(actorID);//write actor id;
					actorAnimChangePacket.writeByte(actorType); //write actor type. 1 = npc;

					myRunnableSender.sendBytes(trimmedBufferBytes(actorAnimChangePacket), "ActorAnimationChange");
				}
			}
		});
		});
	}

	@Subscribe
	private void onItemSpawned(ItemSpawned event)
	{
		clientThread.invokeLater(() ->
		{
			Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

			int tilePlane = event.getTile().getPlane();
			int tileX = event.getTile().getX();
			int tileY = event.getTile().getY();
			int height = Perspective.getTileHeight(client, event.getTile().getLocalLocation(), client.getPlane()) * -1;
			int itemDefinitionId = event.getItem().getId();
			int itemQuantity = event.getItem().getQuantity();
			actorSpawnPacket.writeByte(3); //write tileItem data type
			actorSpawnPacket.writeByte(tilePlane);
			actorSpawnPacket.writeByte(tileX);
			actorSpawnPacket.writeByte(tileY);
			actorSpawnPacket.writeShort(height);
			actorSpawnPacket.writeShort(itemDefinitionId);
			actorSpawnPacket.writeShort(itemQuantity);

			myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
		});
	}
	@Subscribe
	private void onItemDeSpawned(ItemDespawned event)
	{
		Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

		int tilePlane = event.getTile().getPlane();
		int tileX = event.getTile().getX();
		int tileY = event.getTile().getY();
		int itemDefinitionId = event.getItem().getId();
		actorSpawnPacket.writeByte(3); //write tileItem data type
		actorSpawnPacket.writeByte(tilePlane);
		actorSpawnPacket.writeByte(tileX);
		actorSpawnPacket.writeByte(tileY);
		actorSpawnPacket.writeShort(itemDefinitionId);

		myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorDeSpawn");
	}

	@Subscribe
	private void onNpcSpawned(NpcSpawned event) {
		Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

		int instanceId = event.getNpc().getIndex();
		int definitionId = event.getNpc().getId();
		actorSpawnPacket.writeByte(1); //write npc data type
		actorSpawnPacket.writeShort(instanceId);
		actorSpawnPacket.writeShort(definitionId);

		myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
	}

	@Subscribe
	private void onNpcDeSpawned(NpcDespawned event) {
		Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

		int instanceId = event.getNpc().getIndex();
		actorSpawnPacket.writeByte(1); //write npc data type
		actorSpawnPacket.writeShort(instanceId);

		//write bogus packet so len is more than 1

		myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorDeSpawn");
	}

	@Subscribe
	private void onPlayerChanged(PlayerChanged event) {
		Player player = event.getPlayer();
		Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

		int InstanceId = event.getPlayer().getPlayerId();
		actorSpawnPacket.writeByte(2); //write player data type
		actorSpawnPacket.writeShort(InstanceId);

		int[] equipmentIds = player.getPlayerComposition().getEquipmentIds();
		for (int i = 0; i < 12; i++)//equipment
		{
			actorSpawnPacket.writeInt(equipmentIds[i]);
		}

		int[] bodyColors = player.getPlayerComposition().getBodyPartColours();
		for (int i = 0; i < 5; i++) //bodyColors
		{
			actorSpawnPacket.writeByte(bodyColors[i]);
		}
		byte isFemale = 0;
		if (player.getPlayerComposition().isFemale()) isFemale = 1;
		actorSpawnPacket.writeByte(isFemale); //isFemale

		actorSpawnPacket.writeInt(player.getPlayerComposition().getTransformedNpcId()); //npcTransformID

		System.out.println("player change. id: "+InstanceId);
		myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
	}

	@Subscribe
	private void onPlayerSpawned(PlayerSpawned event) {
		Player player = event.getPlayer();
		Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

		int InstanceId = event.getPlayer().getPlayerId();
		actorSpawnPacket.writeByte(2); //write player data type
		actorSpawnPacket.writeShort(InstanceId);

		int[] equipmentIds = player.getPlayerComposition().getEquipmentIds();
		for (int i = 0; i < 12; i++)//equipment
		{
			actorSpawnPacket.writeInt(equipmentIds[i]);
		}

		int[] bodyColors = player.getPlayerComposition().getBodyPartColours();
		for (int i = 0; i < 5; i++) //bodyColors
		{
			actorSpawnPacket.writeByte(bodyColors[i]);
		}
		byte isFemale = 0;
		if (player.getPlayerComposition().isFemale()) isFemale = 1;
		actorSpawnPacket.writeByte(isFemale); //isFemale

		actorSpawnPacket.writeInt(player.getPlayerComposition().getTransformedNpcId()); //npcTransformID

		System.out.println("player spawn. id: "+InstanceId);
		myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorSpawn");
	}

	@Subscribe
	private void onPlayerDeSpawned(PlayerDespawned event) {
		Buffer actorSpawnPacket = client.createBuffer(new byte[100]);

		int instanceId = event.getPlayer().getPlayerId();
		actorSpawnPacket.writeByte(2); //write player data type
		actorSpawnPacket.writeShort(instanceId);

		myRunnableSender.sendBytes(trimmedBufferBytes(actorSpawnPacket), "ActorDeSpawn");
	}

	@Subscribe
	private void onCanvasSizeChanged(CanvasSizeChanged event) {
		canvasSizeChanged = true;
	}

	private void sendPlaneChanged() {
			System.out.println("PlaneChanged");
			Buffer buffer = client.createBuffer(new byte[4]);
			buffer.writeByte(clientPlane);
			myRunnableSender.sendBytes(buffer.getPayload(), "PlaneChanged");
	}

	@Subscribe
	private void gameObjectChanged(GameObjectChanged event)
	{
	}

	@Subscribe
	private void onDrawFinished_Ui(DrawFinished_Ui event)
	{
		SendPerFramePacket();
	}

	@Subscribe
	private void onClientTick(ClientTick event)
	{
/*		WorldPoint area0 = new WorldPoint(1988, 5111, 0);
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

		if (playerLocation.distanceTo(area0) < 150) {
			LocalPoint oculusPosition = LocalPoint.fromWorld(client, area0);
			//now set the occulus position
		}*/
		if (newRegionLoaded) {
			SendPerFramePacket();
			sendTerrainData();
			newRegionLoaded = false;
		}
	}


	private void SendPerFramePacket() {

		//System.out.println("canvasSizeChanged"+viewWidth+" X "+viewHeight);
		if (client.getPlane()!= clientPlane || clientJustConnected) {
			clientPlane = client.getPlane();
			sendPlaneChanged();
		}

		rsclient_ui_pixels_shared_memory.setDataLength(client.getGraphicsPixels().length*4); //write data length
		int viewX = client.getCanvas().getSize().width;
		int viewY = client.getCanvas().getSize().height;
		rsclient_ui_pixels_shared_memory.setInt_BigEndian(4,viewX); //write data length
		rsclient_ui_pixels_shared_memory.setInt_BigEndian(8,viewY); //write data length
		rsclient_ui_pixels_shared_memory.SharedMemoryData.write(12, client.getGraphicsPixels(),0, client.getGraphicsPixels().length); //write pixel data

		if (client.getLocalPlayer() == null) {
			//System.out.println("not logged in yet");
			return;
		}

		byte[] bytes = new byte[10000];
		Buffer perFramePacket = client.createBuffer(bytes);

		int camX = client.getCameraX();
		int camY = client.getCameraY();
		int camZ = client.getCameraZ();
		int camYaw = client.getCameraYaw();
		int camPitch = client.getCameraPitch();
		int camZoom = client.getScale();
		int clientBaseX = client.getBaseX();
		int clientBaseY = client.getBaseY();
		int clientPlane = client.getPlane();
		int maxVisiblePlane = client.getMaxScenePlane();
		int clientCycle = client.getGameCycle();

		int playerLocalX = client.getLocalPlayer().getLocalLocation().getX();
		int playerLocalY = client.getLocalPlayer().getLocalLocation().getY();
		int playerZ = Perspective.getTileHeight(client, client.getLocalPlayer().getLocalLocation(), client.getPlane());


		//stringToSend = "0_"+client.getBaseX() + "," + client.getBaseY() + "," + "0" + "_" + playerLocalX + "," + playerLocalY + "," + playerZ + "_" + "0" + "," + camPitch + "," + camYaw + "_" + camX + "," + camY + "," + camZ+"_"+camZoom;


		perFramePacket.writeShort(camX);
		perFramePacket.writeShort(camY);
		perFramePacket.writeShort(camZ);
		perFramePacket.writeShort(camYaw);
		perFramePacket.writeShort(camPitch);
		perFramePacket.writeShort(camZoom);
		perFramePacket.writeShort(clientBaseX);
		perFramePacket.writeShort(clientBaseY);
		perFramePacket.writeByte(clientPlane);
		perFramePacket.writeByte(maxVisiblePlane);
		perFramePacket.writeInt(clientCycle);


		List<NPC> npcs = client.getNpcs();

		int npcCount = client.getNpcs().size();
		perFramePacket.writeShort(npcCount);

		if (npcCount > 0) {
			for (int i = 0; i < npcCount; i++ ) {
				NPC npc = client.getNpcs().get(i);
				int npcInstanceId = npc.getIndex();
				int npcDefinitionId = npc.getId();
				int npcX = npc.getLocalLocation().getX();
				int npcY = npc.getLocalLocation().getY();
				int npcHeight = Perspective.getTileHeight(client, npc.getLocalLocation(), client.getPlane())*-1;
				int npcOrientation = npc.getCurrentOrientation();

				int npcAnimation = -1;
				int npcAnimationFrame = -1;
				int npcAnimationFrameCycle = -1;

				//set animation ints depending on which animation type is not null (spot, pose or normal)
				if (npc.getAnimation()!= -1) {
					//npcAnimation = npc.getAnimation();
					npcAnimationFrame = npc.getActionFrame();
					npcAnimationFrameCycle = npc.getActionFrameCycle();
				}else {
					if (npc.getPoseAnimation() != -1) {
						//npcAnimation = npc.getPoseAnimation();
						npcAnimationFrame = npc.getPoseFrame();
						npcAnimationFrameCycle = npc.getPoseFrameCycle();
					}else
					if (npc.getGraphic() != -1) {
						//npcAnimation = npc.getGraphic();
						npcAnimationFrame = npc.getSpotAnimFrame();
						npcAnimationFrameCycle = npc.getSpotAnimationFrameCycle();
					}
				}

				perFramePacket.writeShort(npcInstanceId);
				perFramePacket.writeShort(npcDefinitionId);
				perFramePacket.writeShort(npcX);
				perFramePacket.writeShort(npcY);
				perFramePacket.writeShort(npcHeight);
				perFramePacket.writeShort(npcOrientation);
				perFramePacket.writeShort(npcAnimation);
				perFramePacket.writeShort(npcAnimationFrame);
				perFramePacket.writeShort(npcAnimationFrameCycle);
			}
		}

		//players
		int playerCount = client.getPlayers().size();
		perFramePacket.writeShort(playerCount);
		if (playerCount > 0) {
			for (int i = 0; i < playerCount; i++ ) {
				Player player = client.getPlayers().get(i);
				int playerInstanceId =player.getPlayerId();
				int playerX = player.getLocalLocation().getX();
				int playerY = player.getLocalLocation().getY();
				int playerHeight = Perspective.getTileHeight(client, player.getLocalLocation(), client.getPlane())*-1;
				int playerOrientation = player.getCurrentOrientation();
/*				if (playerInstanceId == client.getLocalPlayerIndex()) {
					System.out.println(playerOrientation);
				}*/
				int animation = -1;
				int animationFrame = -1;
				int animationFrameCycle = -1;

				//set animation ints depending on which animation type is not null (spot, pose or normal)
				if (player.getAnimation()!= -1) {
					//npcAnimation = npc.getAnimation();
					animationFrame = player.getActionFrame();
					animationFrameCycle = player.getActionFrameCycle();
				}else {
					if (player.getPoseAnimation() != -1) {
						//npcAnimation = npc.getPoseAnimation();
						animationFrame = player.getPoseFrame();
						animationFrameCycle = player.getPoseFrameCycle();
					}else
					if (player.getGraphic() != -1) {
						//npcAnimation = npc.getGraphic();
						animationFrame = player.getGraphic();
						animationFrameCycle = player.getSpotAnimationFrameCycle();
					}
				}

				perFramePacket.writeShort(playerInstanceId);
				perFramePacket.writeShort(playerX);
				perFramePacket.writeShort(playerY);
				perFramePacket.writeShort(playerHeight);
				perFramePacket.writeShort(playerOrientation);
				perFramePacket.writeShort(animation);
				perFramePacket.writeShort(animationFrame);
				perFramePacket.writeShort(animationFrameCycle);
			}
		}

		myRunnableSender.sendBytes(trimmedBufferBytes(perFramePacket), "PerFramePacket");

		if (canvasSizeChanged||clientJustConnected) {
			clientThread.invokeLater(() -> {
				Buffer canvasSizeBuffer = client.createBuffer(new byte[4]);
				myRunnableSender.sendBytes(canvasSizeBuffer.getPayload(), "CanvasSizeChanged");
				canvasSizeChanged = false;
			});


		}
		if (clientJustConnected) {
			System.out.println("Client Just Connected");
			clientJustConnected = false;
		}


/*		int worldX = client.getLocalPlayer().getWorldLocation().getX();
		int worldY = client.getLocalPlayer().getWorldLocation().getY();
		int worldZ = client.getLocalPlayer().getWorldLocation().getPlane();

		int camX = client.getCameraX();
		int camY = client.getCameraY();
		int camZ = client.getCameraZ();
		int camYaw = client.getCameraYaw();
		int camPitch = client.getCameraPitch();
		int camZoom = client.getScale();
		int viewWidth = client.getViewportWidth();
		int viewHeight = client.getViewportHeight();
		int viewX = client.getCanvas().getLocationOnScreen().x;
		int viewY = client.getCanvas().getLocationOnScreen().y;

		int playerLocalX = client.getLocalPlayer().getLocalLocation().getX();
		int playerLocalY = client.getLocalPlayer().getLocalLocation().getY();
		int playerZ = Perspective.getTileHeight(client, client.getLocalPlayer().getLocalLocation(), client.getPlane());

		int windowIsFocused = 0;
		if (clientUI.isFocused()) windowIsFocused = 1;


		stringToSend = "0_"+client.getBaseX() + "," + client.getBaseY() + "," + "0" + "_" + playerLocalX + "," + playerLocalY + "," + playerZ + "_" + "0" + "," + camPitch + "," + camYaw + "_" + camX + "," + camY + "," + camZ+"_"+camZoom;
		myRunnableSender.sendMessage(stringToSend);

		lastFrameWorldX = worldX;
		lastFrameWorldY = worldY;
		lastFrameWorldZ = worldZ;*/
	}

	private static Color rs2hsbToColor(int hsb)
	{
		int decode_hue = (hsb >> 10) & 0x3f;
		int decode_saturation = (hsb >> 7) & 0x07;
		int decode_brightness = (hsb & 0x7f);
		return Color.getHSBColor((float) decode_hue / 63, (float) decode_saturation / 7, (float) decode_brightness / 127);
	}

	private static void BGRToCol(int BGR)
	{
		int r = (BGR >> 16) & 0xFF/255;
		int g = (BGR >> 8) & 0xFF/255;
		int b = BGR & 0xFF/255;

	}
}

