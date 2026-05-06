package com.example.myhrms.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST(ApiRoutes.LOGIN)
    fun login(@Body loginRequest: LoginRequest): Call<LoginResponse>

    @GET(ApiRoutes.GET_COMPANY)
    fun getCompanies(): Call<CompanyResponse>

    @GET("companies")
    fun getCompaniesLegacy(): Call<CompanyResponse>

    @GET(ApiRoutes.GET_BRANCH)
    fun getBranches(@Query("company_id") companyId: Int): Call<BranchResponse>

    @GET(ApiRoutes.GET_BRANCH)
    fun getBranchesByCoId(@Query("co_id") companyId: Int): Call<BranchResponse>

    @GET("branches")
    fun getBranchesLegacy(@Query("company_id") companyId: Int): Call<BranchResponse>

    @GET(ApiRoutes.GET_DEPARTMENT)
    fun getDepartments(
        @Query("co_id") coId: Int? = null,
        @Query("branch_id") branchId: Int? = null
    ): Call<DepartmentResponse>

    @GET(ApiRoutes.DESIGNATIONS)
    fun getDesignations(
        @Query("branch_id") branchId: Int,
        @Query("sub_dept_id") subDeptId: Int? = null
    ): Call<DesignationResponse>

    @POST(ApiRoutes.DEPARTMENTS)
    fun addDepartment(@Body request: AddDepartmentRequest): Call<AddDepartmentResponse>

    @GET(ApiRoutes.SHIFTS)
    fun getShifts(
        @Query("branch_id") branchId: Int? = null
    ): Call<ShiftResponse>

    @POST(ApiRoutes.SHIFTS)
    fun addShift(@Body request: AddShiftRequest): Call<AddShiftResponse>

    @GET(ApiRoutes.OCCUPATIONS)
    fun getOccupations(): Call<OccupationResponse>

    @POST(ApiRoutes.OCCUPATIONS)
    fun addOccupation(@Body request: AddOccupationRequest): Call<AddOccupationResponse>

    @PUT("${ApiRoutes.OCCUPATIONS}/{id}")
    fun updateOccupation(@Path("id") id: Int, @Body request: AddOccupationRequest): Call<AddOccupationResponse>

    @DELETE("${ApiRoutes.OCCUPATIONS}/{id}")
    fun deleteOccupation(@Path("id") id: Int): Call<AddOccupationResponse>

    @POST(ApiRoutes.ATTENDANCE)
    fun recognizeFace(@Body request: FaceRecognitionRequest): Call<FaceRecognitionResponse>

    @POST(ApiRoutes.MARK_ATTENDANCE)
    fun markAttendanceManual(@Body request: MarkAttendanceRequest): Call<FaceRecognitionResponse>

    @POST(ApiRoutes.CHECK_FACE)
    fun checkFace(@Body request: FaceRecognitionRequest): Call<FaceRecognitionResponse>

    @GET(ApiRoutes.EMPLOYEE_BY_CODE)
    fun getEmployeeByCode(
        @Path("emp_code") empCode: String,
        @Query("branch_id") branchId: Int? = null
    ): Call<FaceRecognitionResponse>

    // ── Employees ────────────────────────────────────────────────
    @GET(ApiRoutes.EMPLOYEES)
    fun getEmployees(): Call<EmployeeResponse>

    @GET(ApiRoutes.EMPLOYEE_SEARCH)
    fun searchEmployees(
        @Query("q") query: String,
        @Query("branch_id") branchId: Int? = null
    ): Call<EmployeeResponse>

    @POST(ApiRoutes.REGISTER)
    fun addEmployee(@Body request: AddEmployeeRequest): Call<AddEmployeeResponse>

    @PUT("${ApiRoutes.EMPLOYEES}/{id}")
    fun updateEmployee(@Path("id") id: Int, @Body request: AddEmployeeRequest): Call<AddEmployeeResponse>

    @DELETE("${ApiRoutes.EMPLOYEES}/{id}")
    fun deleteEmployee(@Path("id") id: Int): Call<AddEmployeeResponse>

    // ── Dashboard Stats ────────────────────────────────────────
    @GET(ApiRoutes.DASHBOARD_STATS)
    fun getDashboardStats(
        @Query("date") date: String,
        @Query("branch_id") branchId: Int? = null,
        @Query("co_id") coId: Int? = null,
        @Query("spell_id") spellId: Int? = null
    ): Call<DashboardStatsResponse>

    // ── Emp-Wise Attendance Report ─────────────────────────────
    @GET(ApiRoutes.EMP_WISE_ATTENDANCE)
    fun getEmpWiseAttendance(
        @Query("from_date")      fromDate: String,
        @Query("to_date")        toDate: String,
        @Query("report_type")    reportType: String,
        @Query("branch_id")      branchId: Int? = null,
        @Query("spell_id")       spellId: Int? = null,
        @Query("dept_id")        deptId: Int? = null,
        @Query("designation_id") designationId: Int? = null,
        @Query("att_type")       attType: String? = null
    ): Call<EmpWiseAttendanceResponse>

    // ── Attendance Dashboard (charts) ──────────────────────────
    @GET(ApiRoutes.ATTENDANCE_DASHBOARD)
    fun getAttendanceDashboard(
        @Query("date") date: String,
        @Query("branch_id") branchId: Int? = null,
        @Query("co_id") coId: Int? = null,
        @Query("spell_id") spellId: Int? = null
    ): Call<AttendanceDashboardResponse>

    // ── Machines ───────────────────────────────────────────────
    @GET("machines")
    fun getMachines(
        @Query("designation_id") designationId: Int
    ): Call<MachineResponse>

    // ── Attendance Report ────────────────────────────────────────
    // Single date query (for Attendance Update page)
    @GET(ApiRoutes.ATTENDANCE_REPORT)
    fun getAttendanceReport(
        @Query("date") date: String,
        @Query("emp_code") empCode: String? = null,
        @Query("emp_name") empName: String? = null,
        @Query("shift_name") shiftName: String? = null,
        @Query("branch_id") branchId: Int? = null
    ): Call<AttendanceReportResponse>

    // Date range query (for Attendance Report page)
    @GET(ApiRoutes.ATTENDANCE_REPORT)
    fun getAttendanceReportRange(
        @Query("from_date") fromDate: String,
        @Query("to_date") toDate: String,
        @Query("department_id") departmentId: Int? = null,
        @Query("emp_code") empCode: String? = null,
        @Query("branch_id") branchId: Int? = null,
        @Query("shift_name") shiftName: String? = null
    ): Call<AttendanceReportResponse>

    // ── Attendance Update ─────────────────────────────────────────
    @GET("attendance/{attendance_id}")
    fun getAttendanceById(@Path("attendance_id") attendanceId: Int): Call<AttendanceDetailResponse>

    @PUT("attendance/{attendance_id}")
    fun updateAttendance(
        @Path("attendance_id") attendanceId: Int,
        @Body request: UpdateAttendanceRequest
    ): Call<UpdateAttendanceResponse>

    // ── Leave Types ────────────────────────────────────────────
    @GET(ApiRoutes.LEAVE_TYPES)
    fun getLeaveTypes(): Call<LeaveTypeResponse>

    // ── Status Master ──────────────────────────────────────────
    @GET(ApiRoutes.STATUS_MST)
    fun getStatusMst(): Call<StatusMstResponse>

    // ── Leave Transactions ─────────────────────────────────────
    @GET(ApiRoutes.LEAVE_TRANSACTIONS)
    fun getLeaveTransactions(
        @Query("branch_id")   branchId: Int? = null,
        @Query("from_date")   fromDate: String? = null,
        @Query("to_date")     toDate: String? = null,
        @Query("emp_code")    empCode: String? = null,
        @Query("status_id")   statusId: Int? = null
    ): Call<LeaveListResponse>

    @POST(ApiRoutes.LEAVE_TRANSACTIONS)
    fun saveLeaveTransaction(@Body request: LeaveSaveRequest): Call<LeaveSaveResponse>

    @DELETE(ApiRoutes.LEAVE_TRANSACTION_DETAIL)
    fun deleteLeaveTransaction(@Path("id") id: Int): Call<LeaveSaveResponse>

    // ── Spinning Doff ─────────────────────────────────────────────
    @GET(ApiRoutes.SPELLS)
    fun getSpells(
        @Query("branch_id") branchId: Int? = null
    ): Call<SpellResponse>

    @GET(ApiRoutes.DOFF_MACHINES)
    fun getDoffMachines(@Query("branch_id") branchId: Int? = null): Call<DoffMachineResponse>

    @GET(ApiRoutes.DOFF_QUALITIES)
    fun getDoffQualities(@Query("branch_id") branchId: Int? = null): Call<SpinningQualityResponse>

    @GET(ApiRoutes.DOFF_TROLLIES)
    fun getDoffTrollies(@Query("branch_id") branchId: Int? = null): Call<TrollyResponse>

    @GET(ApiRoutes.DOFF_TRANSACTIONS)
    fun getDoffTransactions(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int? = null,
        @Query("branch_id") branchId: Int? = null,
        @Query("mc_id")     mcId: Int? = null
    ): Call<DoffListResponse>

    @GET(ApiRoutes.DOFF_LAST_BY_MACHINE)
    fun getDoffLastByMachine(@Query("mc_id") mcId: Int): Call<DoffDefaultsResponse>

    @GET(ApiRoutes.DOFF_LAST_EMP)
    fun getDoffLastEmp(
        @Query("date")     date: String,
        @Query("spell_id") spellId: Int
    ): Call<DoffDefaultsResponse>

    @POST(ApiRoutes.DOFF_TRANSACTIONS)
    fun saveDoffTransaction(@Body request: DoffSaveRequest): Call<DoffSaveResponse>

    @DELETE(ApiRoutes.DOFF_TRANSACTION_DETAIL)
    fun deleteDoffTransaction(@Path("id") id: Int): Call<DoffSaveResponse>

    @GET(ApiRoutes.DOFF_VALIDATE_MACHINE)
    fun validateDoffMachine(
        @Query("mc_no")     mcNo: String,
        @Query("branch_id") branchId: Int? = null
    ): Call<DoffMachineValidateResponse>

    @GET(ApiRoutes.DOFF_VALIDATE_TROLLY)
    fun validateDoffTrolly(
        @Query("trolly_no") trollyNo: String,
        @Query("branch_id") branchId: Int? = null
    ): Call<DoffTrollyValidateResponse>

    @GET(ApiRoutes.DOFF_SUMMARY)
    fun getDoffSummary(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int? = null,
        @Query("branch_id") branchId: Int? = null,
        @Query("mc_id")     mcId: Int? = null
    ): Call<DoffSummaryResponse>

    @GET(ApiRoutes.DOFF_FRAME_MACHINES)
    fun getFrameMachines(
        @Query("branch_id") branchId: Int
    ): Call<DoffMachineResponse>

    @GET(ApiRoutes.DOFF_FRAME_ENTRIES)
    fun getFrameEntries(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<FrameEntriesResponse>

    @POST(ApiRoutes.DOFF_FRAME_ENTRIES)
    fun saveFrameEntries(@Body request: FrameEntriesSaveRequest): Call<FrameEntriesSaveResponse>

    @GET(ApiRoutes.DOFF_FRAME_MACHINE_DEFAULTS)
    fun getFrameMachineDefaults(
        @Query("branch_id") branchId: Int
    ): Call<FrameMachineDefaultsResponse>

    // ── Winding Frame Entry ────────────────────────────────────────
    @GET(ApiRoutes.DOFF_WINDING_FRAME_MACHINES)
    fun getWindingFrameMachines(
        @Query("branch_id") branchId: Int
    ): Call<DoffMachineResponse>

    @GET(ApiRoutes.DOFF_WINDING_FRAME_ENTRIES)
    fun getWindingFrameEntries(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<FrameEntriesResponse>

    @POST(ApiRoutes.DOFF_WINDING_FRAME_ENTRIES)
    fun saveWindingFrameEntries(@Body request: FrameEntriesSaveRequest): Call<FrameEntriesSaveResponse>

    @GET(ApiRoutes.DOFF_WINDING_FRAME_DEFAULTS)
    fun getWindingFrameMachineDefaults(
        @Query("branch_id") branchId: Int
    ): Call<FrameMachineDefaultsResponse>

    @GET(ApiRoutes.DOFF_WINDING_QUALITIES)
    fun getWindingQualities(
        @Query("branch_id") branchId: Int? = null
    ): Call<WindingQualityResponse>

    @GET(ApiRoutes.DOFF_LAST_QUALITY_BY_MC)
    fun getLastQualityByMc(
        @Query("branch_id") branchId: Int,
        @Query("type") type: String = "S"
    ): Call<LastQualityByMcResponse>

    @GET(ApiRoutes.DOFF_WINDING_EB_LOOKUP)
    fun windingEbLookup(
        @Query("eb_no")     ebNo: Long,
        @Query("branch_id") branchId: Int
    ): Call<WindingEbLookupResponse>

    @GET(ApiRoutes.DOFF_WINDING_ENTRIES_LIST)
    fun getWindingEntries(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<WindingEntriesResponse>

    @POST(ApiRoutes.DOFF_WINDING_ENTRY)
    fun saveWindingEntry(@Body request: WindingEntrySaveRequest): Call<WindingEntrySaveResponse>

    @PUT(ApiRoutes.DOFF_WINDING_ENTRY_DETAIL)
    fun updateWindingEntry(
        @Path("id") id: Int,
        @Body request: WindingEntryUpdateRequest
    ): Call<WindingEntrySaveResponse>

    @DELETE(ApiRoutes.DOFF_WINDING_ENTRY_DETAIL)
    fun deleteWindingEntry(@Path("id") id: Int): Call<WindingEntrySaveResponse>

    // ── On Boarding ───────────────────────────────────────────────
    @GET(ApiRoutes.ONBOARDING_EMPLOYEE)
    fun getOnBoardingEmployee(
        @Path("emp_code") empCode: String,
        @Query("branch_id") branchId: Int? = null
    ): Call<OnBoardingEmployeeResponse>

    @POST(ApiRoutes.ONBOARDING_REGISTER_FACE)
    fun registerOnBoardingFace(@Body request: OnBoardingRegisterRequest): Call<OnBoardingRegisterResponse>

    // ── Weight Entry ──────────────────────────────────────────────────
    @GET(ApiRoutes.WEIGHT_TRANSACTIONS)
    fun getWeightTransactions(
        @Query("branch_id") branchId: Int? = null,
        @Query("date")      date: String? = null
    ): Call<WeightListResponse>

    @POST(ApiRoutes.WEIGHT_TRANSACTIONS)
    fun saveWeightTransaction(@Body request: WeightSaveRequest): Call<WeightSaveResponse>

    @DELETE(ApiRoutes.WEIGHT_TRANSACTION_DETAIL)
    fun deleteWeightTransaction(@Path("id") id: Int): Call<WeightSaveResponse>

    // ── Spg Doff Entry (1) ────────────────────────────────────────────────────
    @GET(ApiRoutes.DOFF_SPG1_MECH_CODES)
    fun getSpg1MechCodes(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<Spg1MechCodesResponse>

    @GET(ApiRoutes.DOFF_SPG1_SUMMARY)
    fun getSpg1Summary(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<Spg1SummaryResponse>

    // ── Winding Entry (2) ──────────────────────────────────────────────────
    @GET(ApiRoutes.DOFF_WE2_EMP_LOOKUP)
    fun we2EmpLookup(
        @Query("emp_code")  empCode: String,
        @Query("branch_id") branchId: Int
    ): Call<We2EmpLookupResponse>

    @GET(ApiRoutes.DOFF_WE2_EMPLOYEES)
    fun getWe2Employees(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<We2EmployeesResponse>

    @GET(ApiRoutes.DOFF_WE2)
    fun getWe2Summary(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<We2SummaryResponse>

    @GET(ApiRoutes.DOFF_WE2_DETAIL_BY_EMP)
    fun getWe2Details(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int,
        @Query("eb_id")     ebId: Long
    ): Call<We2SummaryResponse>

    @GET(ApiRoutes.DOFF_WE2_SUMMARY)
    fun getWe2GroupedSummary(
        @Query("date")      date: String,
        @Query("spell_id")  spellId: Int,
        @Query("branch_id") branchId: Int
    ): Call<We2GroupedResponse>

    @POST(ApiRoutes.DOFF_WE2)
    fun saveWe2(@Body req: We2SaveRequest): Call<We2SaveResponse>

    @DELETE(ApiRoutes.DOFF_WE2_DETAIL)
    fun deleteWe2(@Path("id") id: Int): Call<We2SaveResponse>

    @GET(ApiRoutes.DOFF_WE2_QUALITY_SHIFT_REPORT)
    fun getWe2QualityShiftReport(
        @Query("date")      date: String,
        @Query("branch_id") branchId: Int
    ): Call<QualityShiftReportResponse>
}
