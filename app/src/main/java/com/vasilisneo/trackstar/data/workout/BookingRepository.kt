package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.CreateSlotRequest
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.cachePeek
import com.vasilisneo.trackstar.data.local.cachedRead

// Session booking: coach availability (create/list/delete slots) and athlete bookings (browse the
// linked coach's slots, book, cancel, list my bookings). API-first, like the other repos. `open`
// so view-model tests can substitute a fake.
open class BookingRepository {
    private val api = NetworkClient.bookingApi

    // Coach
    open suspend fun createSlot(request: CreateSlotRequest): ApiResult<SlotResponse> =
        apiCall { api.createSlot(request) }

    open suspend fun mySlots(): ApiResult<List<SlotResponse>> =
        cachedRead("mySlots") { apiCall { api.mySlots() } }

    // Last cached slots, no network — for painting the coach's "coming up" instantly.
    open suspend fun cachedMySlots(): List<SlotResponse>? = cachePeek("mySlots")

    open suspend fun deleteSlot(slotId: String): ApiResult<MessageResponse> =
        apiCall { api.deleteSlot(slotId) }

    open suspend fun updateSlot(slotId: String, request: CreateSlotRequest): ApiResult<SlotResponse> =
        apiCall { api.updateSlot(slotId, request) }

    open suspend fun deleteSlotSeries(slotId: String): ApiResult<MessageResponse> =
        apiCall { api.deleteSlotSeries(slotId) }

    // Athlete
    open suspend fun availableSlots(): ApiResult<List<SlotResponse>> =
        cachedRead("availableSlots") { apiCall { api.availableSlots() } }

    open suspend fun cachedAvailableSlots(): List<SlotResponse>? = cachePeek("availableSlots")

    open suspend fun myBookings(): ApiResult<List<SlotResponse>> =
        cachedRead("myBookings") { apiCall { api.myBookings() } }

    open suspend fun cachedMyBookings(): List<SlotResponse>? = cachePeek("myBookings")

    open suspend fun book(slotId: String): ApiResult<SlotResponse> =
        apiCall { api.book(slotId) }

    open suspend fun cancel(slotId: String): ApiResult<SlotResponse> =
        apiCall { api.cancel(slotId) }
}
