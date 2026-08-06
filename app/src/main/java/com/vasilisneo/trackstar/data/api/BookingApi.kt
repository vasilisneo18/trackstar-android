package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Session booking (/api/coach/slots for coaches, /api/bookings for athletes). See the backend
// BookingController for the endpoint shapes.
interface BookingApi {

    // Coach — manage availability.
    @POST("coach/slots")
    suspend fun createSlot(@Body body: CreateSlotRequest): Response<SlotResponse>

    @GET("coach/slots")
    suspend fun mySlots(): Response<List<SlotResponse>>

    @DELETE("coach/slots/{slotId}")
    suspend fun deleteSlot(@Path("slotId") slotId: String): Response<MessageResponse>

    // Athlete — browse + book the linked coach's slots.
    @GET("bookings/available")
    suspend fun availableSlots(): Response<List<SlotResponse>>

    @GET("bookings")
    suspend fun myBookings(): Response<List<SlotResponse>>

    @POST("bookings/{slotId}")
    suspend fun book(@Path("slotId") slotId: String): Response<SlotResponse>

    @DELETE("bookings/{slotId}")
    suspend fun cancel(@Path("slotId") slotId: String): Response<SlotResponse>
}

data class CreateSlotRequest(
    val date: String,        // "yyyy-MM-dd"
    val startTime: String,   // "HH:mm"
    val endTime: String,     // "HH:mm"
    val capacity: Int,
    val title: String?,
    val notes: String?,
    val repeatWeeks: Int = 1, // 1 = just this date; N = repeat weekly for N weeks
)

data class SlotResponse(
    val id: String,
    val coachId: String?,
    val coachName: String?,
    val date: String,
    val startTime: String,
    val endTime: String,
    val capacity: Int,
    val remaining: Int,
    val full: Boolean,
    val bookedByMe: Boolean,
    val title: String?,
    val notes: String?,
    val attendees: List<Attendee>?,
)

data class Attendee(val athleteId: String?, val name: String?)
