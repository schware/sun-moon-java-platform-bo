package com.sunmoon.bo.domain.terminal;

import com.sunmoon.bo.domain.device.Device;
import com.sunmoon.bo.domain.device.DeviceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The join is the screen. Everything interesting here is a case where BO's
 * 장비 master and the Device Server's registry disagree.
 */
class TerminalStatusServiceTest {

    private static final Instant WHEN = Instant.parse("2026-09-15T01:00:00Z");

    private final StubDevices devices = new StubDevices();
    private final StubDirectory directory = new StubDirectory();
    private final TerminalStatusService service = new TerminalStatusService(devices, directory);

    private static Device terminal(String deviceId, String storeId, String terminalType) {
        return new Device(deviceId, deviceId + " 화면", terminalType, "1층", storeId, terminalType,
                null, null, true, null);
    }

    private static ConnectedTerminal session(String deviceId, String storeId, String type) {
        return new ConnectedTerminal(deviceId, storeId, List.of(storeId), type, "ch1", WHEN);
    }

    @Test
    void marksARegisteredTerminalConnectedWhenTheDeviceServerSeesIt() {
        devices.rows.add(terminal("pos-01", "store-01", "POS"));
        directory.sessions.add(session("pos-01", "store-01", "POS"));

        List<TerminalStatus> rows = service.list();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.registered()).isTrue();
            assertThat(row.connected()).isTrue();
            assertThat(row.connectedAt()).isEqualTo(WHEN);
        });
    }

    @Test
    void doesNotLetOneStoresScreenVouchForAnother() {
        devices.rows.add(terminal("pos-01", "store-01", "POS"));
        directory.sessions.add(session("pos-01", "store-02", "POS"));

        List<TerminalStatus> rows = service.list();

        // store-01's counter is dark; store-02 is connected as a terminal
        // BO never registered. Two rows, and neither one is "fine".
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).storeId()).isEqualTo("store-01");
        assertThat(rows.get(0).connected()).isFalse();
        assertThat(rows.get(1).storeId()).isEqualTo("store-02");
        assertThat(rows.get(1).registered()).isFalse();
    }

    @Test
    void doesNotConfusePosAndKdsSharingADeviceId() {
        devices.rows.add(terminal("01", "store-01", "POS"));
        devices.rows.add(terminal("01", "store-01", "KDS"));
        directory.sessions.add(session("01", "store-01", "KDS"));

        List<TerminalStatus> rows = service.list();

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(row -> row.terminalType().equals("POS"))
                .singleElement().extracting(TerminalStatus::connected).isEqualTo(false);
        assertThat(rows).filteredOn(row -> row.terminalType().equals("KDS"))
                .singleElement().extracting(TerminalStatus::connected).isEqualTo(true);
    }

    @Test
    void leavesOutEquipmentThatCouldNeverConnect() {
        devices.rows.add(new Device("prn-01", "영수증 프린터", "PRINTER", "카운터", "store-01", null,
                "192.168.0.60", 9100, true, null));

        // A printer is real 장비 and it is working; showing it as
        // "연결 끊김" on a terminal screen would be a lie about it.
        assertThat(service.list()).isEmpty();
    }

    @Test
    void showsAMultiStoreDidAsUnregisteredRatherThanGuessingAStore() {
        devices.rows.add(terminal("did-hall", null, "DID"));
        directory.sessions.add(new ConnectedTerminal(
                "did-hall", null, List.of("store-01", "store-02"), "DID", "ch9", WHEN));

        List<TerminalStatus> rows = service.list();

        // Both halves have a null store, so they do match — and the row
        // carries the full watch list the Device Server reported.
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.registered()).isTrue();
            assertThat(row.connected()).isTrue();
            assertThat(row.storeIds()).containsExactly("store-01", "store-02");
        });
    }

    private static final class StubDevices implements DeviceRepository {
        private final List<Device> rows = new ArrayList<>();

        @Override
        public List<Device> findAll() {
            return rows;
        }

        @Override
        public List<Device> findByType(String deviceType) {
            return rows.stream().filter(d -> d.deviceType().equals(deviceType)).toList();
        }

        @Override
        public Optional<Device> find(String deviceId) {
            return rows.stream().filter(d -> d.deviceId().equals(deviceId)).findFirst();
        }

        @Override
        public void insert(Device device) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(Device device) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(String deviceId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubDirectory implements TerminalDirectory {
        private final List<ConnectedTerminal> sessions = new ArrayList<>();

        @Override
        public List<ConnectedTerminal> connected() {
            return sessions;
        }
    }
}
