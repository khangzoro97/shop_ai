package com.fortfio.ai;

import lombok.Getter;

/** Where the product sits in the frame (Bria product shot manual placement). */
@Getter
public enum Placement {

	BOTTOM_CENTER("Giữa", "bottom_center"),
	BOTTOM_LEFT("Lệch trái", "bottom_left"),
	BOTTOM_RIGHT("Lệch phải", "bottom_right");

	private final String label;
	private final String briaValue;

	Placement(String label, String briaValue) {
		this.label = label;
		this.briaValue = briaValue;
	}
}
