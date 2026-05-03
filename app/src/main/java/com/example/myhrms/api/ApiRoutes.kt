package com.example.myhrms.api

// Keep all endpoint paths in one place for easy backend route updates.
private const val MASTERS_API_LOCATION = "masters"

object ApiRoutes {
    const val LOGIN = "login"

    const val GET_COMPANY = "$MASTERS_API_LOCATION/get_company"
    const val GET_BRANCH = "$MASTERS_API_LOCATION/get_branch"

    const val DEPARTMENTS = "departments"
    const val GET_DEPARTMENT = "departments"   // alias – same backend route
    const val DESIGNATIONS = "designations"
    const val SHIFTS = "shifts"
    const val OCCUPATIONS = "occupations"

    const val ATTENDANCE = "attendance"
    const val MARK_ATTENDANCE = "mark-attendance"
    const val CHECK_FACE = "check-face"
    const val EMPLOYEE_BY_CODE = "employee/{emp_code}"

    const val EMPLOYEES = "employees"
    const val EMPLOYEE_SEARCH = "employees/search"
    const val REGISTER = "register"

    const val DASHBOARD_STATS = "dashboard-stats"
    const val ATTENDANCE_DASHBOARD = "attendance-dashboard"
    const val ATTENDANCE_REPORT = "attendance-report"
    const val EMP_WISE_ATTENDANCE = "emp-wise-attendance"

    const val STATUS_MST             = "status-mst"
    const val LEAVE_TYPES            = "leave-types"
    const val LEAVE_TRANSACTIONS     = "leave-transactions"
    const val LEAVE_TRANSACTION_DETAIL = "leave-transactions/{id}"

    // ── Spinning Doff ─────────────────────────────────────────────
    const val SPELLS                  = "spells"
    const val DOFF_MACHINES           = "doff/machines"
    const val DOFF_QUALITIES          = "doff/qualities"
    const val DOFF_TROLLIES           = "doff/trollies"
    const val DOFF_TRANSACTIONS       = "doff-transactions"
    const val DOFF_TRANSACTION_DETAIL = "doff-transactions/{id}"
    const val DOFF_LAST_BY_MACHINE    = "doff/last-by-machine"
    const val DOFF_LAST_EMP           = "doff/last-emp"
    const val DOFF_VALIDATE_MACHINE   = "doff/validate-machine"
    const val DOFF_VALIDATE_TROLLY    = "doff/validate-trolly"
    const val DOFF_SUMMARY            = "doff/summary"
    const val DOFF_FRAME_MACHINES     = "doff/frame-machines"
    const val DOFF_FRAME_ENTRIES      = "doff/frame-entries"

    const val ONBOARDING_EMPLOYEE = "onboarding/employee/{emp_code}"
    const val ONBOARDING_REGISTER_FACE = "onboarding/register-face"

    // ── Weight Entry ────────────────────────────────────────────────────
    const val WEIGHT_TRANSACTIONS       = "weight-transactions"
    const val WEIGHT_TRANSACTION_DETAIL = "weight-transactions/{id}"
}

