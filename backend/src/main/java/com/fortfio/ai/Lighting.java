package com.fortfio.ai;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Relight presets; direction and type are values of Bria's relight input schema. */
@Getter
@RequiredArgsConstructor
public enum Lighting {

	SUNRISE("sunrise", "Nắng sớm", "Tươi mới, dịu nhẹ", "side", "sunrise light"),
	GOLDEN("golden", "Nắng chiều", "Ấm áp, đổ bóng dài", "side", "low-angle sunlight"),
	MIDDAY("midday", "Nắng trưa", "Sáng rõ, tương phản mạnh", "top-down", "midday"),
	OVERCAST("overcast", "Trời râm dịu", "Mềm mại, không bóng gắt", "front", "soft overcast daylight lighting"),
	STUDIO("studio", "Studio sắc nét", "Rõ chi tiết kiểu ảnh quảng cáo", "front", "harsh studio lighting"),
	SPOTLIGHT("spotlight", "Đèn spotlight", "Nổi bật sản phẩm, xung quanh tối", "top-down", "spotlight on subject"),
	BOKEH("bokeh", "Bokeh lung linh", "Đèn mờ ảo phía sau", "front", "soft bokeh lighting"),
	BLUE_HOUR("bluehour", "Hoàng hôn xanh", "Sang trọng, huyền bí", "side", "blue hour light"),
	MOONLIGHT("moonlight", "Ánh trăng", "Không khí ban đêm", "side", "moonlight lighting");

	private final String id;
	private final String label;
	private final String hint;
	private final String direction;
	private final String type;

	public static Optional<Lighting> byId(String id) {
		return Arrays.stream(values()).filter(l -> l.id.equals(id)).findFirst();
	}
}
