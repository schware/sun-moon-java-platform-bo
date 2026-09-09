package com.sunmoon.bo.domain.menu;

/**
 * Menu master data only — no 대/중/소 size tiers yet, and no price: price
 * is a history of {@link MenuPrice} rows, resolved by lookup date, not a
 * column here.
 *
 * <p>{@code imageUrl} is a plain link, not a stored file — this family has
 * no file/object storage yet.
 */
public record Menu(String menuCode, String name, String imageUrl, String description, boolean active) {
}
