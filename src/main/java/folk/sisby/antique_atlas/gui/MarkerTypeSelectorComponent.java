package folk.sisby.antique_atlas.gui;

import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.gui.core.ToggleButtonComponent;
import folk.sisby.antique_atlas.MarkerTexture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;


public class MarkerTypeSelectorComponent extends ToggleButtonComponent {
    public static final Identifier MARKER_FRAME_ON = AntiqueAtlas.id("textures/gui/marker_frame_on.png");
    public static final Identifier MARKER_FRAME_OFF = AntiqueAtlas.id("textures/gui/marker_frame_off.png");
    public static final int FRAME_SIZE = 34;

    private final MarkerTexture markerTexture;

    public MarkerTypeSelectorComponent(MarkerTexture markerTexture) {
        this.markerTexture = markerTexture;
        setSize(FRAME_SIZE, FRAME_SIZE);
    }

    public MarkerTexture getMarkerType() {
        return markerTexture;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
        Identifier frameTexture = isSelected() ? MARKER_FRAME_ON : MARKER_FRAME_OFF;
        context.drawTexture(frameTexture, getGuiX() + 1, getGuiY() + 1, 0, 0, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

        Identifier texture = markerTexture.id();
        if (texture != null) {
            context.drawTexture(texture, getGuiX() + 1, getGuiY() + 1, 0, 0, 32, 32, 32, 32);
        }

        super.render(context, mouseX, mouseY, partialTick);
    }
}