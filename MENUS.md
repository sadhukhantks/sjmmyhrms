# MyHrms — Menus

Menu keys/visibility are driven by the backend permission DB (`menus` + `role_menu_permissions` tables), seeded in `E:\sjm\AttendanceSystem\src\permissions\migrate.py`. The Android side maps each `menu_key` to a view in `DashboardActivity.kt` (`applyMenuPermissions()`); unknown or non-viewable keys are hidden (`View.GONE`). Layout: `app/src/main/res/layout/activity_dashboard.xml`.

## Top-level dashboard cards

| menu_key | Name | Opens |
|---|---|---|
| `card_present` | Present (Dept-wise) | (inline card, no activity) |
| `card_jute` | Jute (Quick) | JuteReceivedActivity |
| `card_spg` | SPG (Quick) | SpinningDoffActivity |
| `card_winding` | Winding (Quick) | WindingEntryActivity |
| `card_others` | Others (Quick) | OtherEntriesActivity |
| `card_bales` | Bales (Quick) | BalesProductionEntryActivity |

## Side-menu tree

Groups are `grp_*` (no activity), leaves are `menu_*`.

- **Attendance** — `grp_attendance`
  - Attendance Dashboard — `menu_attendance_dashboard` → AttendanceDashboardActivity
  - Face Registration — `menu_onboarding` → OnBoardingActivity
  - Attendance Entry — `menu_attendance_entry` → AttendanceActivity
  - Attendance Reports — `menu_attendance_reports` → AttendanceReportsActivity
  - **Other Entries** — `grp_other_entries`
    - Leave Entries — `menu_leave_entries` → LeaveEntryActivity
- **Production** — `grp_production`
  - **Jute** — `grp_jute`
    - Jute Received — `menu_jute_received` → JuteReceivedActivity
    - Assorting Entry — `menu_assorting_entry` → AssortingEntryActivity
    - Jute Mukam Received — `menu_jute_mukam_received` → JuteMukamReceivedActivity
  - **Spreader Entry** — `grp_spreader_entry`
    - Spreader Production — `menu_production_entry` → SpreaderProdEntryActivity
    - Spreader Issue — `menu_issue_entry` → SpreaderIssueEntryActivity
  - Drawing Meter Entry — `menu_drawing_meter_entry` → DrawingMeterEntryActivity
  - Spinning Doff — `menu_spinning_doff_entry` → SpinningDoffActivity
  - **Doff Entry** — `grp_doff_entry`
    - Spell-wise Frame Entry — `menu_spellwise_frame_entry` → SpellWiseFrameEntryActivity
    - Spg Doff Entry — `menu_spg_doff_entry` → NewDoffEntryActivity
    - Spg Doff Entry 1 — `menu_spg_doff_entry1` → SpgDoffEntry1Activity
    - Spg Doff with Pic — `menu_spg_doff_pic` → SpgDoffpicActivity (camera OCR fills Gross Wt)
    - SPG Running Hours — `menu_spg_running_hours` → SpgRunningHoursActivity
  - **Winding** — `grp_winding_entry`
    - Winding Entry — `menu_winding_entry` → WindingEntryActivity
    - Cont. Winding Entry — `menu_cont_winding_entry` → ContWindingEntryActivity
  - **Others Entry** — `grp_others_entry`
    - Machine Entry — `menu_mechine_entry` → MachineActivity
    - New Machine Entry — `menu_newmechine_entry` → NewMachineActivity
  - Weaving Entry — `menu_weaving_entry` → MenuPlaceholderActivity (placeholder)
  - **Finishing** — `grp_finishing_entry`
    - Other Entries — `menu_other_entries` → OtherEntriesActivity
    - Bales Production Entry — `menu_bales_production_entry` → BalesProductionEntryActivity
    - Bales Issue Entry — `menu_bales_issue_entry` → BalesIssueEntryActivity
  - **Stocks** — `grp_stocks`
    - Roll Stock — `menu_roll_stock` → RollStockActivity
  - Weight Entry — `menu_weight_entry` → WeightEntryActivity

## Roles & default access

| Role | Access |
|---|---|
| Admin | Everything (view/add/edit/delete/print) |
| Manager | Everything except delete |
| Supervisor | View+Add: all cards and all menus above (per `_SUPERVISOR_KEYS`) |
| Operator | View+Add: `card_present`, attendance entry, face registration, jute group, drawing meter, spinning doff, winding entry, weight entry (per `_OPERATOR_KEYS`) |
| Viewer | View-only + print, all menus |

Backfilled after initial seed (idempotent, runs every startup): `menu_jute_mukam_received`, `grp_winding_entry`, `menu_cont_winding_entry` (Supervisor+Operator); `grp_others_entry`, `menu_mechine_entry`, `menu_newmechine_entry` (Supervisor). Note: on DBs seeded before the winding group existed, `menu_winding_entry` keeps `grp_production` as its DB parent — cosmetic only, the app layout is static.
