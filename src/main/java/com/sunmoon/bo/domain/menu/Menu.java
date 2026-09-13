package com.sunmoon.bo.domain.menu;

/**
 * Menu master data only — no 대/중/소 size tiers yet, and no price: price
 * is a history of {@link MenuPrice} rows, resolved by lookup date, not a
 * column here.
 *
 * <p>{@code imageUrl} is a plain link, not a stored file — this family has
 * no file/object storage yet.
 *
 * <p>{@code kdsDeviceId} is which KDS station should see an order for
 * this item once accepted — not enforced by anything yet.
 * `sun-moon-java-platform-channel-order`'s own catalog is what actually
 * drives real orders today, and it carries the same field independently;
 * this one is for when BO's menu master becomes the real catalog.
 */
public record Menu(
        String menuCode, String name, String imageUrl, String description, String kdsDeviceId, boolean active) {
}
