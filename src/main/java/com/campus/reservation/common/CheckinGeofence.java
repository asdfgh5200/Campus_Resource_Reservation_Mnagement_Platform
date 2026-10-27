package com.campus.reservation.common;

/**
 * 定位打卡基准点与范围（WGS-84，与浏览器 Geolocation 坐标系一致）。
 * 同类型资源共享同一基准点；用户定位坐标仅用于本次距离计算，不落库。
 * 坐标需与 schema.sql 中的打卡点保持一致（应用启动时由该脚本统一刷新）。
 */
public final class CheckinGeofence {

    /** 单体建筑（教学楼 / 实验室 / 图书馆）打卡半径（米） */
    public static final double BUILDING_RADIUS_METERS = 120;
    /** 体育馆打卡半径（米），覆盖多个场馆与室外球场 */
    public static final double GYM_RADIUS_METERS = 150;
    /** 浏览器定位精度上限（米），劣于此值直接拒绝打卡 */
    public static final double MAX_ACCURACY_METERS = 100;
    /** 精度容差上限（米）：按定位精度放宽打卡范围，最多额外放宽该值 */
    public static final double ACCURACY_TOLERANCE_MAX_METERS = 50;

    private static final Point CLASSROOM = new Point("教学楼", 30.511442, 114.333556);
    private static final Point LAB = new Point("学海楼", 30.514356, 114.345786);
    private static final Point LIBRARY = new Point("图书馆", 30.513169, 114.328458);
    private static final Point GYM = new Point("体育馆", 30.504850, 114.330324);

    private CheckinGeofence() {
    }

    /** 按资源类型返回打卡基准点；未知类型回退到教学楼 */
    public static Point of(Integer resourceType) {
        return switch (resourceType == null ? 0 : resourceType) {
            case 2 -> LAB;
            case 3 -> LIBRARY;
            case 4 -> GYM;
            default -> CLASSROOM;
        };
    }

    /** 打卡半径（米）：体育馆范围更大 */
    public static double radiusOf(Integer resourceType) {
        return resourceType != null && resourceType == 4 ? GYM_RADIUS_METERS : BUILDING_RADIUS_METERS;
    }

    /**
     * 允许打卡的距离上限（米）：基础半径 + 精度容差。
     * 浏览器给出的 accuracy 表示位置的置信半径，按同值放宽可避免“站在楼里却被判在圈外”；
     * 容差最多 {@link #ACCURACY_TOLERANCE_MAX_METERS} 米，防止定位漂移过大时跨楼误打卡。
     */
    public static double allowedRadius(double radius, double accuracy) {
        return radius + Math.min(Math.max(accuracy, 0), ACCURACY_TOLERANCE_MAX_METERS);
    }

    /** 打卡基准点：名称 + WGS-84 经纬度 */
    public record Point(String name, double latitude, double longitude) {
    }
}
