package gui.elements;

import gui.UIConstants;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class SearchBox extends JTextField {
	private boolean showingHint;
	private final String hint;

	public SearchBox(final String hint) {
		super(hint);
		this.hint = hint;
		showingHint = true;
		setMargin(UIConstants.SEARCH_BOX_MARGIN);
		addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				if(getText().isEmpty()) {
					forceClearText();
					showingHint = false;
				}
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (getText().isEmpty()) {
					setText(hint);
					showingHint = true;
				}
			}
		});
	}

	@Override
	public String getText() {
		return showingHint ? "" : super.getText();
	}

	private void forceClearText() {
		super.setText("");
	}

	@Override
	public void setText(String text) {
		if (text.isEmpty()) {
			showingHint = true;
			super.setText(hint);
		} else {
			super.setText(text);
		}
	}
}
