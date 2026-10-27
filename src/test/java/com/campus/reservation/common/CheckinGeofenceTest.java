package com.campus.reservation.common;

import com.campus.reservation.service.CheckinService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 打卡基准点与半径的规格测试：坐标必须与产品给定的建筑位置一致。
 */
class CheckinGeofenceTest {

    @Test
    @DisplayName("四类资源使用给定的 WGS-84 打卡点")
    void pointsMatchSpecification() {
        CheckinGeofence.Point classroom = CheckinGeofence.of(1);
        assertThat(classroom.latitude()).isCloseTo(30.511442, within(1e-6));
        assertThat(classroom.longitude()).isCloseTo(114.333556, within(1e-6));

        CheckinGeofence.Point lab = CheckinGeofence.of(2);
        assertThat(lab.latitude()).isCloseTo(30.514356, within(1e-6));
        assertThat(lab.longitude()).isCloseTo(114.345786, within(1e-6));

        CheckinGeofence.Point library = CheckinGeofence.of(3);
        assertThat(library.latitude()).isCloseTo(30.513169, within(1e-6));
        assertThat(library.longitude()).isCloseTo(114.328458, within(1e-6));

        CheckinGeofence.Point gym = CheckinGeofence.of(4);
        assertThat(gym.latitude()).isCloseTo(30.504850, within(1e-6));
        assertThat(gym.longitude()).isCloseTo(114.330324, within(1e-6));
    }

    @Test
    @DisplayName("教学楼与图书馆打卡范围不重叠，体育馆半径更大")
    void radiusKeepsBuildingsApart() {
        CheckinGeofence.Point classroom = CheckinGeofence.of(1);
        CheckinGeofence.Point library = CheckinGeofence.of(3);
        double distance = CheckinService.distance(classroom.latitude(), classroom.longitude(),
                library.latitude(), library.longitude());

        // 两建筑间距约 525 米；半径叠加最大精度容差后仍小于间距，避免跨楼误打卡
        double worstAllowed = CheckinGeofence.BUILDING_RADIUS_METERS + CheckinGeofence.ACCURACY_TOLERANCE_MAX_METERS;
        assertThat(distance).isGreaterThan(worstAllowed);
        assertThat(CheckinGeofence.radiusOf(1)).isEqualTo(CheckinGeofence.BUILDING_RADIUS_METERS);
        assertThat(CheckinGeofence.radiusOf(4)).isEqualTo(CheckinGeofence.GYM_RADIUS_METERS);
    }

    @Test
    @DisplayName("允许距离 = 基础半径 + min(精度, 容差上限)")
    void allowedRadiusCapsTolerance() {
        double radius = CheckinGeofence.BUILDING_RADIUS_METERS;

        // 精度低于容差上限时，按实际精度放宽
        assertThat(CheckinGeofence.allowedRadius(radius, 20)).isEqualTo(radius + 20);
        // 精度高于容差上限时，只放宽上限值
        assertThat(CheckinGeofence.allowedRadius(radius, 90))
                .isEqualTo(radius + CheckinGeofence.ACCURACY_TOLERANCE_MAX_METERS);
        // 异常精度不产生负容差
        assertThat(CheckinGeofence.allowedRadius(radius, -5)).isEqualTo(radius);
    }
}
