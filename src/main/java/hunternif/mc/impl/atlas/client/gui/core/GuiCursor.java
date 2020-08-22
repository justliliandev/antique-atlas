package hunternif.mc.impl.atlas.client.gui.core;

import hunternif.mc.impl.atlas.util.AtlasRenderHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;


/**
 * A GUI element that follows the mouse cursor and is meant to replace it.
 * @author Hunternif
 */
public class GuiCursor extends GuiComponent {
	
	private Identifier texture;
	private int textureWidth, textureHeight;
	/** Coordinates of the cursor point on the texture. */
	private int pointX, pointY;
	
	/**
	 * @param texture	texture image file
	 * @param width		image width
	 * @param height	image height
	 * @param pointX	X of the cursor point on the image
	 * @param pointY	Y of the cursor point on the image
	 */
	public void setTexture(Identifier texture, int width, int height, int pointX, int pointY) {
		this.texture = texture;
		this.textureWidth = width;
		this.textureHeight = height;
		this.pointX = pointX;
		this.pointY = pointY;
	}
	
	@Override
	public int getWidth() {
		return 0;
	}
	
	@Override
	public int getHeight() {
		return 0;
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
		AtlasRenderHelper.drawFullTexture(matrices, texture, mouseX - pointX, mouseY - pointY, textureWidth, textureHeight);
	}
}