package gui.elements;

import database.card.CardColor;
import database.image.ImageStore;
import gui.UIConstants;

import javax.swing.*;
import java.awt.*;

public class ImageToggleButton extends JToggleButton {

	private static final String RAISED_ICON = "raised.png";
	private static final String RAISED_HOVER_ICON = "raisedhover.png";
	private static final String PRESSED_ICON = "pressed.png";
	private static final String PRESSED_HOVER_ICON = "pressedhover.png";

	private final ImageIcon normalIcon;
	private final ImageIcon hoverIcon;
	private final ImageIcon selectedIcon;
	private final ImageIcon selectedHoverIcon;

	public ImageToggleButton(String name, Dimension size) {

		//Make button only display the imageIcon.
		setBorderPainted(false);
		setBorder(null);
		setMargin(new Insets(0, 0, 0, 0));
		setContentAreaFilled(false);
		setFocusPainted(false);

		//Initialize icons.
		String iconPath = "images/buttonicons/" + name + "/";
		normalIcon = ImageStore.getLocalIcon(iconPath + RAISED_ICON, size);
		setIcon(normalIcon);
		selectedIcon = ImageStore.getLocalIcon(iconPath + PRESSED_ICON, size);
		setSelectedIcon(selectedIcon);
		hoverIcon = ImageStore.getLocalIcon(iconPath + RAISED_HOVER_ICON, size);
		selectedHoverIcon = ImageStore.getLocalIcon(iconPath + PRESSED_HOVER_ICON, size);

		addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseEntered(java.awt.event.MouseEvent evt) {
				setIcon(hoverIcon);
				setSelectedIcon(selectedHoverIcon);
			}

			public void mouseExited(java.awt.event.MouseEvent evt) {
				setIcon(normalIcon);
				setSelectedIcon(selectedIcon);
			}
		});
	}

	public ImageToggleButton(CardColor color) {
		this(color.niceName, UIConstants.COLOR_BUTTON_SIZE);
	}
}
