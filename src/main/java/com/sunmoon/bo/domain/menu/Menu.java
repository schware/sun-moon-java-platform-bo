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
 *
 * <p>{@code cookMinutes}는 <b>기준</b> 조리시간(분)입니다. 실측 평균이
 * 아니라 운영자가 정하는 값이고, 예상 대기시간을 "기준 조리시간 ×
 * 대기열 길이"로 뽑을 때 앞의 항이 됩니다. null은 "아직 안 정했다"이며
 * 0으로 취급하지 않습니다 — 0분은 즉시 나온다는 거짓말입니다.
 */
public record Menu(
        String menuCode, String name, String imageUrl, String description, String kdsDeviceId, boolean active,
        Integer cookMinutes) {
}
