-- Delete ALL patients and every record linked to them.
-- TRUNCATE ... CASCADE handles all FK dependencies automatically.

TRUNCATE
    payments,
    invoice_line_items,
    invoices,
    dispensed_items,
    diagnoses,
    visit_prescriptions,
    visit_lab_requests,
    lab_result_parameters,
    lab_results,
    lab_requests,
    prescriptions,
    medical_records,
    surgery_assigned_nurses,
    surgery_intraoperative,
    surgery_item_lists,
    surgery_postop_care,
    surgery_orders,
    vital_signs,
    medication_administration_records,
    wound_care_notes,
    ward_patient_assignments,
    triage_assessments,
    patient_allergies,
    next_of_kin,
    appointments,
    patients
RESTART IDENTITY CASCADE;

SELECT 'All patient data deleted successfully.' AS result;
