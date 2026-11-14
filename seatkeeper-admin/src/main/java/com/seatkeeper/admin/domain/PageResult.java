package com.seatkeeper.admin.domain;

import java.util.List;

/**
 * 一页数据，字段与契约的 AdminReservationPage 一致：
 * {items, page, size, hasMore}
 *
 * 注意：契约里没有 total，翻页靠 hasMore 判断，别自己编一个总数出来。
 */
public record PageResult<T>(List<T> items, int page, int size, boolean hasMore) {

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), page, size, false);
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
