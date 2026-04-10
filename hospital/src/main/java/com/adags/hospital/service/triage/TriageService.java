package com.adags.hospital.service.triage;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.appointment.AppointmentStatus;
import com.adags.hospital.domain.billing.Invoice;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.triage.TriageAssessment;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.triage.TriageRequest;
import com.adags.hospital.dto.triage.TriageResponse;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.patient.PatientRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.triage.TriageRepository;
import com.adags.hospital.service.billing.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TriageService {

    private static final BigDecimal DOCTOR_FEE     = new BigDecimal("10000");
    private static final BigDecimal SPECIALIST_FEE  = new BigDecimal("30000");

    private final TriageRepository triageRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingService billingService;

    public Page<TriageResponse> getByPatient(UUID patientId, Pageable pageable) {
        return triageRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    public TriageResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    public TriageResponse getByAppointmentId(UUID appointmentId) {
        TriageAssessment assessment = triageRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("TriageAssessment", "appointmentId", appointmentId));
        return toResponse(assessment);
    }

    /** Returns the current open TRIAGE appointment queue (SCHEDULED / CONFIRMED only — not yet assessed). */
    @Transactional(readOnly = true)
    public List<Appointment> getActiveTriageQueue() {
        return appointmentRepository.findActiveTriageQueue();
    }

    /** Returns patients already assessed by triage and awaiting doctor / payment. */
    @Transactional(readOnly = true)
    public List<Appointment> getAssessedAwaitingDoctorQueue() {
        return appointmentRepository.findAssessedAwaitingDoctorQueue();
    }

    @Transactional
    public TriageResponse create(TriageRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.patientId()));
        Staff nurse = staffRepository.findById(request.nurseId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", request.nurseId()));

        Appointment appointment = null;
        if (request.appointmentId() != null) {
            appointment = appointmentRepository.findById(request.appointmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", request.appointmentId()));
        }

        // Compute BMI server-side (if both height and weight are provided)
        BigDecimal bmi = request.bmi();
        if (bmi == null && request.weight() != null && request.height() != null
                && request.height().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightM = request.height().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            bmi = request.weight().divide(heightM.multiply(heightM), 2, RoundingMode.HALF_UP);
        }

        // Referral: look up referred doctor and auto-create consultation invoice
        Staff referredDoctor = null;
        String referralType  = null;
        Invoice consultationInvoice = null;

        if (request.referredDoctorId() != null) {
            referredDoctor = staffRepository.findById(request.referredDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", request.referredDoctorId()));
            referralType = referredDoctor.getStaffRole() == Role.SPECIALIST_DOCTOR
                    ? "SPECIALIST_DOCTOR" : "DOCTOR";
            BigDecimal fee = "SPECIALIST_DOCTOR".equals(referralType) ? SPECIALIST_FEE : DOCTOR_FEE;
            String desc = "SPECIALIST_DOCTOR".equals(referralType)
                    ? "Specialist consultation fee — Dr. " + referredDoctor.getFirstName() + " " + referredDoctor.getLastName()
                    : "General consultation fee — Dr. " + referredDoctor.getFirstName() + " " + referredDoctor.getLastName();
            consultationInvoice = billingService.createConsultationInvoice(patient.getId(), desc, fee);
        }

        // Mark appointment IN_PROGRESS when assessment is created
        if (appointment != null && appointment.getStatus() == AppointmentStatus.SCHEDULED) {
            appointment.setStatus(AppointmentStatus.IN_PROGRESS);
            appointmentRepository.save(appointment);
        }

        TriageAssessment assessment = TriageAssessment.builder()
                .patient(patient)
                .nurse(nurse)
                .appointment(appointment)
                .chiefComplaint(request.chiefComplaint())
                // Vital signs
                .temperature(request.temperature())
                .bloodPressureSystolic(request.bloodPressureSystolic())
                .bloodPressureDiastolic(request.bloodPressureDiastolic())
                .pulseRate(request.pulseRate())
                .respiratoryRate(request.respiratoryRate())
                .oxygenSaturation(request.oxygenSaturation())
                // Anthropometric
                .weight(request.weight())
                .height(request.height())
                .bmi(bmi)
                // Medical history
                .knownAllergies(request.knownAllergies())
                .comorbidities(request.comorbidities())
                .currentSymptoms(request.currentSymptoms())
                .modeOfAmbulation(request.modeOfAmbulation())
                // Pain
                .hasPain(request.hasPain())
                .painScore(request.hasPain() ? request.painScore() : null)
                .painLocation(request.hasPain() ? request.painLocation() : null)
                // Risk
                .fallRisk(request.fallRisk())
                .fallScore(request.fallRisk() ? request.fallScore() : null)
                // Infectious
                .infectiousDiseaseRisk(request.infectiousDiseaseRisk())
                // Priority & notes
                .triagePriority(request.triagePriority())
                .notes(request.notes())
                // Referral
                .referredDoctor(referredDoctor)
                .referralType(referralType)
                .consultationInvoice(consultationInvoice)
                .build();

        return toResponse(triageRepository.save(assessment));
    }

    private TriageAssessment findOrThrow(UUID id) {
        return triageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TriageAssessment", "id", id));
    }

    /** Returns all triage assessments performed by the given nurse, newest first. */
    public List<TriageResponse> getMyHistory(UUID nurseId) {
        return triageRepository.findByNurseIdWithDetails(nurseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TriageResponse toResponse(TriageAssessment t) {
        return new TriageResponse(
                t.getId(),
                t.getPatient().getId(),
                t.getPatient().getFirstName() + " " + t.getPatient().getLastName(),
                t.getNurse().getId(),
                t.getNurse().getFirstName() + " " + t.getNurse().getLastName(),
                t.getAppointment() != null ? t.getAppointment().getId() : null,
                t.getAssessmentDateTime(),
                t.getChiefComplaint(),
                t.getTemperature(),
                t.getBloodPressureSystolic(),
                t.getBloodPressureDiastolic(),
                t.getPulseRate(),
                t.getRespiratoryRate(),
                t.getOxygenSaturation(),
                t.getWeight(),
                t.getHeight(),
                t.getBmi(),
                t.getKnownAllergies(),
                t.getComorbidities(),
                t.getCurrentSymptoms(),
                t.getModeOfAmbulation(),
                t.isHasPain(),
                t.getPainScore(),
                t.getPainLocation(),
                t.isFallRisk(),
                t.getFallScore(),
                t.isInfectiousDiseaseRisk(),
                t.getTriagePriority(),
                t.getNotes(),
                t.getReferredDoctor() != null ? t.getReferredDoctor().getId() : null,
                t.getReferredDoctor() != null
                        ? t.getReferredDoctor().getFirstName() + " " + t.getReferredDoctor().getLastName()
                        : null,
                t.getReferralType(),
                t.getConsultationInvoice() != null ? t.getConsultationInvoice().getId() : null
        );
    }
}
