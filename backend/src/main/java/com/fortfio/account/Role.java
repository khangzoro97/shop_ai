package com.fortfio.account;

public enum Role {
	CUSTOMER,
	ADMIN,
	/** Holds Home page trial jobs until a visitor signs up and claims one; cannot log in. */
	SYSTEM
}
