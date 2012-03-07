package com.jayway.maven.plugins.android.config;

public class DummyConfigPojo {

	private String stringValue;
	private Boolean booleanValue;

	public DummyConfigPojo(String stringValue, Boolean booleanValue) {
		this.stringValue = stringValue;
		this.booleanValue = booleanValue;
	}

	public String getStringValue() {
		return stringValue;
	}

	public Boolean getBooleanValue() {
		return booleanValue;
	}

}
