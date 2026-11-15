package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.Seat;
import com.seatkeeper.admin.domain.SeatStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 本地假数据：2 个楼层 × 2 个阅览室 × 12 个座位 = 48 个。
 * 状态和契约一致，只有 AVAILABLE / DISABLED；用固定种子，每次启动看到的分布一样。
 *
 * 服务端没起来时用它跑界面（对应手册 W2「先用假数据」）。
 */
public final class MockSeatRepository implements SeatRepository {

    private static final int SEATS_PER_ROOM = 12;

    /** 楼层 -> 阅览室列表 */
    private final Map<Integer, List<String>> floorToRooms = new LinkedHashMap<>();
    private final Map<Long, Seat> seats = new LinkedHashMap<>();

    public MockSeatRepository() {
        floorToRooms.put(1, List.of("A区", "B区"));
        floorToRooms.put(2, List.of("C区", "D区"));
        seedSeats();
    }

    private void seedSeats() {
        Random random = new Random(20261110L);
        long id = 1000;

        for (Map.Entry<Integer, List<String>> entry : floorToRooms.entrySet()) {
            int floor = entry.getKey();
            for (String room : entry.getValue()) {
                String prefix = room.substring(0, 1);
                for (int i = 1; i <= SEATS_PER_ROOM; i++) {
                    id++;
                    String seatNo = String.format("%s-%02d", prefix, i);
                    SeatStatus status = random.nextInt(100) < 85 ? SeatStatus.AVAILABLE : SeatStatus.DISABLED;
                    seats.put(id, new Seat(id, room, floor, seatNo, status));
                }
            }
        }
    }

    @Override
    public List<Integer> floors() {
        return new ArrayList<>(floorToRooms.keySet());
    }

    @Override
    public List<String> roomsOf(int floor) {
        return floorToRooms.getOrDefault(floor, List.of());
    }

    @Override
    public List<Seat> seatsOf(int floor, String room) {
        List<Seat> result = new ArrayList<>();
        for (Seat seat : seats.values()) {
            if (seat.floor() == floor && seat.room().equals(room)) {
                result.add(seat);
            }
        }
        return result;
    }

    @Override
    public Optional<Seat> updateStatus(long seatId, SeatStatus newStatus) {
        Seat current = seats.get(seatId);
        if (current == null) {
            return Optional.empty();
        }
        Seat updated = current.withStatus(newStatus);
        seats.put(seatId, updated);
        return Optional.of(updated);
    }

    /** 供测试用 */
    public Optional<Seat> find(long seatId) {
        return Optional.ofNullable(seats.get(seatId));
    }
}
