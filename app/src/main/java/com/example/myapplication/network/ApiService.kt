package com.example.myapplication.network

import com.example.myapplication.models.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded

interface ApiService {
    @POST("register.php")
    fun register(@Body request: RegisterRequest): Call<ApiResponse>

    @POST("login.php")
    fun login(@Body request: LoginRequest): Call<ApiResponse>

    @GET("get_users.php")
    fun getUsers(): Call<UsersResponse>

    @GET("get_complaints.php")
    fun getComplaints(): Call<ComplaintsResponse>

    @FormUrlEncoded
    @POST("update_complaint.php")
    fun updateComplaint(
        @Field("complaint_id") id: Int,
        @Field("status") status: String,
        @Field("admin_response") response: String?
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("file_complaint.php")
    fun fileComplaint(
        @Field("resident_id") residentId: String,
        @Field("category") category: String,
        @Field("description") description: String
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("update_location.php")
    fun updateLocation(
        @Field("user_id") userId: Int,
        @Field("latitude") latitude: Double,
        @Field("longitude") longitude: Double,
        @Field("truck_id") truckId: String,
        @Field("speed") speed: Double,
        @Field("is_full") isFull: Boolean
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("log_collection.php")
    fun logCollection(
        @Field("truck_id") truckId: String,
        @Field("zone_name") zoneName: String,
        @Field("type") type: String
    ): Call<ApiResponse>

    @GET("get_locations.php")
    fun getLocations(): Call<LocationsResponse>

    @FormUrlEncoded
    @POST("check_phone.php")
    fun checkPhone(@Field("phone") phone: String): Call<ApiResponse>

    @FormUrlEncoded
    @POST("check_email.php")
    fun checkEmail(@Field("email") email: String): Call<ApiResponse>

    @FormUrlEncoded
    @POST("check_email_availability.php")
    fun checkEmailAvailability(@Field("email") email: String): Call<ApiResponse>

    @FormUrlEncoded
    @POST("check_license.php")
    fun checkLicense(@Field("license_number") license: String): Call<ApiResponse>

    @FormUrlEncoded
    @POST("check_truck.php")
    fun checkTruck(@Field("truck_id") truckId: String): Call<ApiResponse>

    @FormUrlEncoded
    @POST("verify_token.php")
    fun verifyToken(
        @Field("email") email: String,
        @Field("token") token: String
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("reset_password.php")
    fun resetPassword(
        @Field("email") email: String,
        @Field("password") password: String
    ): Call<ApiResponse>

    @FormUrlEncoded
<<<<<<< HEAD
    @POST("decline_request.php")
    fun declineRequest(
        @Field("email") email: String,
        @Field("name") name: String,
        @Field("role") role: String,
        @Field("username") username: String
    ): Call<ApiResponse>

    @POST("approve_registration.php")
    fun approveRegistration(@Body request: RegisterRequest): Call<ApiResponse>

    @FormUrlEncoded
    @POST("change_password.php")
    fun changePassword(
        @Field("id") id: String,
        @Field("role") role: String,
        @Field("old_password") oldPass: String,
        @Field("new_password") newPass: String
    ): Call<ApiResponse>
}
=======
    @POST("update_resident_profile.php")
    fun updateResidentProfile(
        @Field("user_id") userId: Int,
        @Field("name") name: String,
        @Field("email") email: String,
        @Field("phone") phone: String,
        @Field("purok") purok: String
    ): Call<ApiResponse>

    @POST("archive_user.php")
    fun archiveUser(@Body request: ArchiveRequest): Call<ApiResponse>
}
>>>>>>> 117a85521b466e4f823d227f35cd645078d64a09
