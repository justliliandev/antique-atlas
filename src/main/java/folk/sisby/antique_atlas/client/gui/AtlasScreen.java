package folk.sisby.antique_atlas.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.api.AtlasAPI;
import folk.sisby.antique_atlas.client.AntiqueAtlasTextures;
import folk.sisby.antique_atlas.client.MarkerType;
import folk.sisby.antique_atlas.client.api.AtlasClientAPI;
import folk.sisby.antique_atlas.client.assets.BiomeTextures;
import folk.sisby.antique_atlas.client.assets.MarkerTypes;
import folk.sisby.antique_atlas.client.gui.core.ButtonComponent;
import folk.sisby.antique_atlas.client.gui.core.Component;
import folk.sisby.antique_atlas.client.gui.core.CursorComponent;
import folk.sisby.antique_atlas.client.gui.core.IButtonListener;
import folk.sisby.antique_atlas.client.gui.core.ScreenState;
import folk.sisby.antique_atlas.client.gui.core.ScreenState.IState;
import folk.sisby.antique_atlas.client.gui.core.ScreenState.SimpleState;
import folk.sisby.antique_atlas.client.gui.core.ScrollBoxComponent;
import folk.sisby.antique_atlas.client.gui.tiles.SubTile;
import folk.sisby.antique_atlas.client.gui.tiles.SubTileQuartet;
import folk.sisby.antique_atlas.client.gui.tiles.TileRenderIterator;
import folk.sisby.antique_atlas.client.texture.TileTexture;
import folk.sisby.antique_atlas.core.WorldData;
import folk.sisby.antique_atlas.marker.DimensionMarkersData;
import folk.sisby.antique_atlas.marker.Marker;
import folk.sisby.antique_atlas.marker.MarkersData;
import folk.sisby.antique_atlas.util.MathUtil;
import folk.sisby.antique_atlas.util.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AtlasScreen extends Component {
    public static final int WIDTH = 310;
    public static final int HEIGHT = 218;

    private static final int MAP_BORDER_WIDTH = 17;
    private static final int MAP_BORDER_HEIGHT = 11;
    private static final int MAP_WIDTH = WIDTH - MAP_BORDER_WIDTH * 2;
    private static final int MAP_HEIGHT = 194;

    private static final float PLAYER_ROTATION_STEPS = 16;
    private static final int PLAYER_ICON_WIDTH = 7;
    private static final int PLAYER_ICON_HEIGHT = 8;

    public static final int MARKER_SIZE = 32;

    /**
     * If the map scale goes below this value, the tiles will not scale down
     * visually, but will instead span greater area.
     */
    private static final double MIN_SCALE_THRESHOLD = 0.5;

    private final long[] renderTimes = new long[30];

    private int renderTimesIndex = 0;

    // States ==================================================================

    private final ScreenState state = new ScreenState();

    /**
     * If on, navigate the map normally.
     */
    private final IState NORMAL = new SimpleState();

    /**
     * If on, all markers as well as the player icon are hidden.
     */
    private final IState HIDING_MARKERS = new IState() {
        @Override
        public void onEnterState() {
            // Set the button as not selected so that it can be clicked again:
            btnShowMarkers.setSelected(false);
            btnShowMarkers.setTitle(Text.translatable("gui.antique_atlas.showMarkers"));
            btnShowMarkers.setIconTexture(AntiqueAtlasTextures.ICON_SHOW_MARKERS);
        }

        @Override
        public void onExitState() {
            btnShowMarkers.setSelected(false);
            btnShowMarkers.setTitle(Text.translatable("gui.antique_atlas.hideMarkers"));
            btnShowMarkers.setIconTexture(AntiqueAtlasTextures.ICON_HIDE_MARKERS);
        }
    };

    /**
     * If on, a semi-transparent marker is attached to the cursor, and the
     * player's icon becomes semi-transparent as well.
     */
    private final IState PLACING_MARKER = new IState() {
        @Override
        public void onEnterState() {
            btnMarker.setSelected(true);
        }

        @Override
        public void onExitState() {
            btnMarker.setSelected(false);
        }
    };

    /**
     * If on, the closest marker will be deleted upon mouseclick.
     */
    private final IState DELETING_MARKER = new IState() {
        @Override
        public void onEnterState() {
            addChild(eraser);
            btnDelMarker.setSelected(true);
        }

        @Override
        public void onExitState() {
            removeChild(eraser);
            btnDelMarker.setSelected(false);
        }
    };
    private final CursorComponent eraser = new CursorComponent();

    // Buttons =================================================================

    /**
     * Button for placing a marker at current position, local to this Atlas instance.
     */
    private final BookmarkComponent btnMarker;

    /**
     * Button for deleting local markers.
     */
    private final BookmarkComponent btnDelMarker;

    /**
     * Button for showing/hiding all markers.
     */
    private final BookmarkComponent btnShowMarkers;

    /**
     * Button for restoring player's position at the center of the Atlas.
     */
    private final FollowButtonComponent btnPosition;


    // Navigation ==============================================================

    /**
     * How much the map view is offset, in blocks, per click (or per tick).
     */
    private static final int navigateStep = 24;

    /**
     * The button which is currently being pressed. Used for continuous
     * navigation using the arrow buttons. Also used to prevent immediate
     * canceling of placing marker.
     */
    private ButtonComponent selectedButton = null;

    /**
     * Set to true when dragging the map view.
     */
    private boolean isDragging = false;

    /**
     * Offset to the top left corner of the tile at (0, 0) from the center of
     * the map drawing area, in pixels.
     */
    private int mapOffsetX, mapOffsetY;

    /**
     * When dragging, this saves the partly updates of the mapOffset.
     * Turns out, mouse dragging events are too precise.
     */
    private double mapOffsetDeltaX, mapOffsetDeltaY;

    private Integer targetOffsetX, targetOffsetY;
    /**
     * If true, the player's icon will be in the center of the GUI, and the
     * offset of the tiles will be calculated accordingly. Otherwise it's the
     * position of the player that will be calculated with respect to the
     * offset.
     */
    private boolean followPlayer;

    private final BarScaleComponent scaleBar = new BarScaleComponent();

    private final ScrollBoxComponent markers = new ScrollBoxComponent();

    /**
     * Pixel-to-block ratio.
     */
    private double mapScale;
    /**
     * The visual size of a tile in pixels.
     */
    private int tileHalfSize;
    /**
     * The number of chunks a tile spans.
     */
    private int tile2ChunkScale;


    // Markers =================================================================

    /**
     * Local markers in the current dimension
     */
    private DimensionMarkersData localMarkersData;
    /**
     * Global markers in the current dimension
     */
    private DimensionMarkersData globalMarkersData;
    /**
     * The marker highlighted by the eraser. Even though multiple markers may
     * be highlighted at the same time, only one of them will be deleted.
     */
    private Marker hoveredMarker;

    private final MarkerModalComponent markerFinalizer = new MarkerModalComponent();
    /**
     * Displayed where the marker is about to be placed when the Finalizer GUI is on.
     */
    private final BlinkingMarkerComponent blinkingIcon = new BlinkingMarkerComponent();

    // Misc stuff ==============================================================

    private PlayerEntity player;
    private WorldData biomeData;

    /**
     * Coordinate scale factor relative to the actual screen size.
     */
    private double screenScale;

    private long lastUpdateMillis = System.currentTimeMillis();
    private int scaleAlpha = 255;
    private int scaleClipIndex = 0;
    private final int zoomLevelOne = 8;
    private int zoomLevel = zoomLevelOne;
    private final String[] zoomNames = new String[]{"256", "128", "64", "32", "16", "8", "4", "2", "1", "1/2", "1/4", "1/8", "1/16", "1/32", "1/64", "1/128", "1/256"};

    @SuppressWarnings("rawtypes")
    public AtlasScreen() {
        setSize(WIDTH, HEIGHT);
        setMapScale(0.5);
        followPlayer = true;

        btnPosition = new FollowButtonComponent();
        btnPosition.setEnabled(!followPlayer);
        addChild(btnPosition).offsetGuiCoords(283, 194);
        IButtonListener positionListener = button -> {
            selectedButton = button;
            if (button.equals(btnPosition)) {
                followPlayer = true;
                targetOffsetX = null;
                targetOffsetY = null;
                btnPosition.setEnabled(false);
            }
        };
        btnPosition.addListener(positionListener);

        btnMarker = new BookmarkComponent(0, AntiqueAtlasTextures.ICON_ADD_MARKER, Text.translatable("gui.antique_atlas.addMarker"));
        addChild(btnMarker).offsetGuiCoords(300, 14);
        btnMarker.addListener(button -> {
            if (state.is(PLACING_MARKER)) {
                selectedButton = null;
                state.switchTo(NORMAL);
            } else {
                selectedButton = button;
                state.switchTo(PLACING_MARKER);

                // While holding shift, we create a marker on the player's position
                if (hasShiftDown()) {
                    markerFinalizer.setMarkerData(player.getEntityWorld(),
                        getAtlasID(),
                        player.getBlockX(), player.getBlockZ());
                    addChild(markerFinalizer);

                    blinkingIcon.setTexture(markerFinalizer.selectedType.getTexture(),
                        MARKER_SIZE, MARKER_SIZE);
                    addChildBehind(markerFinalizer, blinkingIcon)
                        .setRelativeCoords(worldXToScreenX((int) player.getX()) - getGuiX() - MARKER_SIZE / 2,
                            worldZToScreenY((int) player.getZ()) - getGuiY() - MARKER_SIZE / 2);

                    // Un-press all keys to prevent player from walking infinitely:
                    KeyBinding.unpressAll();

                    selectedButton = null;
                    state.switchTo(NORMAL);
                }
            }
        });
        btnDelMarker = new BookmarkComponent(2, AntiqueAtlasTextures.ICON_DELETE_MARKER, Text.translatable("gui.antique_atlas.delMarker"));
        addChild(btnDelMarker).offsetGuiCoords(300, 33);
        btnDelMarker.addListener(button -> {
            if (state.is(DELETING_MARKER)) {
                selectedButton = null;
                state.switchTo(NORMAL);
            } else {
                selectedButton = button;
                state.switchTo(DELETING_MARKER);
            }
        });
        btnShowMarkers = new BookmarkComponent(3, AntiqueAtlasTextures.ICON_HIDE_MARKERS, Text.translatable("gui.antique_atlas.hideMarkers"));
        addChild(btnShowMarkers).offsetGuiCoords(300, 52);
        btnShowMarkers.addListener(button -> {
            selectedButton = null;
            if (state.is(HIDING_MARKERS)) {
                state.switchTo(NORMAL);
            } else {
                selectedButton = null;
                state.switchTo(HIDING_MARKERS);
            }
        });

        addChild(scaleBar).offsetGuiCoords(20, 198);
        scaleBar.setMapScale(1);

        addChild(markers).setRelativeCoords(-10, 14);
        markers.setViewportSize(21, 180);
        markers.setWheelScrollsVertically();

        markerFinalizer.addMarkerListener(blinkingIcon);

        eraser.setTexture(AntiqueAtlasTextures.ERASER, 12, 14, 2, 11);

        state.switchTo(NORMAL);
    }

    public AtlasScreen prepareToOpen() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));

        this.player = MinecraftClient.getInstance().player;
        updateAtlasData();

        return this;
    }

    @Override
    public void init() {
        super.init();

        screenScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        setCentered();

        updateBookmarkerList();
    }

    public void updateBookmarkerList() {
        markers.removeAllContent();
        markers.scrollTo(0, 0);

        if (localMarkersData == null) return;


        int contentY = 0;
        for (Marker marker : localMarkersData.getAllMarkers()) {
            if (!marker.isVisibleAhead() || marker.isGlobal()) {
                continue;
            }
            MarkerBookmarkComponent bookmark = new MarkerBookmarkComponent(marker);

            bookmark.addListener(button -> {
                if (state.is(NORMAL)) {
                    setTargetPosition(marker.getX(), marker.getZ());
                    followPlayer = false;
                    btnPosition.setEnabled(true);
                } else if (state.is(DELETING_MARKER)) {
                    AtlasClientAPI.getMarkerAPI().deleteMarker(player.getEntityWorld(),
                        getAtlasID(), marker.getId());
                    player.getEntityWorld().playSound(player, player.getBlockPos(),
                        SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.AMBIENT,
                        1F, 0.5F);
                    state.switchTo(NORMAL);
                }
            });

            markers.addContent(bookmark).setRelativeY(contentY);
            contentY += 18 + 2;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseState) {
        boolean result = super.mouseClicked(mouseX, mouseY, mouseState);

        if (result) {
            return true;
        }

        // close atlas with right-click
        if (mouseState == 1 && state.is(NORMAL)) {
            close();
            return true;
        }

        // If clicked on the map, start dragging
        int mapX = (width - MAP_WIDTH) / 2;
        int mapY = (height - MAP_HEIGHT) / 2;
        boolean isMouseOverMap = mouseX >= mapX && mouseX <= mapX + MAP_WIDTH &&
            mouseY >= mapY && mouseY <= mapY + MAP_HEIGHT;
        if (!state.is(NORMAL) && !state.is(HIDING_MARKERS)) {
            int atlasID = getAtlasID();

            if (state.is(PLACING_MARKER) // If clicked on the map, place marker:
                && isMouseOverMap && mouseState == 0 /* left click */) {
                markerFinalizer.setMarkerData(player.getEntityWorld(), atlasID,
                    screenXToWorldX((int) mouseX), screenYToWorldZ((int) mouseY));
                addChild(markerFinalizer);

                blinkingIcon.setTexture(markerFinalizer.selectedType.getTexture(),
                    MARKER_SIZE, MARKER_SIZE);
                addChildBehind(markerFinalizer, blinkingIcon)
                    .setRelativeCoords((int) mouseX - getGuiX() - MARKER_SIZE / 2,
                        (int) mouseY - getGuiY() - MARKER_SIZE / 2);

                // Un-press all keys to prevent player from walking infinitely:
                KeyBinding.unpressAll();

                state.switchTo(NORMAL);
                return true;
            } else if (state.is(DELETING_MARKER) // If clicked on a marker, delete it:
                && hoveredMarker != null && !hoveredMarker.isGlobal() && isMouseOverMap && mouseState == 0) {
                AtlasClientAPI.getMarkerAPI().deleteMarker(player.getEntityWorld(),
                    atlasID, hoveredMarker.getId());
                hoveredMarker = null;
                player.getEntityWorld().playSound(player, player.getBlockPos(),
                    SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.AMBIENT,
                    1F, 0.5F);
            }
            state.switchTo(NORMAL);
        } else if (isMouseOverMap && selectedButton == null) {
            if (hoveredMarker == null /* API: Marker Clicked Event */) {
                isDragging = true;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) {
            navigateMap(0, navigateStep);
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            navigateMap(0, -navigateStep);
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            navigateMap(navigateStep, 0);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            navigateMap(-navigateStep, 0);
        } else if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            setMapScale(mapScale * 2);
        } else if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            setMapScale(mapScale / 2);
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        } else {
            KeyBinding[] hotbarKeys = MinecraftClient.getInstance().options.hotbarKeys;
            for (KeyBinding bind : hotbarKeys) {
                // only handle hotbarkeys when marker gui isn't shown1
                if (bind.matchesKey(keyCode, scanCode) && this.markerFinalizer.getParent() == null) {
                    close();
                    // if we close the gui, then don't handle the event
                    return false;
                }
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double wheelMove) {
        double origWheelMove = wheelMove;

        boolean handled = super.mouseScrolled(mx, my, origWheelMove);

        if (!handled && wheelMove != 0) {
            wheelMove = wheelMove > 0 ? 1 : -1;
            if (AntiqueAtlas.CONFIG.Interface.doReverseWheelZoom) {
                wheelMove *= -1;
            }

            double mouseOffsetX = MinecraftClient.getInstance().getWindow().getFramebufferWidth() / screenScale / 2 - getMouseX();
            double mouseOffsetY = MinecraftClient.getInstance().getWindow().getFramebufferHeight() / screenScale / 2 - getMouseY();
            double newScale = mapScale * Math.pow(2, wheelMove);
            double addOffsetX = 0;
            double addOffsetY = 0;
            if (Math.abs(mouseOffsetX) < MAP_WIDTH / 2f && Math.abs(mouseOffsetY) < MAP_HEIGHT / 2f) {
                addOffsetX = mouseOffsetX * wheelMove;
                addOffsetY = mouseOffsetY * wheelMove;

                if (wheelMove > 0) {
                    addOffsetX *= mapScale / newScale;
                    addOffsetY *= mapScale / newScale;
                }
            }

            setMapScale(newScale, (int) addOffsetX, (int) addOffsetY);

            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));

            return true;
        }

        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseState) {
        boolean result = false;
        if (mouseState != -1) {
            result = selectedButton != null || isDragging;
            selectedButton = null;
            isDragging = false;
            mapOffsetDeltaX = 0;
            mapOffsetDeltaY = 0;
        }
        return super.mouseReleased(mouseX, mouseY, mouseState) || result;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int lastMouseButton, double deltaX, double deltaY) {
        boolean result = false;
        if (isDragging) {
            followPlayer = false;
            btnPosition.setEnabled(true);

            mapOffsetDeltaX += deltaX;
            mapOffsetDeltaY += deltaY;

            int offsetX = (int) (Math.signum(mapOffsetDeltaX) * Math.floor(Math.abs(mapOffsetDeltaX)));
            int offsetY = (int) (Math.signum(mapOffsetDeltaY) * Math.floor(Math.abs(mapOffsetDeltaY)));

            if (Math.abs(mapOffsetDeltaX) >= 1) {
                mapOffsetDeltaX = mapOffsetDeltaX - offsetX;
                mapOffsetX += offsetX;
            }

            if (Math.abs(mapOffsetDeltaY) >= 1) {
                mapOffsetDeltaY = mapOffsetDeltaY - offsetY;
                mapOffsetY += offsetY;
            }

            result = true;
        }
        return super.mouseDragged(mouseX, mouseY, lastMouseButton, deltaX, deltaY) || result;
    }

    @Override
    public void tick() {
        super.tick();
        if (player == null) return;
        if (followPlayer) {
            setMapPosition(player.getBlockX(), player.getBlockZ());
        }

        if (targetOffsetX != null) {
            if (Math.abs(getTargetPositionX() - mapOffsetX) > navigateStep) {
                navigateMap(getTargetPositionX() > mapOffsetX ? navigateStep : -navigateStep, 0);
            } else {
                mapOffsetX = getTargetPositionX();
                targetOffsetX = null;
            }
        }

        if (targetOffsetY != null) {
            if (Math.abs(getTargetPositionY() - mapOffsetY) > navigateStep) {
                navigateMap(0, getTargetPositionY() > mapOffsetY ? navigateStep : -navigateStep);
            } else {
                mapOffsetY = getTargetPositionY();
                targetOffsetY = null;
            }
        }

        updateAtlasData();
    }

    /**
     * Update {@link #biomeData}, {@link #localMarkersData},
     * {@link #globalMarkersData}
     */
    private void updateAtlasData() {
        int atlasID = getAtlasID();

        biomeData = AntiqueAtlas.tileData
            .getData(atlasID, player.getEntityWorld())
            .getWorldData(player.getEntityWorld().getRegistryKey());
        globalMarkersData = AntiqueAtlas.globalMarkersData.getData()
            .getMarkersDataInWorld(player.getEntityWorld().getRegistryKey());
        MarkersData markersData = AntiqueAtlas.markersData
            .getMarkersData(atlasID, player.getEntityWorld());
        if (markersData != null) {
            localMarkersData = markersData
                .getMarkersDataInWorld(player.getEntityWorld().getRegistryKey());
        } else {
            localMarkersData = null;
        }
    }

    /**
     * Offset the map view by given values, in blocks.
     */
    private void navigateMap(int dx, int dy) {
        mapOffsetX += dx;
        mapOffsetY += dy;
        followPlayer = false;
        btnPosition.setEnabled(true);
    }

    private void setMapPosition(int x, int z) {
        mapOffsetX = (int) (-x * mapScale);
        mapOffsetY = (int) (-z * mapScale);
    }

    private void setTargetPosition(int x, int z) {
        targetOffsetX = x;
        targetOffsetY = z;
    }

    private int getTargetPositionX() {
        return (int) (-targetOffsetX * mapScale);
    }

    private int getTargetPositionY() {
        return (int) (-targetOffsetY * mapScale);
    }


    /**
     * Set the pixel-to-block ratio, maintaining the current center of the screen.
     */
    public void setMapScale(double scale) {
        setMapScale(scale, 0, 0);
    }

    /**
     * Set the pixel-to-block ratio, maintaining the current center of the screen with additional offset.
     */
    private void setMapScale(double scale, int addOffsetX, int addOffsetY) {
        double oldScale = mapScale;
        mapScale = Math.min(Math.max(scale, AntiqueAtlas.CONFIG.Interface.minScale), AntiqueAtlas.CONFIG.Interface.maxScale);

        // Scaling not needed
        if (oldScale == mapScale) {
            return;
        }

        if (mapScale >= MIN_SCALE_THRESHOLD) {
            tileHalfSize = (int) Math.round(8 * mapScale);
            tile2ChunkScale = 1;
        } else {
            tileHalfSize = (int) Math.round(8 * MIN_SCALE_THRESHOLD);
            tile2ChunkScale = (int) Math.round(MIN_SCALE_THRESHOLD / mapScale);
        }

        // Times 2 because the contents of the Atlas are rendered at resolution 2 times smaller:
        scaleBar.setMapScale(mapScale * 2);
        mapOffsetX = (int) ((mapOffsetX + addOffsetX) * (mapScale / oldScale));
        mapOffsetY = (int) ((mapOffsetY + addOffsetY) * (mapScale / oldScale));
        scaleClipIndex = MathHelper.floorLog2((int) (mapScale * 8192)) + 1 - 13;
        zoomLevel = -scaleClipIndex + zoomLevelOne;
        scaleAlpha = 255;

        if (followPlayer && (addOffsetX != 0 || addOffsetY != 0)) {
            followPlayer = false;
            btnPosition.setEnabled(true);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float par3) {
        long currentMillis = System.currentTimeMillis();
        long deltaMillis = currentMillis - lastUpdateMillis;
        lastUpdateMillis = currentMillis;

        if (AntiqueAtlas.CONFIG.Performance.debugRender) {
            renderTimes[renderTimesIndex++] = System.currentTimeMillis();
            if (renderTimesIndex == renderTimes.length) {
                renderTimesIndex = 0;
                double elapsed = 0;
                for (int i = 0; i < renderTimes.length - 1; i++) {
                    elapsed += renderTimes[i + 1] - renderTimes[i];
                }
                System.out.printf("GuiAtlas avg. render time: %.3f\n", elapsed / renderTimes.length);
            }
        }

        super.renderBackground(context);

        RenderSystem.setShaderColor(1, 1, 1, 1);
        AntiqueAtlasTextures.BOOK.draw(context, getGuiX(), getGuiY());

        if (biomeData == null) return;

        if (state.is(DELETING_MARKER)) {
            RenderSystem.setShaderColor(1, 1, 1, 0.5f);
        }
        RenderSystem.enableScissor(
            (int) ((getGuiX() + MAP_BORDER_WIDTH) * screenScale),
            (int) ((MinecraftClient.getInstance().getWindow().getFramebufferHeight() - (getGuiY() + MAP_BORDER_HEIGHT + MAP_HEIGHT) * screenScale)),
            (int) (MAP_WIDTH * screenScale), (int) (MAP_HEIGHT * screenScale));
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Find chunk coordinates of the top left corner of the map.
        // The 'roundToBase' is required so that when the map scales below the
        // threshold the tiles don't change when map position changes slightly.
        // The +-2 at the end provide margin so that tiles at the edges of
        // the page have their stitched texture correct.
        int mapStartX = MathUtil.roundToBase((int) Math.floor(-((double) MAP_WIDTH / 2d + mapOffsetX + 2 * tileHalfSize) / mapScale / 16d), tile2ChunkScale);
        int mapStartZ = MathUtil.roundToBase((int) Math.floor(-((double) MAP_HEIGHT / 2d + mapOffsetY + 2 * tileHalfSize) / mapScale / 16d), tile2ChunkScale);
        int mapEndX = MathUtil.roundToBase((int) Math.ceil(((double) MAP_WIDTH / 2d - mapOffsetX + 2 * tileHalfSize) / mapScale / 16d), tile2ChunkScale);
        int mapEndZ = MathUtil.roundToBase((int) Math.ceil(((double) MAP_HEIGHT / 2d - mapOffsetY + 2 * tileHalfSize) / mapScale / 16d), tile2ChunkScale);
        int mapStartScreenX = getGuiX() + WIDTH / 2 + (int) ((mapStartX << 4) * mapScale) + mapOffsetX;
        int mapStartScreenY = getGuiY() + HEIGHT / 2 + (int) ((mapStartZ << 4) * mapScale) + mapOffsetY;
        TileRenderIterator tiles = new TileRenderIterator(biomeData);
        tiles.setScope(new Rect(mapStartX, mapStartZ, mapEndX, mapEndZ));
        tiles.setStep(tile2ChunkScale);

        context.getMatrices().push();
        context.getMatrices().translate(mapStartScreenX, mapStartScreenY, 0);

        for (SubTileQuartet subtiles : tiles) {
            for (SubTile subtile : subtiles) {
                if (subtile == null || subtile.tile == null) continue;
                TileTexture texture = BiomeTextures.getInstance().getTexture(subtile);
                texture.drawSubTile(context, subtile, tileHalfSize);
            }
        }

        context.getMatrices().pop();

        int markersStartX = MathUtil.roundToBase(mapStartX, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP - 1;
        int markersStartZ = MathUtil.roundToBase(mapStartZ, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP - 1;
        int markersEndX = MathUtil.roundToBase(mapEndX, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP + 1;
        int markersEndZ = MathUtil.roundToBase(mapEndZ, MarkersData.CHUNK_STEP) / MarkersData.CHUNK_STEP + 1;

        // Overlay the frame so that edges of the map are smooth:
        RenderSystem.setShaderColor(1, 1, 1, 1);
        AntiqueAtlasTextures.BOOK_FRAME.draw(context, getGuiX(), getGuiY());

        double iconScale = getIconScale();

        // Draw global markers:
        renderMarkers(context, markersStartX, markersStartZ, markersEndX, markersEndZ, globalMarkersData);
        renderMarkers(context, markersStartX, markersStartZ, markersEndX, markersEndZ, localMarkersData);

        RenderSystem.disableScissor();

        AntiqueAtlasTextures.BOOK_FRAME_NARROW.draw(context, getGuiX(), getGuiY());

        renderScaleOverlay(context, deltaMillis);

        // Draw player icon:
        if (!state.is(HIDING_MARKERS)) {
            renderPlayer(context, iconScale);
        }

        // Draw buttons:
        super.render(context, mouseX, mouseY, par3);

        // Draw the semi-transparent marker attached to the cursor when placing a new marker:
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (state.is(PLACING_MARKER)) {
            RenderSystem.setShaderColor(1, 1, 1, 0.5f);
            markerFinalizer.selectedType.calculateMip(iconScale, mapScale);
            MarkerRenderInfo renderInfo = MarkerRenderInfo.ofType(markerFinalizer.selectedType, iconScale, mapScale);
            markerFinalizer.selectedType.resetMip();
            renderInfo.tex.draw(context, mouseX + renderInfo.x, mouseY + renderInfo.y);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        RenderSystem.disableBlend();

        if (AntiqueAtlas.CONFIG.Performance.debugRender && !isDragging && isMouseOver) {
            int x = screenXToWorldX((int) getMouseX());
            int z = screenYToWorldZ((int) getMouseY());

            String coords = String.format("Coords: %d / %d", x, z);

            ChunkPos pos = new ChunkPos(new BlockPos(x, 0, z));
            String chunks = String.format("Chunks: %d / %d", pos.x, pos.z);
            Identifier tile = biomeData.getTile(pos.x, pos.z);

            if (tile == null) {
                drawTooltip(Arrays.asList(Text.literal(coords), Text.literal(chunks)), textRenderer);
            } else {
                String textureSet = BiomeTextures.getInstance().getTextureSet(tile).id.toString();
                drawTooltip(Arrays.asList(
                        Text.literal(coords),
                        Text.literal(chunks),
                        Text.literal("Tile: " + tile),
                        Text.literal("TSet: " + textureSet)),
                    textRenderer);
            }
        }
    }

    private void renderPlayer(DrawContext context, double iconScale) {
        int playerOffsetX = worldXToScreenX(player.getBlockX());
        int playerOffsetY = worldZToScreenY(player.getBlockZ());

        playerOffsetX = MathHelper.clamp(playerOffsetX, getGuiX() + MAP_BORDER_WIDTH, getGuiX() + MAP_WIDTH + MAP_BORDER_WIDTH);
        playerOffsetY = MathHelper.clamp(playerOffsetY, getGuiY() + MAP_BORDER_HEIGHT, getGuiY() + MAP_HEIGHT + MAP_BORDER_HEIGHT);

        // Draw the icon:
        RenderSystem.setShaderColor(1, 1, 1, state.is(PLACING_MARKER) ? 0.5f : 1);
        float playerRotation = (float) Math.round(player.getYaw() / 360f * PLAYER_ROTATION_STEPS) / PLAYER_ROTATION_STEPS * 360f;

        AntiqueAtlasTextures.PLAYER.drawCenteredWithRotation(context, playerOffsetX, playerOffsetY, (int) Math.round(PLAYER_ICON_WIDTH * iconScale), (int) Math.round(PLAYER_ICON_HEIGHT * iconScale), playerRotation);

        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void renderScaleOverlay(DrawContext context, long deltaMillis) {
        MatrixStack matrices = context.getMatrices();
        if (scaleAlpha > 3) {
            matrices.push();
            matrices.translate(getGuiX() + WIDTH - 13, getGuiY() + 12, 0);

            int color = scaleAlpha << 24;

            String text;
            int textWidth, xWidth;

            text = "x";
            xWidth = textWidth = this.textRenderer.getWidth(text);
            xWidth++;
            context.drawText(this.textRenderer, text, -textWidth, 0, color, false);

            text = zoomNames[zoomLevel];
            if (text.contains("/")) {
                String[] parts = text.split("/");

                int centerXtranslate = Math.max(this.textRenderer.getWidth(parts[0]), this.textRenderer.getWidth(parts[1])) / 2;
                matrices.translate(-xWidth - centerXtranslate, (float) -this.textRenderer.fontHeight / 2, 0);

                context.fill(-centerXtranslate - 1, this.textRenderer.fontHeight - 1, centerXtranslate, this.textRenderer.fontHeight, color);

                textWidth = this.textRenderer.getWidth(parts[0]);
                context.drawText(this.textRenderer, parts[0], -textWidth / 2, 0, color, false);

                textWidth = this.textRenderer.getWidth(parts[1]);
                context.drawText(this.textRenderer, parts[1], -textWidth / 2, 10, color, false);
            } else {
                textWidth = this.textRenderer.getWidth(text);
                context.drawText(this.textRenderer, text, -textWidth - xWidth + 1, 2, color, false);
            }

            matrices.pop();

            int deltaScaleAlpha = (int) (deltaMillis * 0.256);
            // because of some crazy high frame rate
            if (deltaScaleAlpha == 0) {
                deltaScaleAlpha = 1;
            }

            scaleAlpha -= deltaScaleAlpha;

            if (scaleAlpha < 0)
                scaleAlpha = 0;

        }
    }

    private void renderMarkers(DrawContext context, int markersStartX, int markersStartZ,
                               int markersEndX, int markersEndZ, DimensionMarkersData markersData) {
        if (markersData == null) return;

        for (int x = markersStartX; x <= markersEndX; x++) {
            for (int z = markersStartZ; z <= markersEndZ; z++) {
                List<Marker> markers = markersData.getMarkersAtChunk(x, z);
                if (markers == null) continue;
                for (Marker marker : markers) {
                    renderMarker(context, marker, getIconScale());
                }
            }
        }
    }

    private void renderMarker(DrawContext context, Marker marker, double scale) {
        MarkerType type = MarkerTypes.getInstance().get(marker.getType());
        if (type.shouldHide(state.is(HIDING_MARKERS), scaleClipIndex)) {
            return;
        }

        int markerX = worldXToScreenX(marker.getX());
        int markerY = worldZToScreenY(marker.getZ());
        if (!marker.isVisibleAhead() &&
            !biomeData.hasTileAt(marker.getChunkX(), marker.getChunkZ())) {
            return;
        }
        type.calculateMip(scale, mapScale);
        MarkerRenderInfo info = MarkerRenderInfo.ofType(type, scale, mapScale);

        boolean mouseIsOverMarker = type.shouldHover((getMouseX() - (markerX + info.x)) / info.tex.width(), (getMouseY() - (markerY + info.y)) / info.tex.height());
        type.resetMip();

        if (mouseIsOverMarker) {
            RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1);
            hoveredMarker = marker;
            // API: Marker Hovered Callback?
        } else {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            if (hoveredMarker == marker) {
                hoveredMarker = null;
            }
        }

        if (state.is(PLACING_MARKER)) {
            RenderSystem.setShaderColor(1, 1, 1, 0.5f);
        } else if (state.is(DELETING_MARKER) && marker.isGlobal()) {
            RenderSystem.setShaderColor(1, 1, 1, 0.5f);
        } else {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }

        if (AntiqueAtlas.CONFIG.Performance.debugRender) {
            System.out.println("Rendering Marker: " + info.tex);
        }

        if (markerX <= getGuiX() + MAP_BORDER_WIDTH || markerX >= getGuiX() + MAP_WIDTH + MAP_BORDER_WIDTH
            || markerY <= getGuiY() + MAP_BORDER_HEIGHT || markerY >= getGuiY() + MAP_HEIGHT + MAP_BORDER_HEIGHT
        ) {
            if (!type.isTechnical()) {
                RenderSystem.setShaderColor(1, 1, 1, 0.5f);
                info.scale(0.8);
            }
        }

        if (!type.isTechnical()) {
            markerX = MathHelper.clamp(markerX, getGuiX() + MAP_BORDER_WIDTH, getGuiX() + MAP_WIDTH + MAP_BORDER_WIDTH);
            markerY = MathHelper.clamp(markerY, getGuiY() + MAP_BORDER_HEIGHT, getGuiY() + MAP_HEIGHT + MAP_BORDER_HEIGHT);
        }

        info.tex.draw(context, markerX + info.x, markerY + info.y, info.width, info.height);

        RenderSystem.setShaderColor(1, 1, 1, 1);

        if (isMouseOver && mouseIsOverMarker && !marker.getLabel().getString().isEmpty()) {
            drawTooltip(Collections.singletonList(marker.getLabel()), textRenderer);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        super.close();
        markerFinalizer.closeChild();
        removeChild(blinkingIcon);
    }

    /**
     * Returns the Y coordinate that the cursor is pointing at.
     */
    private int screenXToWorldX(int mouseX) {
        return (int) Math.round((double) (mouseX - this.width / 2 - mapOffsetX) / mapScale);
    }

    /**
     * Returns the Y block coordinate that the cursor is pointing at.
     */
    private int screenYToWorldZ(int mouseY) {
        return (int) Math.round((double) (mouseY - this.height / 2 - mapOffsetY) / mapScale);
    }

    private int worldXToScreenX(int x) {
        return (int) Math.round((double) x * mapScale + this.width / 2f + mapOffsetX);
    }

    private int worldZToScreenY(int z) {
        return (int) Math.round((double) z * mapScale + this.height / 2f + mapOffsetY);
    }

    @Override
    protected void onChildClosed(Component child) {
        if (child.equals(markerFinalizer)) {
            removeChild(blinkingIcon);
        }
    }

    /**
     * Update all text labels to current localization.
     */
    public void updateL18n() {
        btnMarker.setTitle(Text.translatable("gui.antique_atlas.addMarker"));
    }

    /**
     * Returns the scale of markers and player icon at given mapScale.
     */
    private double getIconScale() {
        if (AntiqueAtlas.CONFIG.Interface.doScaleMarkers) {
            if (mapScale < 0.5) return 0.5;
            if (mapScale > 1) return 2;
        }
        return 1;
    }

    /**
     * Returns atlas id
     */
    private int getAtlasID() {
        return AtlasAPI.getPlayerAtlasId(player);
    }
}