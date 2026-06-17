package com.portfolio.ficc.model;

public enum Side {
	BUY, SELL;

	public boolean isOpposite(Side other) {
		return other != null && this != other;
	}
}
