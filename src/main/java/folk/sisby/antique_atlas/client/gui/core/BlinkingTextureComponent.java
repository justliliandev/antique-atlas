package folk.sisby.antique_atlas.client.gui.core;

import com.mojang.blaze3d.systems.RenderSystem;
import folk.sisby.antique_atlas.client.texture.ITexture;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.opengl.GL11;

/**
 * Displays a texture that changes alpha at regular intervals.
 * By default the texture file is assumed to be full image, but that behavior
 * can be altered by overriding the method {@link #drawImage(DrawContext)}.
 *
 * @author Hunternif
 */
public class BlinkingTextureComponent extends Component {
    private ITexture texture;
    /**
     * The number of milliseconds the icon spends visible or invisible.
     */
    private final long blinkTime;
    private final float visibleAlpha;
    private final float invisibleAlpha;

    private long lastTickTime;
    /**
     * The flag that switches value every "blink".
     */
    private boolean isVisible;

    public BlinkingTextureComponent(long blinkTime, float visibleAlpha, float invisibleAlpha) {
        this.blinkTime = blinkTime;
        this.visibleAlpha = visibleAlpha;
        this.invisibleAlpha = invisibleAlpha;
    }

    public BlinkingTextureComponent() {
        this(500, 1, 0.25f);
    }

    public void setTexture(ITexture texture, int width, int height) {
        this.texture = texture;
        setSize(width, height);
        // Set up the timer so that the image appears visible at the first moment:
        lastTickTime = 0;
        isVisible = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
        long currentTime = System.currentTimeMillis();
        if (lastTickTime + blinkTime < currentTime) {
            lastTickTime = currentTime;
            isVisible = !isVisible;
        }
        RenderSystem.setShaderColor(1, 1, 1, isVisible ? visibleAlpha : invisibleAlpha);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawImage(context);

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void drawImage(DrawContext context) {
        texture.draw(context, getGuiX(), getGuiY(), getWidth(), getHeight());
    }
}