package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.Seat;
import com.seatkeeper.admin.domain.SeatStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSeatRepositoryTest {

    private final MockSeatRepository repository = new MockSeatRepository();

    @Test
    void hasTwoFloorsAndTwoRoomsEach() {
        assertEquals(List.of(1, 2), repository.floors());
        assertEquals(List.of("A区", "B区"), repository.roomsOf(1));
        assertEquals(List.of("C区", "D区"), repository.roomsOf(2));
    }

    @Test
    void eachRoomHasTwelveSeats() {
        for (int floor : repository.floors()) {
            for (String room : repository.roomsOf(floor)) {
                assertEquals(12, repository.seatsOf(floor, room).size(),
                        floor + "楼 " + room + " 座位数不对");
            }
        }
    }

    @Test
    void seatNumbersAreUniqueInsideRoom() {
        List<Seat> seats = repository.seatsOf(1, "A区");
        long distinct = seats.stream().map(Seat::seatNo).distinct().count();
        assertEquals(seats.size(), distinct);
    }

    @Test
    void onlyTwoStatusesExistInContract() {
        // 契约里 seat.status 只有 AVAILABLE / DISABLED
        assertEquals(2, SeatStatus.values().length);
    }

    @Test
    void disableThenEnable() {
        Seat seat = repository.seatsOf(1, "A区").get(0);

        repository.updateStatus(seat.id(), SeatStatus.DISABLED);
        assertEquals(SeatStatus.DISABLED, repository.find(seat.id()).orElseThrow().status());

        repository.updateStatus(seat.id(), SeatStatus.AVAILABLE);
        assertEquals(SeatStatus.AVAILABLE, repository.find(seat.id()).orElseThrow().status());
    }

    @Test
    void unknownSeatReturnsEmpty() {
        assertTrue(repository.updateStatus(999999L, SeatStatus.DISABLED).isEmpty());
    }

    @Test
    void floorNameIsChinese() {
        Seat seat = repository.seatsOf(1, "A区").get(0);
        assertEquals("1楼", seat.floorName());
        assertEquals("1楼 A区 " + seat.seatNo(), seat.location());
    }
}
